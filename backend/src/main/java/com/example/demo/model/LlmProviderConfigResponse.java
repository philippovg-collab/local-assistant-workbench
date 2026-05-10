package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LlmProviderConfigResponse(
    String id,
    String name,
    LlmProviderType providerType,
    LlmProviderPurpose purpose,
    String baseUrl,
    boolean hasApiKey,
    String authHeaderName,
    String authScheme,
    String chatCompletionsPath,
    String modelsPath,
    String embeddingsPath,
    String defaultModel,
    String embeddingModel,
    double temperature,
    int timeoutSeconds,
    Integer expectedEmbeddingDimension,
    boolean activeChat,
    boolean activeEmbedding,
    LlmProviderStatus status,
    String lastProbeAt,
    String lastSuccessfulProbeAt,
    String lastErrorCode,
    String lastErrorMessage,
    String createdAt,
    String updatedAt
) {
}
