package com.example.demo.service;

import com.example.demo.config.OcrProperties;
import com.example.demo.infrastructure.material.LexicalProviderMode;
import com.example.demo.infrastructure.material.MaterialCatalogRepository;
import com.example.demo.infrastructure.material.MaterialIndexingQueueRepository;
import com.example.demo.infrastructure.material.OcrCapability;
import com.example.demo.infrastructure.material.OcrCapabilityProvider;
import com.example.demo.model.HealthResponse;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class HealthStatusService {

    private final OcrProperties ocrProperties;
    private final OcrCapabilityProvider ocrCapabilityProvider;
    private final RagStorageHealthService ragStorageHealthService;
    private final RuntimeReadinessService runtimeReadinessService;
    private final MaterialCatalogRepository materialCatalogRepository;
    private final MaterialIndexingQueueRepository indexingQueueRepository;
    private final ProductionLexicalSearchRouter productionLexicalSearchRouter;
    private final QualityLayerHealthService qualityLayerHealthService;
    private final ChatAuditService chatAuditService;

    public HealthStatusService(
        OcrProperties ocrProperties,
        OcrCapabilityProvider ocrCapabilityProvider,
        RagStorageHealthService ragStorageHealthService,
        RuntimeReadinessService runtimeReadinessService,
        MaterialCatalogRepository materialCatalogRepository,
        MaterialIndexingQueueRepository indexingQueueRepository,
        ProductionLexicalSearchRouter productionLexicalSearchRouter,
        QualityLayerHealthService qualityLayerHealthService,
        ChatAuditService chatAuditService
    ) {
        this.ocrProperties = ocrProperties;
        this.ocrCapabilityProvider = ocrCapabilityProvider;
        this.ragStorageHealthService = ragStorageHealthService;
        this.runtimeReadinessService = runtimeReadinessService;
        this.materialCatalogRepository = materialCatalogRepository;
        this.indexingQueueRepository = indexingQueueRepository;
        this.productionLexicalSearchRouter = productionLexicalSearchRouter;
        this.qualityLayerHealthService = qualityLayerHealthService;
        this.chatAuditService = chatAuditService;
    }

    public HealthResponse currentHealth() {
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
        ProductionLexicalSearchRouter.LexicalRoutingDecision lexicalRoutingDecision =
            productionLexicalSearchRouter.currentDecisionWithRefresh();
        var searchHealth = lexicalRoutingDecision.searchHealth();
        ChatAuditService.AuditHealth auditHealth = chatAuditService.currentHealth();
        String ocrStatus = !ocrProperties.isEnabled()
            ? "DISABLED"
            : capability.scannedPdfSupport() ? "UP" : "DOWN";
        boolean coreRuntimeReady = "UP".equals(runtimeReadiness.directStatus())
            && "UP".equals(runtimeReadiness.embeddingStatus())
            && storageHealth.ready();
        boolean lexicalSearchReady = lexicalSearchReady(lexicalRoutingDecision, searchHealth);
        boolean ragReady = coreRuntimeReady && lexicalSearchReady && "READY".equals(knowledgeReadiness.status());
        String ragStatus = ragReady ? "UP" : "DOWN";
        String ragDegradedReasonCode = resolveRagDegradedReasonCode(
            runtimeReadiness,
            storageHealth,
            knowledgeReadiness,
            lexicalRoutingDecision,
            searchHealth
        );
        String ragDegradedReasonMessage = resolveRagDegradedReasonMessage(
            runtimeReadiness,
            storageHealth,
            knowledgeReadiness,
            lexicalRoutingDecision,
            searchHealth
        );
        Map<String, HealthResponse.ReadinessComponent> readiness = readinessComponents(
            runtimeReadiness,
            storageHealth,
            knowledgeReadiness,
            lexicalRoutingDecision,
            searchHealth,
            auditHealth,
            indexingQueueSnapshot
        );

        return new HealthResponse(
            "spring-backend",
            overallStatus(ragReady, indexSyncReady(searchHealth), auditHealth),
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
            qualityLayerHealthService.currentHealth(),
            readiness
        );
    }

    private String resolveRagDegradedReasonCode(
        RuntimeReadinessService.RuntimeReadiness runtimeReadiness,
        RagStorageHealthService.StorageHealth storageHealth,
        KnowledgeReadiness knowledgeReadiness,
        ProductionLexicalSearchRouter.LexicalRoutingDecision lexicalRoutingDecision,
        ElasticsearchHealthService.SearchSyncHealth searchHealth
    ) {
        if (!"UP".equals(runtimeReadiness.directStatus())) {
            return runtimeReadiness.directReasonCode();
        }
        if (!"UP".equals(runtimeReadiness.embeddingStatus())) {
            return runtimeReadiness.embeddingReasonCode();
        }
        if (!"UP".equals(storageHealth.databaseStatus())) {
            return "rag.database_unavailable";
        }
        if (!"UP".equals(storageHealth.vectorStatus())) {
            return "rag.vector_unavailable";
        }
        if (!lexicalSearchReady(lexicalRoutingDecision, searchHealth)) {
            return searchHealth.reasonCode() == null ? "rag.lexical_search_unavailable" : searchHealth.reasonCode();
        }
        return knowledgeReadiness.reasonCode();
    }

    private String resolveRagDegradedReasonMessage(
        RuntimeReadinessService.RuntimeReadiness runtimeReadiness,
        RagStorageHealthService.StorageHealth storageHealth,
        KnowledgeReadiness knowledgeReadiness,
        ProductionLexicalSearchRouter.LexicalRoutingDecision lexicalRoutingDecision,
        ElasticsearchHealthService.SearchSyncHealth searchHealth
    ) {
        if (!"UP".equals(runtimeReadiness.directStatus())) {
            return runtimeReadiness.directReasonMessage();
        }
        if (!"UP".equals(runtimeReadiness.embeddingStatus())) {
            return runtimeReadiness.embeddingReasonMessage();
        }
        if (!"UP".equals(storageHealth.databaseStatus())) {
            return storageHealth.databaseReasonMessage();
        }
        if (!"UP".equals(storageHealth.vectorStatus())) {
            return storageHealth.vectorReasonMessage();
        }
        if (!lexicalSearchReady(lexicalRoutingDecision, searchHealth)) {
            return searchHealth.reasonMessage() == null
                ? "Configured lexical search provider is unavailable and no runtime fallback is active."
                : searchHealth.reasonMessage();
        }
        return knowledgeReadiness.reasonMessage();
    }

    private boolean lexicalSearchReady(
        ProductionLexicalSearchRouter.LexicalRoutingDecision lexicalRoutingDecision,
        ElasticsearchHealthService.SearchSyncHealth searchHealth
    ) {
        if (lexicalRoutingDecision == null) {
            return false;
        }
        if (lexicalRoutingDecision.configuredMode() == LexicalProviderMode.POSTGRES) {
            return true;
        }
        if (lexicalRoutingDecision.fallbackApplied()) {
            return true;
        }
        return "UP".equals(searchHealth.clusterStatus());
    }

    private String overallStatus(
        boolean ragReady,
        boolean indexSyncReady,
        ChatAuditService.AuditHealth auditHealth
    ) {
        return ragReady
            && indexSyncReady
            && (auditHealth == null || "UP".equals(auditHealth.status()))
            ? "UP"
            : "DEGRADED";
    }

    private Map<String, HealthResponse.ReadinessComponent> readinessComponents(
        RuntimeReadinessService.RuntimeReadiness runtimeReadiness,
        RagStorageHealthService.StorageHealth storageHealth,
        KnowledgeReadiness knowledgeReadiness,
        ProductionLexicalSearchRouter.LexicalRoutingDecision lexicalRoutingDecision,
        ElasticsearchHealthService.SearchSyncHealth searchHealth,
        ChatAuditService.AuditHealth auditHealth,
        MaterialIndexingQueueRepository.IndexingQueueSnapshot indexingQueueSnapshot
    ) {
        Map<String, HealthResponse.ReadinessComponent> components = new LinkedHashMap<>();
        components.put("runtime", new HealthResponse.ReadinessComponent(
            runtimeReadiness.ragStatus(),
            runtimeReadiness.directReasonCode(),
            runtimeReadiness.directReasonMessage(),
            runtimeReadiness.cachedAt()
        ));
        components.put("rag-storage", new HealthResponse.ReadinessComponent(
            storageHealth.ready() ? "UP" : "DOWN",
            storageHealth.ready() ? null : "rag.storage_unavailable",
            storageHealth.ready() ? null : firstNonNull(storageHealth.databaseReasonMessage(), storageHealth.vectorReasonMessage()),
            null
        ));
        components.put("embedding", new HealthResponse.ReadinessComponent(
            runtimeReadiness.embeddingStatus(),
            runtimeReadiness.embeddingReasonCode(),
            runtimeReadiness.embeddingReasonMessage(),
            runtimeReadiness.embeddingLastSuccessfulProbeAt()
        ));
        components.put("lexical-search", new HealthResponse.ReadinessComponent(
            lexicalSearchStatus(lexicalRoutingDecision, searchHealth),
            searchHealth.reasonCode(),
            searchHealth.reasonMessage(),
            searchHealth.lastSuccessfulSyncAt() == null ? null : searchHealth.lastSuccessfulSyncAt().toString()
        ));
        components.put("index-sync", new HealthResponse.ReadinessComponent(
            indexSyncStatus(searchHealth),
            searchHealth.failedCount() > 0 ? "search.index_sync_failed" : null,
            searchHealth.failedCount() > 0 ? "Search sync has failed queue entries." : null,
            searchHealth.lastSuccessfulSyncAt() == null ? null : searchHealth.lastSuccessfulSyncAt().toString()
        ));
        components.put("knowledge", new HealthResponse.ReadinessComponent(
            knowledgeReadiness.status(),
            knowledgeReadiness.reasonCode(),
            knowledgeReadiness.reasonMessage(),
            indexingQueueSnapshot == null || indexingQueueSnapshot.nextRetryAt() == null
                ? null
                : indexingQueueSnapshot.nextRetryAt().toString()
        ));
        components.put("audit", new HealthResponse.ReadinessComponent(
            auditHealth == null ? "UNKNOWN" : auditHealth.status(),
            auditHealth == null ? "chat_audit.health_unknown" : auditHealth.reasonCode(),
            auditHealth == null ? "Chat audit health has not been observed." : auditHealth.reasonMessage(),
            auditHealth == null ? null : auditHealth.lastStateChangedAt()
        ));
        return components;
    }

    private String lexicalSearchStatus(
        ProductionLexicalSearchRouter.LexicalRoutingDecision lexicalRoutingDecision,
        ElasticsearchHealthService.SearchSyncHealth searchHealth
    ) {
        if (lexicalRoutingDecision != null && lexicalRoutingDecision.fallbackApplied()) {
            return "FALLBACK_ACTIVE";
        }
        return lexicalSearchReady(lexicalRoutingDecision, searchHealth) ? "UP" : "DOWN";
    }

    private boolean indexSyncReady(ElasticsearchHealthService.SearchSyncHealth searchHealth) {
        return "UP".equals(indexSyncStatus(searchHealth));
    }

    private String indexSyncStatus(ElasticsearchHealthService.SearchSyncHealth searchHealth) {
        if (searchHealth.failedCount() > 0) {
            return "DEGRADED";
        }
        if (searchHealth.pendingCount() > 0 || searchHealth.inProgressCount() > 0) {
            return "DEGRADED";
        }
        return "UP";
    }

    private String firstNonNull(String primary, String fallback) {
        return primary == null ? fallback : primary;
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
