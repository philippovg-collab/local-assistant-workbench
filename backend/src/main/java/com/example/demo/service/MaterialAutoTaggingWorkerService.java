package com.example.demo.service;

import com.example.demo.config.MaterialProperties;
import com.example.demo.service.material.MaterialAutoTaggingLease;
import com.example.demo.service.material.MaterialAutoTaggingTask;
import com.example.demo.service.material.port.MaterialAutoTaggingTaskRepository;
import java.time.Instant;
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
public class MaterialAutoTaggingWorkerService {

    private static final Logger logger = LoggerFactory.getLogger(MaterialAutoTaggingWorkerService.class);
    private static final long[] RETRY_BACKOFF_SECONDS = {30L, 120L, 600L};

    private final MaterialAutoTaggingTaskRepository taskRepository;
    private final MaterialAutoTaggingLifecycleService lifecycleService;
    private final MaterialProperties properties;
    private final Executor executor;
    private final AtomicInteger scheduledWorkers = new AtomicInteger(0);

    public MaterialAutoTaggingWorkerService(
        MaterialAutoTaggingTaskRepository taskRepository,
        MaterialAutoTaggingLifecycleService lifecycleService,
        MaterialProperties properties,
        @Qualifier("materialAutoTaggingExecutor") Executor executor
    ) {
        this.taskRepository = taskRepository;
        this.lifecycleService = lifecycleService;
        this.properties = properties == null ? new MaterialProperties() : properties;
        this.executor = executor == null ? Runnable::run : executor;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        requestProcessing();
    }

    public void requestProcessing() {
        int maxWorkers = Math.max(1, properties.getAutoTaggingWorkerCount());
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
                executor.execute(this::drainQueue);
            } catch (RejectedExecutionException exception) {
                scheduledWorkers.decrementAndGet();
                logger.warn("Material auto-tagging executor rejected queue processing request; leaving tasks pending", exception);
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
            taskRepository.resetExpiredClaims(now.minusSeconds(properties.getAutoTaggingLeaseSeconds()), now);

            int processedTasks = 0;
            int maxTasks = Math.max(1, properties.getAutoTaggingDrainMaxJobs());
            while (processedTasks < maxTasks) {
                MaterialAutoTaggingLease lease = taskRepository.claimNext(Instant.now()).orElse(null);
                if (lease == null) {
                    break;
                }
                processLease(lease);
                processedTasks += 1;
            }
        } finally {
            scheduledWorkers.decrementAndGet();
            if (taskRepository.hasPending(Instant.now())) {
                requestProcessing();
            }
        }
    }

    private void processLease(MaterialAutoTaggingLease lease) {
        MaterialAutoTaggingTask task = lease.task();
        MDC.put("taskId", task.id());
        MDC.put("materialId", task.materialId());
        MDC.put("attempt", String.valueOf(lease.attemptNumber()));
        try {
            lifecycleService.processTask(task);
        } catch (MaterialAutoTaggingService.AutoTaggingException exception) {
            handleFailure(task, lease.attemptNumber(), failureCode(exception), exception.getMessage(), exception);
        } catch (RuntimeException exception) {
            handleFailure(
                task,
                lease.attemptNumber(),
                MaterialAutoTaggingLifecycleService.FAILURE_PROVIDER,
                rootMessage(exception),
                exception
            );
        } finally {
            MDC.remove("taskId");
            MDC.remove("materialId");
            MDC.remove("attempt");
        }
    }

    private void handleFailure(
        MaterialAutoTaggingTask task,
        int attemptNumber,
        String code,
        String message,
        RuntimeException exception
    ) {
        Instant now = Instant.now();
        if (attemptNumber >= properties.getAutoTaggingMaxAttempts()) {
            logger.warn(
                "Material auto-tagging failed terminally: taskId={} materialId={} attempts={} code={} message={}",
                task.id(),
                task.materialId(),
                attemptNumber,
                code,
                message,
                exception
            );
            taskRepository.markFailed(task.id(), code, message, now);
            return;
        }

        Instant nextRetryAt = now.plusSeconds(backoffSeconds(attemptNumber));
        logger.warn(
            "Material auto-tagging failed and will retry: taskId={} materialId={} attempts={} nextRetryAt={} code={} message={}",
            task.id(),
            task.materialId(),
            attemptNumber,
            nextRetryAt,
            code,
            message,
            exception
        );
        taskRepository.markRetry(task.id(), code, message, now, nextRetryAt);
    }

    private String failureCode(MaterialAutoTaggingService.AutoTaggingException exception) {
        return switch (exception.kind()) {
            case PARSE -> MaterialAutoTaggingLifecycleService.FAILURE_PARSE;
            case PROVIDER -> MaterialAutoTaggingLifecycleService.FAILURE_PROVIDER;
        };
    }

    private long backoffSeconds(int attemptNumber) {
        int index = Math.max(0, Math.min(RETRY_BACKOFF_SECONDS.length - 1, attemptNumber - 1));
        return RETRY_BACKOFF_SECONDS[index];
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
