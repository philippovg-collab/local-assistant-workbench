package com.example.demo.service.context;

import com.example.demo.config.ContextProperties;
import com.example.demo.config.ContextRetentionProperties;
import com.example.demo.service.context.port.ContextMaintenanceRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ContextRetentionService {

    private static final Logger logger = LoggerFactory.getLogger(ContextRetentionService.class);
    private static final Duration RECENT_FAILURE_WINDOW = Duration.ofHours(24);

    private final ContextProperties contextProperties;
    private final ContextRetentionProperties retentionProperties;
    private final ContextMaintenanceRepository maintenanceRepository;
    private final AtomicReference<RetentionFailure> lastFailure = new AtomicReference<>();

    public ContextRetentionService(
        ContextProperties contextProperties,
        ContextRetentionProperties retentionProperties,
        ContextMaintenanceRepository maintenanceRepository
    ) {
        this.contextProperties = contextProperties;
        this.retentionProperties = retentionProperties;
        this.maintenanceRepository = maintenanceRepository;
    }

    @Scheduled(cron = "0 50 3 * * *")
    public void deleteExpiredContextArtifacts() {
        if (!contextProperties.isEnabled() || !retentionProperties.isEnabled()) {
            return;
        }
        try {
            ContextRetentionCleanupResult result = deleteExpiredContextArtifacts(Instant.now());
            if (result.totalDeleted() > 0) {
                logger.info(
                    "Deleted expired context artifacts snapshots={} summaryJobs={} rejectedMemories={} deletedMemories={} memoryJobs={} softDeletedConversations={} emptyConversations={}",
                    result.deletedSnapshots(),
                    result.deletedSummaryJobs(),
                    result.purgedRejectedMemoryEntries(),
                    result.purgedDeletedMemoryEntries(),
                    result.deletedMemoryJobs(),
                    result.purgedSoftDeletedConversations(),
                    result.purgedEmptyConversations()
                );
            }
        } catch (RuntimeException exception) {
            lastFailure.set(new RetentionFailure(Instant.now(), rootMessage(exception)));
            logger.warn("Context retention cleanup failed", exception);
        }
    }

    public ContextRetentionCleanupResult deleteExpiredContextArtifacts(Instant now) {
        if (!contextProperties.isEnabled() || !retentionProperties.isEnabled()) {
            return ContextRetentionCleanupResult.empty();
        }
        Instant effectiveNow = now == null ? Instant.now() : now;
        ContextRetentionCleanupResult result = maintenanceRepository.deleteExpiredContextArtifacts(
            effectiveNow.minus(retentionProperties.getSnapshotDays(), ChronoUnit.DAYS),
            effectiveNow.minus(retentionProperties.getSummaryJobDays(), ChronoUnit.DAYS),
            effectiveNow.minus(retentionProperties.getMemoryRejectedDays(), ChronoUnit.DAYS),
            effectiveNow.minus(retentionProperties.getMemoryDeletedDays(), ChronoUnit.DAYS),
            effectiveNow.minus(retentionProperties.getMemoryJobDays(), ChronoUnit.DAYS),
            effectiveNow.minus(retentionProperties.getDeletedConversationDays(), ChronoUnit.DAYS),
            effectiveNow.minus(retentionProperties.getEmptyConversationDays(), ChronoUnit.DAYS),
            retentionProperties.getKeepLatestSnapshotsPerConversation(),
            retentionProperties.getBatchSize()
        );
        lastFailure.set(null);
        return result == null ? ContextRetentionCleanupResult.empty() : result;
    }

    public RetentionFailure recentFailure(Instant now) {
        RetentionFailure failure = lastFailure.get();
        if (failure == null || failure.failedAt() == null) {
            return null;
        }
        Instant effectiveNow = now == null ? Instant.now() : now;
        return failure.failedAt().plus(RECENT_FAILURE_WINDOW).isAfter(effectiveNow) ? failure : null;
    }

    private String rootMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }

    public record RetentionFailure(Instant failedAt, String message) {
    }
}
