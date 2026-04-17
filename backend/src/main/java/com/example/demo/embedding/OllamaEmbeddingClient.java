package com.example.demo.embedding;

import com.example.demo.api.ApiException;
import com.example.demo.config.EmbeddingProperties;
import com.example.demo.config.LlmProperties;
import com.example.demo.llm.OllamaApiTransport;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class OllamaEmbeddingClient implements EmbeddingClient {

    private static final int EXPECTED_EMBEDDING_DIMENSION = 768;

    private final EmbeddingProperties embeddingProperties;
    private final LlmProperties llmProperties;
    private final OllamaApiTransport transport;

    public OllamaEmbeddingClient(
        OllamaApiTransport transport,
        EmbeddingProperties embeddingProperties,
        LlmProperties llmProperties
    ) {
        this.transport = transport;
        this.embeddingProperties = embeddingProperties;
        this.llmProperties = llmProperties;
    }

    @Override
    public float[] embed(String input) {
        List<float[]> embeddings = embedInternal(input);
        if (embeddings.size() != 1) {
            throw new ApiException(
                HttpStatus.BAD_GATEWAY,
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

        return embedInternal(inputs);
    }

    private List<float[]> embedInternal(Object input) {
        if (input instanceof String value && !StringUtils.hasText(value)) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "embedding.invalid_input",
                "Embedding input must not be blank"
            );
        }

        if (input instanceof List<?> values && values.isEmpty()) {
            return List.of();
        }

        try {
            EmbedResponse payload = transport.postJson(
                llmProperties.getBaseUrl(),
                "/api/embed",
                Duration.ofSeconds(embeddingProperties.getTimeoutSeconds()),
                Map.of(
                    "model", embeddingProperties.getModel(),
                    "input", input
                ),
                EmbedResponse.class,
                "embedding.provider_unavailable",
                "Unable to reach the local embedding provider",
                "embedding.provider_bad_response",
                "Embedding provider returned an invalid status",
                "embedding.provider_parse_failed",
                "Unable to parse the embedding provider response",
                "embedding.provider_interrupted",
                "Embedding request was interrupted",
                "embedding.invalid_configuration",
                "Invalid embedding configuration"
            );
            if (payload.embeddings() == null || payload.embeddings().isEmpty()) {
                throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "embedding.provider_empty_embedding",
                    "Embedding provider returned no vectors"
                );
            }

            return payload.embeddings().stream()
                .map(this::toFloatArray)
                .toList();
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "embedding.invalid_configuration",
                "Invalid embedding configuration: " + exception.getMessage(),
                exception
            );
        }
    }

    private float[] toFloatArray(List<Double> values) {
        if (values == null || values.isEmpty()) {
            throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                "embedding.provider_empty_embedding",
                "Embedding provider returned an empty vector"
            );
        }

        if (values.size() != EXPECTED_EMBEDDING_DIMENSION) {
            throw new ApiException(
                HttpStatus.BAD_GATEWAY,
                "embedding.provider_dimension_mismatch",
                "Embedding provider returned " + values.size()
                    + " dimensions, expected " + EXPECTED_EMBEDDING_DIMENSION
            );
        }

        float[] embedding = new float[values.size()];
        for (int index = 0; index < values.size(); index++) {
            embedding[index] = values.get(index).floatValue();
        }

        return embedding;
    }

    private record EmbedResponse(
        String model,
        List<List<Double>> embeddings
    ) {
    }
}
