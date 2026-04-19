package com.example.demo.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import com.example.demo.config.SearchSyncProperties;
import com.example.demo.infrastructure.material.MaterialSearchSyncQueueEntry;
import com.example.demo.infrastructure.material.MaterialSearchSyncQueueRepository;
import com.example.demo.infrastructure.material.SearchableMaterialSnapshot;
import com.example.demo.infrastructure.material.MaterialSearchableSnapshotRepository;
import com.example.demo.infrastructure.material.SearchableChunkDocument;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.search-sync", name = "enabled", havingValue = "true")
public class ElasticsearchIndexSyncService {

    private static final Logger logger = LoggerFactory.getLogger(ElasticsearchIndexSyncService.class);

    private final MaterialSearchSyncQueueRepository queueRepository;
    private final MaterialSearchableSnapshotRepository searchableSnapshotRepository;
    private final ElasticsearchHealthService healthService;
    private final ElasticsearchClient elasticsearchClient;
    private final SearchSyncProperties searchSyncProperties;
    private final Executor searchSyncExecutor;
    private final AtomicBoolean drainScheduled = new AtomicBoolean(false);

    public ElasticsearchIndexSyncService(
        MaterialSearchSyncQueueRepository queueRepository,
        MaterialSearchableSnapshotRepository searchableSnapshotRepository,
        ElasticsearchHealthService healthService,
        ElasticsearchClient elasticsearchClient,
        SearchSyncProperties searchSyncProperties,
        @Qualifier("searchSyncExecutor") Executor searchSyncExecutor
    ) {
        this.queueRepository = queueRepository;
        this.searchableSnapshotRepository = searchableSnapshotRepository;
        this.healthService = healthService;
        this.elasticsearchClient = elasticsearchClient;
        this.searchSyncProperties = searchSyncProperties;
        this.searchSyncExecutor = searchSyncExecutor;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        requestProcessing();
    }

    public void requestProcessing() {
        if (!drainScheduled.compareAndSet(false, true)) {
            return;
        }

        searchSyncExecutor.execute(this::drainQueue);
    }

    long backoffSeconds(int attemptNumber) {
        long base = Math.max(1, searchSyncProperties.getRetryBaseSeconds());
        long candidate = base * (1L << Math.max(0, attemptNumber - 1));
        return Math.min(candidate, Math.max(base, searchSyncProperties.getRetryMaxSeconds()));
    }

    private void drainQueue() {
        try {
            Instant now = Instant.now();
            queueRepository.resetExpiredSearchSyncClaims(
                now.minusSeconds(searchSyncProperties.getClaimLeaseSeconds()),
                now
            );

            while (true) {
                List<MaterialSearchSyncQueueEntry> claimedEntries = queueRepository.claimNextSearchSyncBatch(
                    Instant.now(),
                    searchSyncProperties.getClaimBatchSize()
                );
                if (claimedEntries.isEmpty()) {
                    break;
                }
                processClaimedBatch(claimedEntries, Instant.now());
            }
        } finally {
            drainScheduled.set(false);
            if (queueRepository.hasPendingSearchSyncEvents(Instant.now())) {
                requestProcessing();
            }
        }
    }

    private void processClaimedBatch(List<MaterialSearchSyncQueueEntry> claimedEntries, Instant startedAt) {
        int completedCount = 0;
        int retriedCount = 0;
        int failedCount = 0;
        Instant lastSuccessfulSyncAt = null;

        for (MaterialSearchSyncQueueEntry entry : claimedEntries) {
            try {
                reconcileMaterial(entry.materialId());
                Instant completedAt = Instant.now();
                queueRepository.completeSearchSyncEntry(entry.materialId(), entry.claimedAt(), completedAt);
                completedCount += 1;
                lastSuccessfulSyncAt = completedAt;
            } catch (RuntimeException exception) {
                if (handleFailure(entry, exception) == FailureDisposition.FAILED) {
                    failedCount += 1;
                } else {
                    retriedCount += 1;
                }
            }
        }

        if (lastSuccessfulSyncAt != null) {
            healthService.recordSuccessfulSync(lastSuccessfulSyncAt);
        }
        logger.info(
            "Elasticsearch sync batch completed: claimed={} completed={} retried={} failed={} startedAt={}",
            claimedEntries.size(),
            completedCount,
            retriedCount,
            failedCount,
            startedAt
        );
    }

    private FailureDisposition handleFailure(
        MaterialSearchSyncQueueEntry entry,
        RuntimeException exception
    ) {
        int currentAttempt = Math.max(1, entry.attemptCount());
        Instant now = Instant.now();
        String errorCode = "search.sync_failed";
        String errorMessage = rootMessage(exception);

        if (currentAttempt >= searchSyncProperties.getMaxAttempts()) {
            queueRepository.markSearchSyncEntryFailed(
                entry.materialId(),
                entry.claimedAt(),
                errorCode,
                errorMessage,
                now
            );
            logger.warn(
                "Elasticsearch sync failed terminally: materialId={} attempts={} message={}",
                entry.materialId(),
                currentAttempt,
                errorMessage,
                exception
            );
            return FailureDisposition.FAILED;
        }

        Instant nextAttemptAt = now.plusSeconds(backoffSeconds(currentAttempt));
        queueRepository.markSearchSyncEntryForRetry(
            entry.materialId(),
            entry.claimedAt(),
            errorCode,
            errorMessage,
            now,
            nextAttemptAt
        );
        logger.warn(
            "Elasticsearch sync failed and will retry: materialId={} attempts={} nextAttemptAt={} message={}",
            entry.materialId(),
            currentAttempt,
            nextAttemptAt,
            errorMessage,
            exception
        );
        return FailureDisposition.RETRY;
    }

    private void reconcileMaterial(String materialId) {
        SearchableMaterialSnapshot snapshot = searchableSnapshotRepository.resolveSearchableSnapshot(materialId);
        deleteExistingDocuments(materialId);
        if (!snapshot.searchable()) {
            return;
        }

        List<SearchableChunkDocument> documents = SearchableChunkDocument.fromSnapshot(snapshot);
        if (documents.isEmpty()) {
            return;
        }

        BulkResponse response;
        try {
            response = elasticsearchClient.bulk(bulkRequest -> {
                for (SearchableChunkDocument document : documents) {
                    bulkRequest.operations(operation -> operation.index(index -> index
                        .index(searchSyncProperties.writeAlias())
                        .id(document.documentId())
                        .document(document)
                    ));
                }
                return bulkRequest;
            });
        } catch (IOException exception) {
            throw new IllegalStateException("Elasticsearch bulk index failed", exception);
        }
        assertBulkSuccess(response, "index searchable chunks");
    }

    private void deleteExistingDocuments(String materialId) {
        try {
            elasticsearchClient.deleteByQuery(delete -> delete
                .index(searchSyncProperties.writeAlias())
                .query(query -> query.term(term -> term.field("materialId").value(materialId)))
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Elasticsearch delete-by-query failed", exception);
        }
    }

    private void assertBulkSuccess(BulkResponse response, String operation) {
        if (!response.errors()) {
            return;
        }

        List<String> reasons = new ArrayList<>();
        response.items().forEach(item -> {
            if (item.error() != null && item.error().reason() != null) {
                reasons.add(item.error().reason());
            }
        });
        throw new IllegalStateException(
            "Elasticsearch bulk " + operation + " failed: " + String.join("; ", reasons)
        );
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private enum FailureDisposition {
        RETRY,
        FAILED
    }
}
