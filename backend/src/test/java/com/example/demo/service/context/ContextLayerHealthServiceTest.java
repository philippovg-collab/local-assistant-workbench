package com.example.demo.service.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.demo.config.ContextProperties;
import com.example.demo.model.HealthResponse;
import com.example.demo.service.context.port.ContextMaintenanceRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ContextLayerHealthServiceTest {

    private final ContextMaintenanceRepository maintenanceRepository = Mockito.mock(ContextMaintenanceRepository.class);
    private final ContextRetentionService retentionService = Mockito.mock(ContextRetentionService.class);

    @Test
    void returnsDisabledWhenContextLayerIsDisabled() {
        ContextProperties properties = new ContextProperties();
        ContextLayerHealthService service = new ContextLayerHealthService(properties, maintenanceRepository, retentionService);

        HealthResponse.ReadinessComponent readiness = service.currentReadiness();

        assertEquals("DISABLED", readiness.status());
        assertEquals("context.disabled", readiness.reasonCode());
        verifyNoInteractions(maintenanceRepository);
    }

    @Test
    void returnsUpWhenEnabledStoresAndJobsAreHealthy() {
        ContextProperties properties = enabledProperties();
        when(maintenanceRepository.summaryJobHealth(any())).thenReturn(new ContextMaintenanceRepository.SummaryJobHealth(0, 0));
        when(retentionService.recentFailure(any())).thenReturn(null);
        ContextLayerHealthService service = new ContextLayerHealthService(properties, maintenanceRepository, retentionService);

        HealthResponse.ReadinessComponent readiness = service.currentReadiness();

        assertEquals("UP", readiness.status());
    }

    @Test
    void degradesOnRepositoryFailure() {
        ContextProperties properties = enabledProperties();
        doThrow(new IllegalStateException("snapshots unavailable"))
            .when(maintenanceRepository)
            .assertContextSnapshotStoreReadable();
        ContextLayerHealthService service = new ContextLayerHealthService(properties, maintenanceRepository, retentionService);

        HealthResponse.ReadinessComponent readiness = service.currentReadiness();

        assertEquals("DEGRADED", readiness.status());
        assertEquals("context.storage_unavailable", readiness.reasonCode());
    }

    @Test
    void degradesOnFailedSummaryJobs() {
        ContextProperties properties = enabledProperties();
        when(maintenanceRepository.summaryJobHealth(any())).thenReturn(new ContextMaintenanceRepository.SummaryJobHealth(1, 0));
        ContextLayerHealthService service = new ContextLayerHealthService(properties, maintenanceRepository, retentionService);

        HealthResponse.ReadinessComponent readiness = service.currentReadiness();

        assertEquals("DEGRADED", readiness.status());
        assertEquals("context.summary_jobs_unhealthy", readiness.reasonCode());
    }

    @Test
    void degradesOnRecentRetentionFailure() {
        ContextProperties properties = enabledProperties();
        when(maintenanceRepository.summaryJobHealth(any())).thenReturn(new ContextMaintenanceRepository.SummaryJobHealth(0, 0));
        when(retentionService.recentFailure(any())).thenReturn(new ContextRetentionService.RetentionFailure(
            Instant.parse("2026-05-10T00:00:00Z"),
            "cleanup failed"
        ));
        ContextLayerHealthService service = new ContextLayerHealthService(properties, maintenanceRepository, retentionService);

        HealthResponse.ReadinessComponent readiness = service.currentReadiness();

        assertEquals("DEGRADED", readiness.status());
        assertEquals("context.retention_failed", readiness.reasonCode());
    }

    private static ContextProperties enabledProperties() {
        ContextProperties properties = new ContextProperties();
        properties.setEnabled(true);
        properties.setConversationsEnabled(true);
        properties.setHistoryEnabled(true);
        properties.setStickyStateEnabled(true);
        properties.setRetrievalQueryResolutionEnabled(true);
        properties.setSummaryEnabled(true);
        return properties;
    }
}
