package com.example.demo.embedding;

import com.example.demo.error.ErrorType;
import com.example.demo.error.ProviderException;
import com.example.demo.llm.OllamaApiTransport;
import com.example.demo.llmprovider.ActiveLlmProvider;
import com.example.demo.llmprovider.ActiveLlmProviderResolver;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class OpenAiCompatibleEmbeddingClient implements EmbeddingClient {

    private final OllamaApiTransport transport;
    private final Supplier<ActiveLlmProvider> embeddingProviderSupplier;

    public OpenAiCompatibleEmbeddingClient(
        OllamaApiTransport transport,
        ActiveLlmProviderResolver activeProviderResolver
    ) {
        this(transport, activeProviderResolver::resolveEmbeddingProvider);
    }

    protected OpenAiCompatibleEmbeddingClient(
        OllamaApiTransport transport,
        Supplier<ActiveLlmProvider> embeddingProviderSupplier
    ) {
        this.transport = transport;
        this.embeddingProviderSupplier = embeddingProviderSupplier;
    }

    @Override
    public float[] embed(String input) {
        List<float[]> embeddings = embedInternal(embeddingProviderSupplier.get(), input);
        if (embeddings.size() != 1) {
            throw new ProviderException(
                ErrorType.PROVIDER_BAD_RESPONSE,
                "embedding.provider_empty_embedding",
                "Embedding provider returned an unexpected number of vectors"
            );
        }

        return embeddings.getFirst();
    }

    @Override
    public List<float[]> embedAll(List<String> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return List.of();
        }

        return embedInternal(embeddingProviderSupplier.get(), inputs);
    }

    public float[] embed(ActiveLlmProvider provider, String input) {
        List<float[]> embeddings = embedInternal(provider, input);
        if (embeddings.size() != 1) {
            throw new ProviderException(
                ErrorType.PROVIDER_BAD_RESPONSE,
                "embedding.provider_empty_embedding",
                "Embedding provider returned an unexpected number of vectors"
            );
        }

        return embeddings.getFirst();
    }

    private List<float[]> embedInternal(ActiveLlmProvider provider, Object input) {
        if (input instanceof String value && !StringUtils.hasText(value)) {
            throw new ProviderException(
                ErrorType.INVALID_REQUEST,
                "embedding.invalid_input",
                "Embedding input must not be blank"
            );
        }

        if (input instanceof List<?> values && values.isEmpty()) {
            return List.of();
        }

        if (!StringUtils.hasText(provider.embeddingModel())) {
            throw new ProviderException(
                ErrorType.PROVIDER_BAD_RESPONSE,
                "embedding.model_unavailable",
                "No embedding model is configured for the active embedding provider"
            );
        }
        try {
            EmbedResponse payload = transport.postJson(
                provider.baseUrl(),
                provider.embeddingsPath(),
                Duration.ofSeconds(provider.timeoutSeconds()),
                authorizationHeaders(provider),
                Map.of(
                    "model", provider.embeddingModel(),
                    "input", input
                ),
                EmbedResponse.class,
                "embedding.provider_unavailable",
                "Unable to reach the configured embedding provider",
                "embedding.provider_bad_response",
                "Embedding provider returned an invalid status",
                "embedding.provider_parse_failed",
                "Unable to parse the embedding provider response",
                "embedding.provider_interrupted",
                "Embedding request was interrupted",
                "embedding.invalid_configuration",
                "Invalid embedding configuration"
            );
            if (payload.data() == null || payload.data().isEmpty()) {
                throw new ProviderException(
                    ErrorType.PROVIDER_BAD_RESPONSE,
                    "embedding.provider_empty_embedding",
                    "Embedding provider returned no vectors"
                );
            }

            return payload.data().stream()
                .map(EmbedData::embedding)
                .map(values -> toFloatArray(provider, values))
                .toList();
        } catch (IllegalArgumentException exception) {
            throw new ProviderException(
                ErrorType.INTERNAL,
                "embedding.invalid_configuration",
                "Invalid embedding configuration: " + exception.getMessage(),
                exception
            );
        }
    }

    private float[] toFloatArray(ActiveLlmProvider provider, List<Double> values) {
        if (values == null || values.isEmpty()) {
            throw new ProviderException(
                ErrorType.PROVIDER_BAD_RESPONSE,
                "embedding.provider_empty_embedding",
                "Embedding provider returned an empty vector"
            );
        }

        Integer configuredDimension = provider.expectedEmbeddingDimension();
        int expectedDimension = configuredDimension == null ? values.size() : Math.max(1, configuredDimension);
        if (configuredDimension != null && values.size() != expectedDimension) {
            throw new ProviderException(
                ErrorType.PROVIDER_BAD_RESPONSE,
                "embedding.provider_dimension_mismatch",
                "Embedding provider returned " + values.size()
                    + " dimensions, expected " + expectedDimension
            );
        }

        float[] embedding = new float[values.size()];
        for (int index = 0; index < values.size(); index++) {
            embedding[index] = values.get(index).floatValue();
        }

        return embedding;
    }

    private Map<String, String> authorizationHeaders(ActiveLlmProvider provider) {
        if (!StringUtils.hasText(provider.apiKey())) {
            return Map.of();
        }
        String scheme = StringUtils.hasText(provider.authScheme()) ? provider.authScheme().trim() : "";
        String value = StringUtils.hasText(scheme)
            ? scheme + " " + provider.apiKey().trim()
            : provider.apiKey().trim();
        return Map.of(provider.authHeaderName(), value);
    }

    private record EmbedResponse(
        String model,
        List<EmbedData> data
    ) {
    }

    private record EmbedData(
        List<Double> embedding
    ) {
    }
}
