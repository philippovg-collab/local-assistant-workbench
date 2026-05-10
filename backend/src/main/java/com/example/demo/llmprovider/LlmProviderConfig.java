package com.example.demo.llmprovider;

import com.example.demo.model.LlmProviderPurpose;
import com.example.demo.model.LlmProviderStatus;
import com.example.demo.model.LlmProviderType;
import java.time.Instant;
import java.util.UUID;

public record LlmProviderConfig(
    UUID id,
    String name,
    LlmProviderType providerType,
    LlmProviderPurpose purpose,
    String baseUrl,
    String apiKeyCiphertext,
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
    Instant lastProbeAt,
    Instant lastSuccessfulProbeAt,
    String lastErrorCode,
    String lastErrorMessage,
    Instant createdAt,
    Instant updatedAt
) {
    public boolean canServeChat() {
        return purpose == LlmProviderPurpose.CHAT || purpose == LlmProviderPurpose.CHAT_AND_EMBEDDING;
    }

    public boolean canServeEmbedding() {
        return purpose == LlmProviderPurpose.EMBEDDING || purpose == LlmProviderPurpose.CHAT_AND_EMBEDDING;
    }
}
