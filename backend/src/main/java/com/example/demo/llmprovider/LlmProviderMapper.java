package com.example.demo.llmprovider;

import com.example.demo.model.LlmProviderConfigResponse;
import java.time.Instant;

public final class LlmProviderMapper {

    private LlmProviderMapper() {
    }

    public static LlmProviderConfigResponse toResponse(LlmProviderConfig provider) {
        return new LlmProviderConfigResponse(
            provider.id().toString(),
            provider.name(),
            provider.providerType(),
            provider.purpose(),
            provider.baseUrl(),
            provider.apiKeyCiphertext() != null && !provider.apiKeyCiphertext().isBlank(),
            provider.authHeaderName(),
            provider.authScheme(),
            provider.chatCompletionsPath(),
            provider.modelsPath(),
            provider.embeddingsPath(),
            provider.defaultModel(),
            provider.embeddingModel(),
            provider.temperature(),
            provider.timeoutSeconds(),
            provider.expectedEmbeddingDimension(),
            provider.activeChat(),
            provider.activeEmbedding(),
            provider.status(),
            timestamp(provider.lastProbeAt()),
            timestamp(provider.lastSuccessfulProbeAt()),
            provider.lastErrorCode(),
            provider.lastErrorMessage(),
            timestamp(provider.createdAt()),
            timestamp(provider.updatedAt())
        );
    }

    private static String timestamp(Instant instant) {
        return instant == null ? null : instant.toString();
    }
}
