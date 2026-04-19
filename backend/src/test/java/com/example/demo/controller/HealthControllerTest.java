package com.example.demo.controller;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.config.OcrProperties;
import com.example.demo.config.RolloutProperties;
import com.example.demo.infrastructure.material.LexicalProviderMode;
import com.example.demo.infrastructure.material.LexicalProviderType;
import com.example.demo.infrastructure.material.MaterialCatalogRepository;
import com.example.demo.infrastructure.material.MaterialIndexingQueueRepository;
import com.example.demo.infrastructure.material.OcrCapability;
import com.example.demo.infrastructure.material.OcrCapabilityProvider;
import com.example.demo.service.ChatAuditService;
import com.example.demo.service.ElasticsearchHealthService;
import com.example.demo.service.HealthStatusService;
import com.example.demo.service.ProductionLexicalSearchRouter;
import com.example.demo.service.QualityLayerHealthService;
import com.example.demo.service.RagStorageHealthService;
import com.example.demo.service.RuntimeReadinessService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class HealthControllerTest {

    private MockMvc mockMvc;
    private OcrCapabilityProvider ocrCapabilityProvider;
    private RagStorageHealthService ragStorageHealthService;
    private RuntimeReadinessService runtimeReadinessService;
    private MaterialCatalogRepository materialCatalogRepository;
    private MaterialIndexingQueueRepository indexingQueueRepository;
    private ProductionLexicalSearchRouter productionLexicalSearchRouter;
    private ChatAuditService chatAuditService;

    @BeforeEach
    void setUp() {
        OcrProperties ocrProperties = new OcrProperties();
        ocrCapabilityProvider = org.mockito.Mockito.mock(OcrCapabilityProvider.class);
        ragStorageHealthService = org.mockito.Mockito.mock(RagStorageHealthService.class);
        runtimeReadinessService = org.mockito.Mockito.mock(RuntimeReadinessService.class);
        materialCatalogRepository = org.mockito.Mockito.mock(MaterialCatalogRepository.class);
        indexingQueueRepository = org.mockito.Mockito.mock(MaterialIndexingQueueRepository.class);
        productionLexicalSearchRouter = org.mockito.Mockito.mock(ProductionLexicalSearchRouter.class);
        chatAuditService = org.mockito.Mockito.mock(ChatAuditService.class);
        when(chatAuditService.currentHealth()).thenReturn(new ChatAuditService.AuditHealth("UP", null, null, 0, null));
        mockMvc = MockMvcBuilders
            .standaloneSetup(new HealthController(new HealthStatusService(
                ocrProperties,
                ocrCapabilityProvider,
                ragStorageHealthService,
                runtimeReadinessService,
                materialCatalogRepository,
                indexingQueueRepository,
                productionLexicalSearchRouter,
                QualityLayerHealthService.noop(new RolloutProperties()),
                chatAuditService
            )))
            .setMessageConverters(new MappingJackson2HttpMessageConverter())
            .build();
    }

    @Test
    void reportsEmptyCorpusAsRuntimeUpButRagDown() throws Exception {
        stubHealthyOcrAndStorage();
        when(runtimeReadinessService.currentReadiness()).thenReturn(runtimeReadiness("UP", null, null, "UP", null, "UP", null));
        when(materialCatalogRepository.countMaterials()).thenReturn(0);
        when(materialCatalogRepository.countActiveMaterials()).thenReturn(0);
        when(materialCatalogRepository.countReadyMaterials()).thenReturn(0);
        when(indexingQueueRepository.getIndexingQueueSnapshot()).thenReturn(
            new MaterialIndexingQueueRepository.IndexingQueueSnapshot(0, 0, 0, null)
        );
        stubRoutingDecision(
            LexicalProviderMode.POSTGRES,
            LexicalProviderType.POSTGRES,
            false,
            null,
            null,
            new ElasticsearchHealthService.SearchSyncHealth("UP", null, null, 0, 0, 0, null, null, null)
        );

        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DEGRADED"))
            .andExpect(jsonPath("$.ragStatus").value("DOWN"))
            .andExpect(jsonPath("$.knowledgeStatus").value("EMPTY"))
            .andExpect(jsonPath("$.knowledgeReasonCode").value("knowledge.empty"))
            .andExpect(jsonPath("$.qualityLayer.flags.metadataV1").value(false))
            .andExpect(jsonPath("$.qualityLayer.retrievalWindow.sampleSize").value(0))
            .andExpect(jsonPath("$.materialCount").value(0))
            .andExpect(jsonPath("$.activeMaterialCount").value(0))
            .andExpect(jsonPath("$.historicalMaterialCount").value(0))
            .andExpect(jsonPath("$.readyMaterialCount").value(0));
    }

    @Test
    void reportsHistoricalOnlyCorpusSeparatelyFromRuntimeHealth() throws Exception {
        stubHealthyOcrAndStorage();
        when(runtimeReadinessService.currentReadiness()).thenReturn(runtimeReadiness("UP", null, null, "UP", null, "UP", null));
        when(materialCatalogRepository.countMaterials()).thenReturn(3);
        when(materialCatalogRepository.countActiveMaterials()).thenReturn(0);
        when(materialCatalogRepository.countReadyMaterials()).thenReturn(0);
        when(indexingQueueRepository.getIndexingQueueSnapshot()).thenReturn(
            new MaterialIndexingQueueRepository.IndexingQueueSnapshot(0, 0, 0, null)
        );
        stubRoutingDecision(
            LexicalProviderMode.POSTGRES,
            LexicalProviderType.POSTGRES,
            false,
            null,
            null,
            new ElasticsearchHealthService.SearchSyncHealth("UP", null, null, 0, 0, 0, null, null, null)
        );

        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DEGRADED"))
            .andExpect(jsonPath("$.ragStatus").value("DOWN"))
            .andExpect(jsonPath("$.knowledgeStatus").value("HISTORICAL_ONLY"))
            .andExpect(jsonPath("$.knowledgeReasonCode").value("knowledge.historical_only"))
            .andExpect(jsonPath("$.materialCount").value(3))
            .andExpect(jsonPath("$.activeMaterialCount").value(0))
            .andExpect(jsonPath("$.historicalMaterialCount").value(3));
    }

    @Test
    void reportsIndexingCorpusAsNotReadyYetWithoutDegradingBackend() throws Exception {
        stubHealthyOcrAndStorage();
        when(runtimeReadinessService.currentReadiness()).thenReturn(runtimeReadiness("UP", null, null, "UP", null, "UP", null));
        when(materialCatalogRepository.countMaterials()).thenReturn(1);
        when(materialCatalogRepository.countActiveMaterials()).thenReturn(1);
        when(materialCatalogRepository.countReadyMaterials()).thenReturn(0);
        when(indexingQueueRepository.getIndexingQueueSnapshot()).thenReturn(
            new MaterialIndexingQueueRepository.IndexingQueueSnapshot(1, 1, 0, Instant.parse("2026-04-17T10:05:00Z"))
        );
        stubRoutingDecision(
            LexicalProviderMode.POSTGRES,
            LexicalProviderType.POSTGRES,
            false,
            null,
            null,
            new ElasticsearchHealthService.SearchSyncHealth(
                "UP",
                null,
                null,
                0,
                0,
                0,
                null,
                null,
                Instant.parse("2026-04-17T10:02:00Z")
            )
        );

        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DEGRADED"))
            .andExpect(jsonPath("$.ragStatus").value("DOWN"))
            .andExpect(jsonPath("$.knowledgeStatus").value("INDEXING"))
            .andExpect(jsonPath("$.knowledgeReasonCode").value("knowledge.indexing_in_progress"))
            .andExpect(jsonPath("$.indexingPendingCount").value(1))
            .andExpect(jsonPath("$.indexingInProgressCount").value(1));
    }

    @Test
    void reportsHealthyReadyCorpusUsingBackendTruthModel() throws Exception {
        stubHealthyOcrAndStorage();
        when(runtimeReadinessService.currentReadiness()).thenReturn(runtimeReadiness("UP", null, null, "UP", null, "UP", null));
        when(materialCatalogRepository.countMaterials()).thenReturn(2);
        when(materialCatalogRepository.countActiveMaterials()).thenReturn(1);
        when(materialCatalogRepository.countReadyMaterials()).thenReturn(1);
        when(indexingQueueRepository.getIndexingQueueSnapshot()).thenReturn(
            new MaterialIndexingQueueRepository.IndexingQueueSnapshot(1, 0, 0, Instant.parse("2026-04-17T10:05:00Z"))
        );
        stubRoutingDecision(
            LexicalProviderMode.POSTGRES,
            LexicalProviderType.POSTGRES,
            false,
            null,
            null,
            new ElasticsearchHealthService.SearchSyncHealth(
                "UP",
                null,
                null,
                0,
                0,
                0,
                null,
                null,
                Instant.parse("2026-04-17T10:02:00Z")
            )
        );

        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.ragStatus").value("UP"))
            .andExpect(jsonPath("$.knowledgeStatus").value("READY"))
            .andExpect(jsonPath("$.searchStatus").value("UP"))
            .andExpect(jsonPath("$.runtimeCachedAt").value("2026-04-17T10:00:00Z"))
            .andExpect(jsonPath("$.directLastSuccessfulProbeAt").value("2026-04-17T09:58:45Z"))
            .andExpect(jsonPath("$.llmLastSuccessfulProbeAt").value("2026-04-17T09:59:00Z"))
            .andExpect(jsonPath("$.embeddingLastSuccessfulProbeAt").value("2026-04-17T09:59:30Z"))
            .andExpect(jsonPath("$.databaseStatus").value("UP"))
            .andExpect(jsonPath("$.vectorStatus").value("UP"))
            .andExpect(jsonPath("$.ocrStatus").value("UP"))
            .andExpect(jsonPath("$.ocrLanguages[0]").value("kaz"));
    }

    @Test
    void reportsDirectChatFailureSeparatelyFromModelCatalogAvailability() throws Exception {
        stubHealthyOcrAndStorage();
        when(runtimeReadinessService.currentReadiness()).thenReturn(runtimeReadiness(
            "DOWN",
            "llm.chat_probe_failed",
            "Direct chat probe failed: timeout",
            "UP",
            null,
            "UP",
            null
        ));
        when(materialCatalogRepository.countMaterials()).thenReturn(1);
        when(materialCatalogRepository.countActiveMaterials()).thenReturn(1);
        when(materialCatalogRepository.countReadyMaterials()).thenReturn(1);
        when(indexingQueueRepository.getIndexingQueueSnapshot()).thenReturn(
            new MaterialIndexingQueueRepository.IndexingQueueSnapshot(0, 1, 2, Instant.parse("2026-04-17T10:15:00Z"))
        );
        stubRoutingDecision(
            LexicalProviderMode.AUTO,
            LexicalProviderType.POSTGRES,
            true,
            "search.cluster_unavailable",
            "Elasticsearch cluster is unavailable: connection refused",
            new ElasticsearchHealthService.SearchSyncHealth(
                "DOWN",
                "search.cluster_unavailable",
                "Elasticsearch cluster is unavailable: connection refused",
                2,
                1,
                3,
                Instant.parse("2026-04-17T10:16:00Z"),
                Instant.parse("2026-04-17T10:01:00Z"),
                null
            )
        );

        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DEGRADED"))
            .andExpect(jsonPath("$.directStatus").value("DOWN"))
            .andExpect(jsonPath("$.directReasonCode").value("llm.chat_probe_failed"))
            .andExpect(jsonPath("$.directReasonMessage").value("Direct chat probe failed: timeout"))
            .andExpect(jsonPath("$.llmStatus").value("UP"))
            .andExpect(jsonPath("$.ragStatus").value("DOWN"))
            .andExpect(jsonPath("$.ragDegradedReasonCode").value("llm.chat_probe_failed"))
            .andExpect(jsonPath("$.searchStatus").value("DOWN"))
            .andExpect(jsonPath("$.searchProvider").value("postgres"))
            .andExpect(jsonPath("$.searchSyncBacklog.failedCount").value(3))
            .andExpect(jsonPath("$.indexingInProgressCount").value(1))
            .andExpect(jsonPath("$.indexingFailedCount").value(2));
    }

    @Test
    void returnsDegradedHealthPayloadWhenDatabaseIsUnavailable() throws Exception {
        when(ocrCapabilityProvider.currentCapability()).thenReturn(
            OcrCapability.embeddedTextAndOcr(List.of("kaz", "rus", "eng"), 12)
        );
        when(ragStorageHealthService.currentHealth()).thenReturn(
            new RagStorageHealthService.StorageHealth(
                "DOWN",
                "PostgreSQL connection is unavailable: connection refused",
                "DOWN",
                "pgvector is unavailable because PostgreSQL is unreachable"
            )
        );
        when(runtimeReadinessService.currentReadiness()).thenReturn(runtimeReadiness("UP", null, null, "UP", null, "UP", null));
        stubRoutingDecision(
            LexicalProviderMode.POSTGRES,
            LexicalProviderType.POSTGRES,
            false,
            null,
            null,
            new ElasticsearchHealthService.SearchSyncHealth("UP", null, null, 0, 0, 0, null, null, null)
        );

        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DEGRADED"))
            .andExpect(jsonPath("$.ragStatus").value("DOWN"))
            .andExpect(jsonPath("$.knowledgeStatus").value("DEGRADED"))
            .andExpect(jsonPath("$.knowledgeReasonCode").value("knowledge.storage_unavailable"))
            .andExpect(jsonPath("$.knowledgeReasonMessage").value("PostgreSQL connection is unavailable: connection refused"))
            .andExpect(jsonPath("$.materialCount").doesNotExist())
            .andExpect(jsonPath("$.activeMaterialCount").doesNotExist())
            .andExpect(jsonPath("$.readyMaterialCount").doesNotExist())
            .andExpect(jsonPath("$.historicalMaterialCount").doesNotExist())
            .andExpect(jsonPath("$.indexingPendingCount").doesNotExist())
            .andExpect(jsonPath("$.indexingInProgressCount").doesNotExist())
            .andExpect(jsonPath("$.indexingFailedCount").doesNotExist());

        verify(materialCatalogRepository, never()).countMaterials();
        verify(materialCatalogRepository, never()).countActiveMaterials();
        verify(materialCatalogRepository, never()).countReadyMaterials();
        verify(indexingQueueRepository, never()).getIndexingQueueSnapshot();
    }

    private void stubHealthyOcrAndStorage() {
        when(ocrCapabilityProvider.currentCapability()).thenReturn(
            OcrCapability.embeddedTextAndOcr(List.of("kaz", "rus", "eng"), 12)
        );
        when(ragStorageHealthService.currentHealth()).thenReturn(
            new RagStorageHealthService.StorageHealth("UP", null, "UP", null)
        );
    }

    private RuntimeReadinessService.RuntimeReadiness runtimeReadiness(
        String directStatus,
        String directReasonCode,
        String llmStatus,
        String llmReasonCode,
        String embeddingStatus,
        String embeddingReasonCode
    ) {
        return runtimeReadiness(
            directStatus,
            directReasonCode,
            null,
            llmStatus,
            llmReasonCode,
            embeddingStatus,
            embeddingReasonCode
        );
    }

    private RuntimeReadinessService.RuntimeReadiness runtimeReadiness(
        String directStatus,
        String directReasonCode,
        String directReasonMessage,
        String llmStatus,
        String llmReasonCode,
        String embeddingStatus,
        String embeddingReasonCode
    ) {
        return new RuntimeReadinessService.RuntimeReadiness(
            directStatus,
            "UP".equals(directStatus) && "UP".equals(embeddingStatus) ? "UP" : "DOWN",
            directReasonCode,
            directReasonMessage,
            llmStatus,
            llmReasonCode,
            llmReasonCode == null ? null : directReasonMessage,
            embeddingStatus,
            embeddingReasonCode,
            embeddingReasonCode == null ? null : "Embedding provider is unavailable",
            "2026-04-17T10:00:00Z",
            "2026-04-17T09:58:45Z",
            "2026-04-17T09:59:00Z",
            "2026-04-17T09:59:30Z"
        );
    }

    private void stubRoutingDecision(
        LexicalProviderMode configuredMode,
        LexicalProviderType effectiveProvider,
        boolean fallbackApplied,
        String fallbackReasonCode,
        String fallbackReasonMessage,
        ElasticsearchHealthService.SearchSyncHealth searchSyncHealth
    ) {
        ProductionLexicalSearchRouter.LexicalRoutingDecision decision =
            new ProductionLexicalSearchRouter.LexicalRoutingDecision(
                configuredMode,
                effectiveProvider,
                fallbackApplied,
                fallbackReasonCode,
                fallbackReasonMessage,
                searchSyncHealth
            );
        when(productionLexicalSearchRouter.currentDecisionWithRefresh()).thenReturn(decision);
    }
}
