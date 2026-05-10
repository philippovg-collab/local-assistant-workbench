package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record HealthResponse(
    String application,
    String status,
    String timestamp,
    String directStatus,
    String directReasonCode,
    String directReasonMessage,
    String directLastSuccessfulProbeAt,
    String ragStatus,
    String knowledgeStatus,
    String knowledgeReasonCode,
    String knowledgeReasonMessage,
    Integer materialCount,
    Integer activeMaterialCount,
    Integer historicalMaterialCount,
    Integer readyMaterialCount,
    String searchStatus,
    String searchMode,
    String searchProvider,
    String searchReasonCode,
    String searchReasonMessage,
    SearchSyncBacklog searchSyncBacklog,
    String llmStatus,
    String llmReasonCode,
    String llmReasonMessage,
    String embeddingStatus,
    String embeddingReasonCode,
    String embeddingReasonMessage,
    String runtimeCachedAt,
    String ocrStatus,
    String ocrReasonCode,
    String ocrReasonMessage,
    List<String> ocrLanguages,
    String databaseStatus,
    String databaseReasonMessage,
    String vectorStatus,
    String vectorReasonMessage,
    String llmLastSuccessfulProbeAt,
    String embeddingLastSuccessfulProbeAt,
    String ragDegradedReasonCode,
    String ragDegradedReasonMessage,
    Integer indexingPendingCount,
    Integer indexingInProgressCount,
    Integer indexingFailedCount,
    String indexingNextRetryAt,
    String indexingOldestPendingAt,
    String indexingOldestInProgressAt,
    QualityLayerHealth qualityLayer,
    ActiveProviderSummary activeChatProvider,
    ActiveProviderSummary activeEmbeddingProvider,
    ContextFeatures contextFeatures,
    Map<String, ReadinessComponent> readiness
) {
    public HealthResponse(
        String application,
        String status,
        String timestamp,
        String directStatus,
        String directReasonCode,
        String directReasonMessage,
        String directLastSuccessfulProbeAt,
        String ragStatus,
        String knowledgeStatus,
        String knowledgeReasonCode,
        String knowledgeReasonMessage,
        Integer materialCount,
        Integer activeMaterialCount,
        Integer historicalMaterialCount,
        Integer readyMaterialCount,
        String searchStatus,
        String searchMode,
        String searchProvider,
        String searchReasonCode,
        String searchReasonMessage,
        SearchSyncBacklog searchSyncBacklog,
        String llmStatus,
        String llmReasonCode,
        String llmReasonMessage,
        String embeddingStatus,
        String embeddingReasonCode,
        String embeddingReasonMessage,
        String runtimeCachedAt,
        String ocrStatus,
        String ocrReasonCode,
        String ocrReasonMessage,
        List<String> ocrLanguages,
        String databaseStatus,
        String databaseReasonMessage,
        String vectorStatus,
        String vectorReasonMessage
    ) {
        this(
            application,
            status,
            timestamp,
            directStatus,
            directReasonCode,
            directReasonMessage,
            directLastSuccessfulProbeAt,
            ragStatus,
            knowledgeStatus,
            knowledgeReasonCode,
            knowledgeReasonMessage,
            materialCount,
            activeMaterialCount,
            historicalMaterialCount,
            readyMaterialCount,
            searchStatus,
            searchMode,
            searchProvider,
            searchReasonCode,
            searchReasonMessage,
            searchSyncBacklog,
            llmStatus,
            llmReasonCode,
            llmReasonMessage,
            embeddingStatus,
            embeddingReasonCode,
            embeddingReasonMessage,
            runtimeCachedAt,
            ocrStatus,
            ocrReasonCode,
            ocrReasonMessage,
            ocrLanguages,
            databaseStatus,
            databaseReasonMessage,
            vectorStatus,
            vectorReasonMessage,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SearchSyncBacklog(
        Integer pendingCount,
        Integer inProgressCount,
        Integer failedCount,
        String nextRetryAt,
        String oldestOutstandingAt,
        String lastSuccessfulSyncAt
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ReadinessComponent(
        String status,
        String reasonCode,
        String reasonMessage,
        String observedAt
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ActiveProviderSummary(
        String id,
        String name,
        LlmProviderType providerType,
        String baseUrl,
        LlmProviderStatus status,
        Boolean fallback
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ContextFeatures(
        Boolean context,
        Boolean conversations,
        Boolean history,
        Boolean sticky,
        Boolean rewrite,
        Boolean summary,
        Boolean longTermMemory
    ) {
    }
}
