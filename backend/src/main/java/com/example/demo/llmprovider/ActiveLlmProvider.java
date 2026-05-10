package com.example.demo.llmprovider;

import com.example.demo.config.EmbeddingProperties;
import com.example.demo.config.LlmProperties;
import com.example.demo.model.LlmProviderStatus;
import com.example.demo.model.LlmProviderType;
import org.springframework.util.StringUtils;

public record ActiveLlmProvider(
    String id,
    String name,
    LlmProviderType providerType,
    String baseUrl,
    String apiKey,
    String authHeaderName,
    String authScheme,
    String chatCompletionsPath,
    String modelsPath,
    String embeddingsPath,
    String defaultModel,
    String embeddingModel,
    double temperature,
    double topP,
    int timeoutSeconds,
    Integer expectedEmbeddingDimension,
    LlmProviderStatus status,
    boolean fallback
) {
    private static final String DEFAULT_AUTH_HEADER = "Authorization";
    private static final String DEFAULT_AUTH_SCHEME = "Bearer";
    private static final String DEFAULT_CHAT_PATH = "/v1/chat/completions";
    private static final String DEFAULT_MODELS_PATH = "/v1/models";
    private static final String DEFAULT_EMBEDDINGS_PATH = "/v1/embeddings";

    public static ActiveLlmProvider fromLlmProperties(LlmProperties properties) {
        return new ActiveLlmProvider(
            "default-local",
            "Default env chat provider",
            LlmProviderType.OPENAI_COMPATIBLE,
            properties.getBaseUrl(),
            properties.getApiKey(),
            DEFAULT_AUTH_HEADER,
            DEFAULT_AUTH_SCHEME,
            DEFAULT_CHAT_PATH,
            DEFAULT_MODELS_PATH,
            DEFAULT_EMBEDDINGS_PATH,
            properties.getModel(),
            null,
            properties.getTemperature(),
            properties.getTopP(),
            properties.getTimeoutSeconds(),
            null,
            LlmProviderStatus.UNKNOWN,
            true
        );
    }

    public static ActiveLlmProvider fromEmbeddingProperties(
        EmbeddingProperties embeddingProperties,
        LlmProperties llmProperties
    ) {
        String baseUrl = StringUtils.hasText(embeddingProperties.getBaseUrl())
            ? embeddingProperties.getBaseUrl().trim()
            : llmProperties.getBaseUrl();
        String apiKey = StringUtils.hasText(embeddingProperties.getApiKey())
            ? embeddingProperties.getApiKey().trim()
            : llmProperties.getApiKey();
        return new ActiveLlmProvider(
            "default-local-embeddings",
            "Default env embedding provider",
            LlmProviderType.OPENAI_COMPATIBLE,
            baseUrl,
            apiKey,
            DEFAULT_AUTH_HEADER,
            DEFAULT_AUTH_SCHEME,
            DEFAULT_CHAT_PATH,
            DEFAULT_MODELS_PATH,
            DEFAULT_EMBEDDINGS_PATH,
            llmProperties.getModel(),
            embeddingProperties.getModel(),
            llmProperties.getTemperature(),
            llmProperties.getTopP(),
            embeddingProperties.getTimeoutSeconds(),
            embeddingProperties.getExpectedDimension(),
            LlmProviderStatus.UNKNOWN,
            true
        );
    }

    public String chatModelOrDefault(String requestedModel) {
        if (StringUtils.hasText(requestedModel)) {
            return requestedModel.trim();
        }
        return defaultModel();
    }

    public String embeddingFingerprint() {
        return normalize(baseUrl) + "|" + normalize(embeddingsPath) + "|"
            + normalize(embeddingModel) + "|" + expectedEmbeddingDimension;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
