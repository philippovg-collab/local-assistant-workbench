package com.example.demo.controller;

import com.example.demo.config.OcrProperties;
import com.example.demo.infrastructure.material.MaterialCatalogRepository;
import com.example.demo.infrastructure.material.MaterialIndexingQueueRepository;
import com.example.demo.infrastructure.material.OcrCapability;
import com.example.demo.infrastructure.material.OcrCapabilityProvider;
import com.example.demo.model.HealthResponse;
import com.example.demo.service.ProductionLexicalSearchRouter;
import com.example.demo.service.QualityLayerHealthService;
import com.example.demo.service.RagStorageHealthService;
import com.example.demo.service.RuntimeReadinessService;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final OcrProperties ocrProperties;
    private final OcrCapabilityProvider ocrCapabilityProvider;
    private final RagStorageHealthService ragStorageHealthService;
    private final RuntimeReadinessService runtimeReadinessService;
    private final MaterialCatalogRepository materialCatalogRepository;
    private final MaterialIndexingQueueRepository indexingQueueRepository;
    private final ProductionLexicalSearchRouter productionLexicalSearchRouter;
    private final QualityLayerHealthService qualityLayerHealthService;

    public HealthController(
        OcrProperties ocrProperties,
        OcrCapabilityProvider ocrCapabilityProvider,
        RagStorageHealthService ragStorageHealthService,
        RuntimeReadinessService runtimeReadinessService,
        MaterialCatalogRepository materialCatalogRepository,
        MaterialIndexingQueueRepository indexingQueueRepository,
        ProductionLexicalSearchRouter productionLexicalSearchRouter,
        QualityLayerHealthService qualityLayerHealthService
    ) {
        this.ocrProperties = ocrProperties;
        this.ocrCapabilityProvider = ocrCapabilityProvider;
        this.ragStorageHealthService = ragStorageHealthService;
        this.runtimeReadinessService = runtimeReadinessService;
        this.materialCatalogRepository = materialCatalogRepository;
        this.indexingQueueRepository = indexingQueueRepository;
        this.productionLexicalSearchRouter = productionLexicalSearchRouter;
        this.qualityLayerHealthService = qualityLayerHealthService;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        OcrCapability capability = ocrCapabilityProvider.currentCapability();
        RagStorageHealthService.StorageHealth storageHealth = ragStorageHealthService.currentHealth();
        RuntimeReadinessService.RuntimeReadiness runtimeReadiness = runtimeReadinessService.currentReadiness();
        MaterialIndexingQueueRepository.IndexingQueueSnapshot indexingQueueSnapshot = null;
        KnowledgeReadiness knowledgeReadiness;
        if (!"UP".equals(storageHealth.databaseStatus())) {
            knowledgeReadiness = KnowledgeReadiness.storageUnavailable(storageHealth.databaseReasonMessage());
        } else {
            indexingQueueSnapshot = indexingQueueRepository.getIndexingQueueSnapshot();
            knowledgeReadiness = resolveKnowledgeReadiness(indexingQueueSnapshot);
        }
        ProductionLexicalSearchRouter.LexicalRoutingDecision lexicalRoutingDecision = productionLexicalSearchRouter.currentDecisionWithRefresh();
        var searchHealth = lexicalRoutingDecision.searchHealth();
        String ocrStatus = !ocrProperties.isEnabled()
            ? "DISABLED"
            : capability.scannedPdfSupport() ? "UP" : "DOWN";
        boolean coreRuntimeReady = "UP".equals(runtimeReadiness.directStatus())
            && "UP".equals(runtimeReadiness.embeddingStatus())
            && storageHealth.ready();
        boolean ragReady = coreRuntimeReady && "READY".equals(knowledgeReadiness.status());
        String ragStatus = ragReady ? "UP" : "DOWN";
        String ragDegradedReasonCode = resolveRagDegradedReasonCode(runtimeReadiness, storageHealth, knowledgeReadiness);
        String ragDegradedReasonMessage = resolveRagDegradedReasonMessage(runtimeReadiness, storageHealth, knowledgeReadiness);

        return new HealthResponse(
            "spring-backend",
            coreRuntimeReady ? "UP" : "DEGRADED",
            Instant.now().toString(),
            runtimeReadiness.directStatus(),
            runtimeReadiness.directReasonCode(),
            runtimeReadiness.directReasonMessage(),
            runtimeReadiness.directLastSuccessfulProbeAt(),
            ragStatus,
            knowledgeReadiness.status(),
            knowledgeReadiness.reasonCode(),
            knowledgeReadiness.reasonMessage(),
            knowledgeReadiness.materialCount(),
            knowledgeReadiness.activeMaterialCount(),
            knowledgeReadiness.historicalMaterialCount(),
            knowledgeReadiness.readyMaterialCount(),
            searchHealth.clusterStatus(),
            lexicalRoutingDecision.configuredMode().propertyValue(),
            lexicalRoutingDecision.effectiveProvider().propertyValue(),
            lexicalRoutingDecision.fallbackReasonCode(),
            lexicalRoutingDecision.fallbackReasonMessage(),
            new HealthResponse.SearchSyncBacklog(
                searchHealth.pendingCount(),
                searchHealth.inProgressCount(),
                searchHealth.failedCount(),
                searchHealth.nextRetryAt() == null ? null : searchHealth.nextRetryAt().toString(),
                searchHealth.oldestOutstandingAt() == null ? null : searchHealth.oldestOutstandingAt().toString(),
                searchHealth.lastSuccessfulSyncAt() == null ? null : searchHealth.lastSuccessfulSyncAt().toString()
            ),
            runtimeReadiness.llmStatus(),
            runtimeReadiness.llmReasonCode(),
            runtimeReadiness.llmReasonMessage(),
            runtimeReadiness.embeddingStatus(),
            runtimeReadiness.embeddingReasonCode(),
            runtimeReadiness.embeddingReasonMessage(),
            runtimeReadiness.cachedAt(),
            ocrStatus,
            capability.reasonCode(),
            capability.reasonMessage(),
            capability.languages(),
            storageHealth.databaseStatus(),
            storageHealth.databaseReasonMessage(),
            storageHealth.vectorStatus(),
            storageHealth.vectorReasonMessage(),
            runtimeReadiness.llmLastSuccessfulProbeAt(),
            runtimeReadiness.embeddingLastSuccessfulProbeAt(),
            ragDegradedReasonCode,
            ragDegradedReasonMessage,
            indexingQueueSnapshot == null ? null : indexingQueueSnapshot.pendingCount(),
            indexingQueueSnapshot == null ? null : indexingQueueSnapshot.inProgressCount(),
            indexingQueueSnapshot == null ? null : indexingQueueSnapshot.failedCount(),
            indexingQueueSnapshot == null || indexingQueueSnapshot.nextRetryAt() == null
                ? null
                : indexingQueueSnapshot.nextRetryAt().toString(),
            qualityLayerHealthService.currentHealth()
        );
    }

    private String resolveRagDegradedReasonCode(
        RuntimeReadinessService.RuntimeReadiness runtimeReadiness,
        RagStorageHealthService.StorageHealth storageHealth,
        KnowledgeReadiness knowledgeReadiness
    ) {
        if ("DOWN".equals(runtimeReadiness.directStatus())) {
            return runtimeReadiness.directReasonCode();
        }
        if ("DOWN".equals(runtimeReadiness.embeddingStatus())) {
            return runtimeReadiness.embeddingReasonCode();
        }
        if (!"UP".equals(storageHealth.databaseStatus())) {
            return "rag.database_unavailable";
        }
        if (!"UP".equals(storageHealth.vectorStatus())) {
            return "rag.vector_unavailable";
        }
        return knowledgeReadiness.reasonCode();
    }

    private String resolveRagDegradedReasonMessage(
        RuntimeReadinessService.RuntimeReadiness runtimeReadiness,
        RagStorageHealthService.StorageHealth storageHealth,
        KnowledgeReadiness knowledgeReadiness
    ) {
        if ("DOWN".equals(runtimeReadiness.directStatus())) {
            return runtimeReadiness.directReasonMessage();
        }
        if ("DOWN".equals(runtimeReadiness.embeddingStatus())) {
            return runtimeReadiness.embeddingReasonMessage();
        }
        if (!"UP".equals(storageHealth.databaseStatus())) {
            return storageHealth.databaseReasonMessage();
        }
        if (!"UP".equals(storageHealth.vectorStatus())) {
            return storageHealth.vectorReasonMessage();
        }
        return knowledgeReadiness.reasonMessage();
    }

    private KnowledgeReadiness resolveKnowledgeReadiness(MaterialIndexingQueueRepository.IndexingQueueSnapshot indexingQueueSnapshot) {
        Integer materialCount = materialCatalogRepository.countMaterials();
        Integer activeMaterialCount = materialCatalogRepository.countActiveMaterials();
        Integer readyMaterialCount = materialCatalogRepository.countReadyMaterials();
        Integer historicalMaterialCount = Math.max(0, materialCount - activeMaterialCount);
        boolean indexingInFlight = indexingQueueSnapshot.pendingCount() > 0 || indexingQueueSnapshot.inProgressCount() > 0;

        if (materialCount == 0) {
            return new KnowledgeReadiness(
                "EMPTY",
                "knowledge.empty",
                "В knowledge base пока нет материалов.",
                materialCount,
                activeMaterialCount,
                historicalMaterialCount,
                readyMaterialCount
            );
        }
        if (activeMaterialCount == 0) {
            return new KnowledgeReadiness(
                "HISTORICAL_ONLY",
                "knowledge.historical_only",
                "В каталоге остались только исторические версии, поэтому RAG пока не на чем grounded.",
                materialCount,
                activeMaterialCount,
                historicalMaterialCount,
                readyMaterialCount
            );
        }
        if (readyMaterialCount > 0) {
            return new KnowledgeReadiness(
                "READY",
                null,
                null,
                materialCount,
                activeMaterialCount,
                historicalMaterialCount,
                readyMaterialCount
            );
        }
        if (indexingInFlight) {
            return new KnowledgeReadiness(
                "INDEXING",
                "knowledge.indexing_in_progress",
                "Активная версия уже принята, но индекс ещё не догнал её до READY или PARTIAL_READY.",
                materialCount,
                activeMaterialCount,
                historicalMaterialCount,
                readyMaterialCount
            );
        }
        return new KnowledgeReadiness(
            "DEGRADED",
            "knowledge.no_ready_active_versions",
            "Активные материалы есть, но ни один ещё не готов для retrieval.",
            materialCount,
            activeMaterialCount,
            historicalMaterialCount,
            readyMaterialCount
        );
    }

    private record KnowledgeReadiness(
        String status,
        String reasonCode,
        String reasonMessage,
        Integer materialCount,
        Integer activeMaterialCount,
        Integer historicalMaterialCount,
        Integer readyMaterialCount
    ) {
        private static KnowledgeReadiness storageUnavailable(String reasonMessage) {
            return new KnowledgeReadiness(
                "DEGRADED",
                "knowledge.storage_unavailable",
                reasonMessage,
                null,
                null,
                null,
                null
            );
        }
    }
}
