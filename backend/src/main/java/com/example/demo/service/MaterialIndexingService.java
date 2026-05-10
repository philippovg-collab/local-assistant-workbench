package com.example.demo.service;

import com.example.demo.service.material.MaterialIndexingLease;
import com.example.demo.service.material.StoredEmbeddedMaterialChunk;
import com.example.demo.service.material.StoredMaterialChunk;
import com.example.demo.service.material.StoredMaterialRecord;
import com.example.demo.service.material.StoredMaterialSegment;
import com.example.demo.service.material.port.MaterialChunkingRepository;
import com.example.demo.service.material.port.MaterialIndexingQueueRepository;

import com.example.demo.error.ApplicationException;
import com.example.demo.error.CodedException;
import com.example.demo.error.ErrorType;
import com.example.demo.config.MaterialProperties;
import com.example.demo.embedding.EmbeddingClient;
import com.example.demo.model.MaterialIndexingStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MaterialIndexingService {

    private static final Logger logger = LoggerFactory.getLogger(MaterialIndexingService.class);

    private final MaterialChunkingRepository chunkingRepository;
    private final MaterialIndexingQueueRepository indexingQueueRepository;
    private final MaterialContentSupport contentSupport;
    private final EmbeddingClient embeddingClient;
    private final MaterialProperties properties;
    private final MaterialSearchSyncLifecycleService lifecycleService;
    private final Executor materialIndexingExecutor;
    private final AtomicInteger scheduledWorkers = new AtomicInteger(0);

    public MaterialIndexingService(
        MaterialChunkingRepository chunkingRepository,
        MaterialIndexingQueueRepository indexingQueueRepository,
        MaterialContentSupport contentSupport,
        EmbeddingClient embeddingClient,
        MaterialProperties properties,
        MaterialSearchSyncLifecycleService lifecycleService,
        @Qualifier("materialIndexingExecutor") Executor materialIndexingExecutor
    ) {
        this.chunkingRepository = chunkingRepository;
        this.indexingQueueRepository = indexingQueueRepository;
        this.contentSupport = contentSupport;
        this.embeddingClient = embeddingClient;
        this.properties = properties;
        this.lifecycleService = lifecycleService;
        this.materialIndexingExecutor = materialIndexingExecutor;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        requestProcessing();
    }

    public void requestProcessing() {
        int maxWorkers = Math.max(1, properties.getIndexingWorkerCount());
        for (int slot = 0; slot < maxWorkers; slot++) {
            int currentWorkers = scheduledWorkers.get();
            if (currentWorkers >= maxWorkers) {
                return;
            }
            if (!scheduledWorkers.compareAndSet(currentWorkers, currentWorkers + 1)) {
                slot -= 1;
                continue;
            }
            try {
                materialIndexingExecutor.execute(this::drainQueue);
            } catch (RejectedExecutionException exception) {
                scheduledWorkers.decrementAndGet();
                logger.warn("Material indexing executor rejected queue processing request; leaving jobs pending", exception);
                return;
            }
        }
    }

    public int activeWorkerCount() {
        return scheduledWorkers.get();
    }

    private void drainQueue() {
        try {
            Instant now = Instant.now();
            indexingQueueRepository.resetExpiredIndexingClaims(now.minusSeconds(properties.getIndexingLeaseSeconds()), now);

            int processedJobs = 0;
            int maxJobs = Math.max(1, properties.getIndexingDrainMaxJobs());
            while (processedJobs < maxJobs) {
                MaterialIndexingLease lease = indexingQueueRepository.claimNextIndexing(Instant.now())
                    .orElse(null);
                if (lease == null) {
                    break;
                }
                processLease(lease);
                processedJobs += 1;
            }
        } finally {
            scheduledWorkers.decrementAndGet();
            if (indexingQueueRepository.hasPendingIndexing(Instant.now())) {
                requestProcessing();
            }
        }
    }

    private void processLease(MaterialIndexingLease lease) {
        StoredMaterialRecord record = lease.record();
        MDC.put("materialId", record.id());
        MDC.put("attempt", String.valueOf(lease.attemptNumber()));
        try {
            List<StoredMaterialChunk> rawChunks = chunkingRepository.findChunks(record.id());
            if (rawChunks.isEmpty()) {
                List<StoredMaterialSegment> storedSegments = chunkingRepository.findSegments(record.id());
                if (!storedSegments.isEmpty()) {
                    rawChunks = contentSupport.buildChunks(
                        storedSegments,
                        contentSupport.resolveChunkProfile(chunkingRepository.findChunkProfile(record.id()))
                    );
                } else {
                    rawChunks = contentSupport.normalizeChunks(
                        List.of(),
                        record.content(),
                        contentSupport.normalizeExtractor(record.extractor()),
                        Boolean.TRUE.equals(record.ocrUsed())
                    );
                }
            }

            List<StoredEmbeddedMaterialChunk> embeddedChunks = embedChunks(rawChunks);
            String successReasonCode = isPartialWarning(record.statusReasonCode()) ? record.statusReasonCode() : null;
            String successReasonMessage = isPartialWarning(record.statusReasonCode()) ? record.statusReasonMessage() : null;
            MaterialIndexingStatus successStatus = successReasonCode == null
                ? MaterialIndexingStatus.READY
                : MaterialIndexingStatus.PARTIAL_READY;

            lifecycleService.markIndexingReady(
                record.id(),
                embeddedChunks,
                successStatus,
                successReasonCode,
                successReasonMessage,
                Instant.now()
            );
            logger.info(
                "Material indexing completed: materialId={} status={} chunks={}",
                record.id(),
                successStatus,
                embeddedChunks.size()
            );
        } catch (CodedException exception) {
            handleFailure(record, lease.attemptNumber(), exception.getCode(), exception.getMessage(), exception);
        } catch (RuntimeException exception) {
            handleFailure(record, lease.attemptNumber(), "material.indexing_failed", rootMessage(exception), exception);
        } finally {
            MDC.remove("materialId");
            MDC.remove("attempt");
        }
    }

    private void handleFailure(
        StoredMaterialRecord record,
        int attemptNumber,
        String code,
        String message,
        RuntimeException exception
    ) {
        if (attemptNumber >= properties.getIndexingMaxAttempts()) {
            logger.warn(
                "Material indexing failed terminally: materialId={} attempts={} code={} message={}",
                record.id(),
                attemptNumber,
                code,
                message,
                exception
            );
            indexingQueueRepository.markIndexingFailed(record.id(), code, message, Instant.now());
            return;
        }

        Instant nextRetryAt = Instant.now().plusSeconds(backoffSeconds(attemptNumber));
        logger.warn(
            "Material indexing failed and will retry: materialId={} attempts={} nextRetryAt={} code={} message={}",
            record.id(),
            attemptNumber,
            nextRetryAt,
            code,
            message,
            exception
        );
        indexingQueueRepository.rescheduleIndexing(record.id(), code, message, Instant.now(), nextRetryAt);
    }

    private long backoffSeconds(int attemptNumber) {
        long base = Math.max(1, properties.getIndexingRetryBaseSeconds());
        int exponent = Math.min(30, Math.max(0, attemptNumber - 1));
        long candidate = base * (1L << exponent);
        return Math.min(candidate, Math.max(base, properties.getIndexingRetryMaxSeconds()));
    }

    private List<StoredEmbeddedMaterialChunk> embedChunks(List<StoredMaterialChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }

        List<float[]> embeddings = embeddingClient.embedAll(
            chunks.stream().map(StoredMaterialChunk::text).toList()
        );
        if (embeddings.size() != chunks.size()) {
            throw new ApplicationException(
                ErrorType.PROVIDER_BAD_RESPONSE,
                "embedding.provider_empty_embedding",
                "Embedding provider returned a different number of vectors than requested"
            );
        }

        List<StoredEmbeddedMaterialChunk> embeddedChunks = new ArrayList<>();
        for (int index = 0; index < chunks.size(); index++) {
            StoredMaterialChunk chunk = chunks.get(index);
            embeddedChunks.add(new StoredEmbeddedMaterialChunk(
                chunk.index(),
                chunk.text(),
                chunk.page(),
                contentSupport.normalizeExtractor(chunk.extractor()),
                Boolean.TRUE.equals(chunk.ocrUsed()),
                embeddings.get(index),
                chunk.chunkType(),
                chunk.sectionPath(),
                chunk.headingTrail(),
                chunk.tableId(),
                chunk.slideId(),
                chunk.parserConfidence()
            ));
        }
        return embeddedChunks;
    }

    private boolean isPartialWarning(String reasonCode) {
        return StringUtils.hasText(reasonCode) && reasonCode.startsWith("material.partial_");
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
