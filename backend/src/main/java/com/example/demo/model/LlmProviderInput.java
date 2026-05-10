package com.example.demo.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LlmProviderInput(
    @NotBlank
    String name,
    @NotNull
    LlmProviderType providerType,
    @NotNull
    LlmProviderPurpose purpose,
    @NotBlank
    String baseUrl,
    String apiKey,
    Boolean clearApiKey,
    String authHeaderName,
    String authScheme,
    String chatCompletionsPath,
    String modelsPath,
    String embeddingsPath,
    String defaultModel,
    String embeddingModel,
    @DecimalMin("0.0")
    @DecimalMax("2.0")
    Double temperature,
    @Min(1)
    @Max(3600)
    Integer timeoutSeconds,
    @Min(1)
    Integer expectedEmbeddingDimension,
    Boolean activeChat,
    Boolean activeEmbedding
) {
}
