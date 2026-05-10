package com.example.demo.service.memory;

import com.example.demo.config.ContextProperties;
import com.example.demo.service.conversation.StoredConversationRun;
import com.example.demo.service.conversation.port.ConversationRepository;
import com.example.demo.service.memory.port.MemoryRepository;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
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

@Service
public class MemoryCandidateExtractionWorkerService {

    private static final Logger logger = LoggerFactory.getLogger(MemoryCandidateExtractionWorkerService.class);

    private final ContextProperties properties;
    private final MemoryRepository repository;
    private final ConversationRepository conversationRepository;
    private final MemoryCandidateExtractionService extractionService;
    private final Executor executor;
    private final AtomicInteger scheduledWorkers = new AtomicInteger(0);
    private final String workerId = ManagementFactory.getRuntimeMXBean().getName() + "-memory-" + UUID.randomUUID();

    public MemoryCandidateExtractionWorkerService(
        ContextProperties properties,
        MemoryRepository repository,
        ConversationRepository conversationRepository,
        MemoryCandidateExtractionService extractionService,
        @Qualifier("memoryExtractionExecutor") Executor executor
    ) {
        this.properties = properties;
        this.repository = repository;
        this.conversationRepository = conversationRepository;
        this.extractionService = extractionService;
        this.executor = executor == null ? Runnable::run : executor;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        requestProcessing();
    }

    public void requestProcessing() {
        if (!properties.isLongTermMemoryEnabled()) {
            return;
        }
        int maxWorkers = properties.getMemoryExtractionWorkerCount();
        while (scheduledWorkers.get() < maxWorkers) {
            if (!scheduledWorkers.compareAndSet(scheduledWorkers.get(), scheduledWorkers.get() + 1)) {
                continue;
            }
            try {
                executor.execute(this::drainQueue);
            } catch (RejectedExecutionException exception) {
                scheduledWorkers.decrementAndGet();
                logger.warn("Memory extraction executor rejected processing request; jobs remain pending", exception);
                return;
            }
        }
    }

    private void drainQueue() {
        try {
            Instant now = Instant.now();
            repository.resetExpiredExtractionLeases(now);
            int processed = 0;
            while (processed < properties.getMemoryExtractionDrainMaxJobs() && properties.isLongTermMemoryEnabled()) {
                MemoryExtractionLease lease = repository.claimNextExtractionJob(
                    workerId,
                    Instant.now(),
                    Duration.ofSeconds(properties.getMemoryExtractionLeaseSeconds())
                ).orElse(null);
                if (lease == null) {
                    break;
                }
                process(lease);
                processed += 1;
            }
        } finally {
            scheduledWorkers.decrementAndGet();
            if (properties.isLongTermMemoryEnabled() && repository.hasPendingExtractionJobs(Instant.now())) {
                requestProcessing();
            }
        }
    }

    private void process(MemoryExtractionLease lease) {
        MemoryExtractionJob job = lease.job();
        MDC.put("runId", job.runId());
        try {
            StoredConversationRun run = conversationRepository.findRunByRunId(job.runId()).orElse(null);
            if (run == null || !"COMPLETED".equals(run.status())) {
                repository.markExtractionDone(lease, Instant.now());
                return;
            }
            String workspaceKey = conversationRepository.findConversation(run.conversationId())
                .map(conversation -> conversation.workspaceKey())
                .orElse(null);
            extractionService.extractCandidates(run, workspaceKey, null, lease);
            repository.markExtractionDone(lease, Instant.now());
        } catch (RuntimeException exception) {
            handleFailure(lease, exception);
        } finally {
            MDC.remove("runId");
        }
    }

    private void handleFailure(MemoryExtractionLease lease, RuntimeException exception) {
        MemoryExtractionJob job = lease.job();
        int attemptNumber = lease.attemptNumber();
        Instant now = Instant.now();
        String code = exception.getClass().getSimpleName();
        String message = rootMessage(exception);
        if (attemptNumber >= properties.getMemoryExtractionMaxAttempts()) {
            repository.markExtractionFailed(lease, code, message, now);
            logger.warn("Memory extraction failed terminally: jobId={} runId={}", job.id(), job.runId(), exception);
            return;
        }
        Instant nextRetryAt = now.plusSeconds(backoffSeconds(attemptNumber));
        repository.markExtractionRetry(lease, code, message, now, nextRetryAt);
        logger.warn("Memory extraction failed and will retry: jobId={} runId={}", job.id(), job.runId(), exception);
    }

    private long backoffSeconds(int attemptNumber) {
        long multiplier = 1L << Math.min(10, Math.max(0, attemptNumber - 1));
        return Math.min(
            properties.getMemoryExtractionRetryMaxSeconds(),
            properties.getMemoryExtractionRetryBaseSeconds() * multiplier
        );
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
