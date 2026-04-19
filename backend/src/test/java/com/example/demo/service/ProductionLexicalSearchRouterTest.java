package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.example.demo.config.RagProperties;
import com.example.demo.config.SearchSyncProperties;
import com.example.demo.infrastructure.material.LexicalProviderType;
import com.example.demo.infrastructure.material.LexicalSearchProvider;
import com.example.demo.infrastructure.material.MaterialChunkSearchMatch;
import com.example.demo.infrastructure.material.MaterialSearchSyncQueueRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class ProductionLexicalSearchRouterTest {

    @Test
    void routesAutoModeToElasticsearchWhenSearchPlaneIsUp() {
        StubLexicalSearchProvider postgres = new StubLexicalSearchProvider(LexicalProviderType.POSTGRES);
        StubLexicalSearchProvider elasticsearch = new StubLexicalSearchProvider(LexicalProviderType.ELASTICSEARCH);
        ProductionLexicalSearchRouter router = router("auto", postgres, elasticsearch);

        ProductionLexicalSearchRouter.LexicalRoutingDecision decision = router.currentDecision(
            health("UP", null, null)
        );

        assertEquals("auto", decision.configuredMode().propertyValue());
        assertEquals(LexicalProviderType.ELASTICSEARCH, decision.effectiveProvider());
        assertFalse(decision.fallbackApplied());
    }

    @Test
    void fallsBackToPostgresWhenSearchPlaneIsDegraded() {
        StubLexicalSearchProvider postgres = new StubLexicalSearchProvider(LexicalProviderType.POSTGRES);
        StubLexicalSearchProvider elasticsearch = new StubLexicalSearchProvider(LexicalProviderType.ELASTICSEARCH);
        ProductionLexicalSearchRouter router = router("auto", postgres, elasticsearch);

        ProductionLexicalSearchRouter.LexicalRoutingDecision decision = router.currentDecision(
            health("DEGRADED", "search.sync_backlog_stale", "backlog is stale")
        );

        assertEquals(LexicalProviderType.POSTGRES, decision.effectiveProvider());
        assertTrue(decision.fallbackApplied());
        assertEquals("search.sync_backlog_stale", decision.fallbackReasonCode());
    }

    @Test
    void retriesViaPostgresWhenElasticsearchFailsAtRuntimeInAutoMode() {
        StubLexicalSearchProvider postgres = new StubLexicalSearchProvider(LexicalProviderType.POSTGRES);
        StubLexicalSearchProvider elasticsearch = new StubLexicalSearchProvider(LexicalProviderType.ELASTICSEARCH);
        elasticsearch.exceptionToThrow = new IllegalStateException("connection reset");
        ProductionLexicalSearchRouter router = router("auto", postgres, elasticsearch);

        ProductionLexicalSearchRouter.LexicalSearchResult result = router.search("pricing", 5);

        assertEquals(LexicalProviderType.POSTGRES, result.effectiveProvider());
        assertTrue(result.fallbackApplied());
        assertEquals("search.runtime_failure", result.fallbackReasonCode());
        assertEquals(1, postgres.searchCalls);
        assertEquals(1, elasticsearch.searchCalls);
    }

    @Test
    void explicitElasticsearchModeDoesNotFallbackOnRuntimeFailure() {
        StubLexicalSearchProvider postgres = new StubLexicalSearchProvider(LexicalProviderType.POSTGRES);
        StubLexicalSearchProvider elasticsearch = new StubLexicalSearchProvider(LexicalProviderType.ELASTICSEARCH);
        elasticsearch.exceptionToThrow = new IllegalStateException("cluster unavailable");
        ProductionLexicalSearchRouter router = router("elasticsearch", postgres, elasticsearch);

        assertThrows(IllegalStateException.class, () -> router.search("pricing", 5));
        assertEquals(0, postgres.searchCalls);
        assertEquals(1, elasticsearch.searchCalls);
    }

    @Test
    void latchesRuntimeFailureSoAutoModeSkipsElasticsearchUntilSafeRefresh() throws Exception {
        StubLexicalSearchProvider postgres = new StubLexicalSearchProvider(LexicalProviderType.POSTGRES);
        StubLexicalSearchProvider elasticsearch = new StubLexicalSearchProvider(LexicalProviderType.ELASTICSEARCH);
        elasticsearch.exceptionToThrow = new IllegalStateException("connection reset");
        MutableClock clock = new MutableClock(Instant.parse("2026-04-17T10:00:00Z"));
        MaterialSearchSyncQueueRepository queueRepository = org.mockito.Mockito.mock(MaterialSearchSyncQueueRepository.class);
        when(queueRepository.getSearchSyncQueueSnapshot()).thenReturn(
            new MaterialSearchSyncQueueRepository.SearchSyncQueueSnapshot(0, 0, 0, null, null)
        );
        ElasticsearchClient elasticsearchClient = org.mockito.Mockito.mock(ElasticsearchClient.class);
        when(elasticsearchClient.info()).thenReturn(null);

        ProductionLexicalSearchRouter router = router(
            "auto",
            postgres,
            elasticsearch,
            new ElasticsearchHealthService(
                properties(15),
                queueRepository,
                providerOf(elasticsearchClient),
                clock
            )
        );

        router.currentDecisionWithRefresh();
        ProductionLexicalSearchRouter.LexicalSearchResult firstResult = router.search("pricing", 5);
        ProductionLexicalSearchRouter.LexicalSearchResult secondResult = router.search("pricing", 5);
        int elasticsearchCallsAfterSecond = elasticsearch.searchCalls;
        int postgresCallsAfterSecond = postgres.searchCalls;
        clock.advanceSeconds(16);
        elasticsearch.exceptionToThrow = null;
        router.currentDecisionWithRefresh();
        ProductionLexicalSearchRouter.LexicalSearchResult thirdResult = router.search("pricing", 5);

        assertEquals(LexicalProviderType.POSTGRES, firstResult.effectiveProvider());
        assertEquals("DEGRADED", firstResult.searchHealth().clusterStatus());

        assertEquals(LexicalProviderType.POSTGRES, secondResult.effectiveProvider());
        assertEquals("search.runtime_failure", secondResult.fallbackReasonCode());
        assertEquals(1, elasticsearchCallsAfterSecond);
        assertEquals(2, postgresCallsAfterSecond);

        assertEquals(LexicalProviderType.ELASTICSEARCH, thirdResult.effectiveProvider());
        assertEquals(2, elasticsearch.searchCalls);
        assertEquals(2, postgres.searchCalls);
    }

    @Test
    void autoModeUsesPostgresUntilElasticsearchHasBeenProbed() throws Exception {
        StubLexicalSearchProvider postgres = new StubLexicalSearchProvider(LexicalProviderType.POSTGRES);
        StubLexicalSearchProvider elasticsearch = new StubLexicalSearchProvider(LexicalProviderType.ELASTICSEARCH);
        MutableClock clock = new MutableClock(Instant.parse("2026-04-17T10:00:00Z"));
        MaterialSearchSyncQueueRepository queueRepository = org.mockito.Mockito.mock(MaterialSearchSyncQueueRepository.class);
        when(queueRepository.getSearchSyncQueueSnapshot()).thenReturn(
            new MaterialSearchSyncQueueRepository.SearchSyncQueueSnapshot(0, 0, 0, null, null)
        );
        ElasticsearchClient elasticsearchClient = org.mockito.Mockito.mock(ElasticsearchClient.class);
        when(elasticsearchClient.info()).thenReturn(null);
        ProductionLexicalSearchRouter router = router(
            "auto",
            postgres,
            elasticsearch,
            new ElasticsearchHealthService(
                properties(15),
                queueRepository,
                providerOf(elasticsearchClient),
                clock
            )
        );

        ProductionLexicalSearchRouter.LexicalSearchResult result = router.search("pricing", 5);

        assertEquals(LexicalProviderType.POSTGRES, result.effectiveProvider());
        assertEquals("search.health_unprobed", result.fallbackReasonCode());
        assertEquals(1, postgres.searchCalls);
        assertEquals(0, elasticsearch.searchCalls);
        org.mockito.Mockito.verify(elasticsearchClient, org.mockito.Mockito.never()).info();
    }

    private ProductionLexicalSearchRouter router(
        String configuredMode,
        StubLexicalSearchProvider postgres,
        StubLexicalSearchProvider elasticsearch
    ) {
        ElasticsearchHealthService healthService = org.mockito.Mockito.mock(ElasticsearchHealthService.class);
        when(healthService.currentHealth()).thenReturn(health("UP", null, null));
        return router(configuredMode, postgres, elasticsearch, healthService);
    }

    private ProductionLexicalSearchRouter router(
        String configuredMode,
        StubLexicalSearchProvider postgres,
        StubLexicalSearchProvider elasticsearch,
        ElasticsearchHealthService healthService
    ) {
        RagProperties ragProperties = new RagProperties();
        ragProperties.setLexicalProvider(configuredMode);
        return new ProductionLexicalSearchRouter(
            new LexicalSearchStrategy(List.of(postgres, elasticsearch)),
            new LexicalSearchModeResolver(ragProperties),
            healthService
        );
    }

    private ElasticsearchHealthService.SearchSyncHealth health(
        String status,
        String reasonCode,
        String reasonMessage
    ) {
        return new ElasticsearchHealthService.SearchSyncHealth(
            status,
            reasonCode,
            reasonMessage,
            0,
            0,
            0,
            null,
            null,
            Instant.parse("2026-04-17T10:15:00Z")
        );
    }

    private SearchSyncProperties properties(int ttlSeconds) {
        SearchSyncProperties properties = new SearchSyncProperties();
        properties.setEnabled(true);
        properties.setHealthSnapshotTtlSeconds(ttlSeconds);
        return properties;
    }

    private ObjectProvider<ElasticsearchClient> providerOf(ElasticsearchClient elasticsearchClient) {
        return new StaticListableBeanFactory(Map.of("elasticsearchClient", elasticsearchClient))
            .getBeanProvider(ElasticsearchClient.class);
    }

    private static final class StubLexicalSearchProvider implements LexicalSearchProvider {

        private final LexicalProviderType providerType;
        private RuntimeException exceptionToThrow;
        private int searchCalls;

        private StubLexicalSearchProvider(LexicalProviderType providerType) {
            this.providerType = providerType;
        }

        @Override
        public LexicalProviderType type() {
            return providerType;
        }

        @Override
        public List<MaterialChunkSearchMatch> search(String query, int limit) {
            searchCalls++;
            if (exceptionToThrow != null) {
                throw exceptionToThrow;
            }
            return List.of(new MaterialChunkSearchMatch(
                providerType.propertyValue() + "-material",
                0,
                providerType.propertyValue(),
                query,
                null,
                "direct-text",
                false,
                null,
                1.0d
            ));
        }
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
