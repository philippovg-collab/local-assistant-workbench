package com.example.demo.service;

import com.example.demo.service.material.port.MaterialSearchSyncQueueRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.example.demo.config.SearchSyncProperties;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class ElasticsearchHealthServiceTest {

    private MaterialSearchSyncQueueRepository queueRepository;
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        queueRepository = org.mockito.Mockito.mock(MaterialSearchSyncQueueRepository.class);
        clock = new MutableClock(Instant.parse("2026-04-17T10:00:00Z"));
        when(queueRepository.getSearchSyncQueueSnapshot()).thenReturn(
            new MaterialSearchSyncQueueRepository.SearchSyncQueueSnapshot(
                2,
                1,
                3,
                Instant.parse("2026-04-17T10:10:00Z"),
                Instant.parse("2026-04-17T10:00:00Z")
            )
        );
    }

    @Test
    void reportsDisabledWhenSearchSyncIsTurnedOff() {
        ElasticsearchHealthService service = new ElasticsearchHealthService(
            properties(false),
            queueRepository,
            emptyProvider(),
            clock
        );

        ElasticsearchHealthService.SearchSyncHealth health = service.refreshHealthSnapshotIfStale();

        assertEquals("DISABLED", health.clusterStatus());
        assertEquals("search.sync_disabled", health.reasonCode());
        assertEquals(2, health.pendingCount());
    }

    @Test
    void reportsDownWhenElasticsearchClientIsMissing() {
        ElasticsearchHealthService service = new ElasticsearchHealthService(
            properties(true),
            queueRepository,
            emptyProvider(),
            clock
        );

        ElasticsearchHealthService.SearchSyncHealth health = service.refreshHealthSnapshotIfStale();

        assertEquals("DOWN", health.clusterStatus());
        assertTrue(health.reasonMessage().contains("not configured"));
    }

    @Test
    void reportsUpAndIncludesLastSuccessfulSyncTimestamp() throws Exception {
        ElasticsearchClient elasticsearchClient = org.mockito.Mockito.mock(ElasticsearchClient.class);
        when(elasticsearchClient.info()).thenReturn(null);
        when(queueRepository.getSearchSyncQueueSnapshot()).thenReturn(
            new MaterialSearchSyncQueueRepository.SearchSyncQueueSnapshot(0, 0, 0, null, null)
        );

        ElasticsearchHealthService service = new ElasticsearchHealthService(
            properties(true),
            queueRepository,
            providerOf(elasticsearchClient),
            clock
        );
        service.recordSuccessfulSync(Instant.parse("2026-04-17T10:15:00Z"));

        ElasticsearchHealthService.SearchSyncHealth health = service.refreshHealthSnapshotIfStale();

        assertEquals("UP", health.clusterStatus());
        assertEquals(Instant.parse("2026-04-17T10:15:00Z"), health.lastSuccessfulSyncAt());
        verify(elasticsearchClient, never()).info();
    }

    @Test
    void reportsDegradedWhenOutstandingBacklogIsOlderThanSafeThreshold() throws Exception {
        ElasticsearchClient elasticsearchClient = org.mockito.Mockito.mock(ElasticsearchClient.class);
        when(elasticsearchClient.info()).thenReturn(null);

        ElasticsearchHealthService service = new ElasticsearchHealthService(
            properties(true),
            queueRepository,
            providerOf(elasticsearchClient),
            clock
        );

        ElasticsearchHealthService.SearchSyncHealth health = service.refreshHealthSnapshotIfStale();

        assertEquals("DEGRADED", health.clusterStatus());
        assertEquals("search.sync_failures", health.reasonCode());
    }

    @Test
    void reportsDownWhenElasticsearchClusterIsUnavailable() throws Exception {
        ElasticsearchClient elasticsearchClient = org.mockito.Mockito.mock(ElasticsearchClient.class);
        when(elasticsearchClient.info()).thenThrow(new IOException("connection refused"));

        ElasticsearchHealthService service = new ElasticsearchHealthService(
            properties(true),
            queueRepository,
            providerOf(elasticsearchClient),
            clock
        );

        ElasticsearchHealthService.SearchSyncHealth health = service.refreshHealthSnapshotIfStale();

        assertEquals("DOWN", health.clusterStatus());
        assertTrue(health.reasonMessage().contains("connection refused"));
    }

    @Test
    void refreshPathCachesLiveProbeUntilTtlExpires() throws Exception {
        ElasticsearchClient elasticsearchClient = org.mockito.Mockito.mock(ElasticsearchClient.class);
        when(elasticsearchClient.info()).thenReturn(null);
        when(queueRepository.getSearchSyncQueueSnapshot()).thenReturn(
            new MaterialSearchSyncQueueRepository.SearchSyncQueueSnapshot(0, 0, 0, null, null)
        );

        ElasticsearchHealthService service = new ElasticsearchHealthService(
            properties(true),
            queueRepository,
            providerOf(elasticsearchClient),
            clock
        );

        ElasticsearchHealthService.SearchSyncHealth initialHealth = service.currentHealth();
        ElasticsearchHealthService.SearchSyncHealth firstRefresh = service.refreshHealthSnapshotIfStale();
        clock.advanceSeconds(10);
        ElasticsearchHealthService.SearchSyncHealth secondRefresh = service.refreshHealthSnapshotIfStale();
        clock.advanceSeconds(6);
        ElasticsearchHealthService.SearchSyncHealth thirdRefresh = service.refreshHealthSnapshotIfStale();

        assertEquals("UNKNOWN", initialHealth.clusterStatus());
        assertEquals("search.health_unprobed", initialHealth.reasonCode());
        assertEquals("UP", firstRefresh.clusterStatus());
        assertEquals("UP", secondRefresh.clusterStatus());
        assertEquals("UP", thirdRefresh.clusterStatus());
        verify(elasticsearchClient, org.mockito.Mockito.times(2)).info();
    }

    @Test
    void initialSnapshotWithClientDoesNotReportUpBeforeProbe() throws Exception {
        ElasticsearchClient elasticsearchClient = org.mockito.Mockito.mock(ElasticsearchClient.class);
        when(elasticsearchClient.info()).thenReturn(null);
        when(queueRepository.getSearchSyncQueueSnapshot()).thenReturn(
            new MaterialSearchSyncQueueRepository.SearchSyncQueueSnapshot(0, 0, 0, null, null)
        );

        ElasticsearchHealthService service = new ElasticsearchHealthService(
            properties(true),
            queueRepository,
            providerOf(elasticsearchClient),
            clock
        );

        ElasticsearchHealthService.SearchSyncHealth health = service.currentHealth();

        assertEquals("UNKNOWN", health.clusterStatus());
        assertEquals("search.health_unprobed", health.reasonCode());
        verify(elasticsearchClient, never()).info();
    }

    @Test
    void runtimeFailureLatchKeepsDegradedSnapshotUntilSafeRefreshWindow() throws Exception {
        ElasticsearchClient elasticsearchClient = org.mockito.Mockito.mock(ElasticsearchClient.class);
        when(elasticsearchClient.info()).thenReturn(null);
        when(queueRepository.getSearchSyncQueueSnapshot()).thenReturn(
            new MaterialSearchSyncQueueRepository.SearchSyncQueueSnapshot(0, 0, 0, null, null)
        );

        ElasticsearchHealthService service = new ElasticsearchHealthService(
            properties(true),
            queueRepository,
            providerOf(elasticsearchClient),
            clock
        );

        service.recordRuntimeFailure(new IllegalStateException("connection reset"));
        ElasticsearchHealthService.SearchSyncHealth latchedHealth = service.currentHealth();
        clock.advanceSeconds(10);
        ElasticsearchHealthService.SearchSyncHealth beforeTtlRefresh = service.refreshHealthSnapshotIfStale();
        clock.advanceSeconds(6);
        ElasticsearchHealthService.SearchSyncHealth afterTtlRefresh = service.refreshHealthSnapshotIfStale();

        assertEquals("DEGRADED", latchedHealth.clusterStatus());
        assertEquals("search.runtime_failure", latchedHealth.reasonCode());
        assertEquals("DEGRADED", beforeTtlRefresh.clusterStatus());
        assertEquals("UP", afterTtlRefresh.clusterStatus());
        verify(elasticsearchClient).info();
    }

    private SearchSyncProperties properties(boolean enabled) {
        SearchSyncProperties properties = new SearchSyncProperties();
        properties.setEnabled(enabled);
        properties.setHealthSnapshotTtlSeconds(15);
        return properties;
    }

    private ObjectProvider<ElasticsearchClient> emptyProvider() {
        return new StaticListableBeanFactory().getBeanProvider(ElasticsearchClient.class);
    }

    private ObjectProvider<ElasticsearchClient> providerOf(ElasticsearchClient elasticsearchClient) {
        return new StaticListableBeanFactory(Map.of("elasticsearchClient", elasticsearchClient))
            .getBeanProvider(ElasticsearchClient.class);
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }
    }
}
