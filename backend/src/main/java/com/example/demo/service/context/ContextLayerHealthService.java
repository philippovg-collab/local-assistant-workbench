package com.example.demo.service.context;

import com.example.demo.config.ContextProperties;
import com.example.demo.model.HealthResponse;
import com.example.demo.service.context.port.ContextMaintenanceRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class ContextLayerHealthService {

    private final ContextProperties contextProperties;
    private final ContextMaintenanceRepository maintenanceRepository;
    private final ContextRetentionService retentionService;

    public ContextLayerHealthService(
        ContextProperties contextProperties,
        ContextMaintenanceRepository maintenanceRepository,
        ContextRetentionService retentionService
    ) {
        this.contextProperties = contextProperties;
        this.maintenanceRepository = maintenanceRepository;
        this.retentionService = retentionService;
    }

    public HealthResponse.ReadinessComponent currentReadiness() {
        Instant observedAt = Instant.now();
        if (!contextProperties.isEnabled()) {
            return new HealthResponse.ReadinessComponent(
                "DISABLED",
                "context.disabled",
                "Context layer is disabled by app.context.enabled=false.",
                observedAt.toString()
            );
        }

        try {
            assertEnabledStoresReadable();
            HealthResponse.ReadinessComponent summaryHealth = summaryJobReadiness(observedAt);
            if (summaryHealth != null) {
                return summaryHealth;
            }
            HealthResponse.ReadinessComponent memoryHealth = memoryJobReadiness(observedAt);
            if (memoryHealth != null) {
                return memoryHealth;
            }
            HealthResponse.ReadinessComponent retentionHealth = retentionReadiness(observedAt);
            if (retentionHealth != null) {
                return retentionHealth;
            }
            return new HealthResponse.ReadinessComponent("UP", null, null, observedAt.toString());
        } catch (RuntimeException exception) {
            return new HealthResponse.ReadinessComponent(
                "DEGRADED",
                "context.storage_unavailable",
                rootMessage(exception),
                observedAt.toString()
            );
        }
    }

    private void assertEnabledStoresReadable() {
        if (contextProperties.isConversationsEnabled()) {
            maintenanceRepository.assertConversationStoreReadable();
        }
        if (contextProperties.isHistoryEnabled()) {
            maintenanceRepository.assertContextSnapshotStoreReadable();
        }
        if (contextProperties.isStickyStateEnabled()) {
            maintenanceRepository.assertStickyStateStoreReadable();
        }
        if (contextProperties.isRetrievalQueryResolutionEnabled()) {
            maintenanceRepository.assertRetrievalResolutionStoreReadable();
        }
        if (contextProperties.isSummaryEnabled()) {
            maintenanceRepository.assertStickyStateStoreReadable();
        }
        if (contextProperties.isLongTermMemoryEnabled()) {
            maintenanceRepository.assertMemoryStoreReadable();
        }
    }

    private HealthResponse.ReadinessComponent summaryJobReadiness(Instant observedAt) {
        if (!contextProperties.isSummaryEnabled()) {
            return null;
        }
        ContextMaintenanceRepository.SummaryJobHealth summaryJobs = maintenanceRepository.summaryJobHealth(observedAt);
        if (summaryJobs.failedCount() > 0 || summaryJobs.stuckCount() > 0) {
            return new HealthResponse.ReadinessComponent(
                "DEGRADED",
                "context.summary_jobs_unhealthy",
                "Conversation summary jobs have failed or stuck entries.",
                observedAt.toString()
            );
        }
        return null;
    }

    private HealthResponse.ReadinessComponent memoryJobReadiness(Instant observedAt) {
        if (!contextProperties.isLongTermMemoryEnabled()) {
            return null;
        }
        ContextMaintenanceRepository.MemoryJobHealth memoryJobs = maintenanceRepository.memoryJobHealth(observedAt);
        if (memoryJobs.failedCount() > 0 || memoryJobs.stuckCount() > 0) {
            return new HealthResponse.ReadinessComponent(
                "DEGRADED",
                "context.memory_jobs_unhealthy",
                "Memory extraction jobs have failed or stuck entries.",
                observedAt.toString()
            );
        }
        return null;
    }

    private HealthResponse.ReadinessComponent retentionReadiness(Instant observedAt) {
        ContextRetentionService.RetentionFailure failure = retentionService.recentFailure(observedAt);
        if (failure == null) {
            return null;
        }
        return new HealthResponse.ReadinessComponent(
            "DEGRADED",
            "context.retention_failed",
            failure.message(),
            failure.failedAt().toString()
        );
    }

    private String rootMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }
}
