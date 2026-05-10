package com.example.demo.embedding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.demo.error.ErrorType;
import com.example.demo.error.ProviderException;
import com.example.demo.llm.OllamaApiTransport;
import com.example.demo.llmprovider.ActiveLlmProvider;
import com.example.demo.model.LlmProviderStatus;
import com.example.demo.model.LlmProviderType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleEmbeddingClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void embedAllUsesActiveEmbeddingProviderBatchInputHeadersAndDimension() {
        RecordingTransport transport = new RecordingTransport(objectMapper);
        transport.embeddingResponseJson = """
            {
              "model": "active-embedding",
              "data": [
                {"embedding": [1.0, 2.0, 3.0]},
                {"embedding": [4.0, 5.0, 6.0]}
              ]
            }
            """;
        OpenAiCompatibleEmbeddingClient client = new OpenAiCompatibleEmbeddingClient(
            transport,
            () -> activeProvider("active-embedding", 3)
        );

        List<float[]> embeddings = client.embedAll(List.of("first", "second"));

        JsonNode payload = objectMapper.valueToTree(transport.lastPayload);
        assertEquals("http://active-embedding.local", transport.lastBaseUrl);
        assertEquals("/embedding/vectorize", transport.lastPath);
        assertEquals(Duration.ofSeconds(11), transport.lastTimeout);
        assertEquals("Token secret-key", transport.lastHeaders.get("X-Embedding-Token"));
        assertEquals("active-embedding", payload.get("model").asText());
        assertEquals("first", payload.get("input").get(0).asText());
        assertEquals("second", payload.get("input").get(1).asText());
        assertEquals(2, embeddings.size());
        assertEquals(3, embeddings.getFirst().length);
        assertEquals(3, embeddings.getLast().length);
    }

    @Test
    void missingActiveEmbeddingModelFailsBeforeCallingProvider() {
        RecordingTransport transport = new RecordingTransport(objectMapper);
        OpenAiCompatibleEmbeddingClient client = new OpenAiCompatibleEmbeddingClient(
            transport,
            () -> activeProvider("", 3)
        );

        ProviderException exception = assertThrows(
            ProviderException.class,
            () -> client.embed("healthcheck")
        );

        assertEquals(ErrorType.PROVIDER_BAD_RESPONSE, exception.getType());
        assertEquals("embedding.model_unavailable", exception.getCode());
        assertEquals(null, transport.lastPath);
    }

    @Test
    void validatesReturnedDimensionsAgainstActiveProviderContract() {
        RecordingTransport transport = new RecordingTransport(objectMapper);
        transport.embeddingResponseJson = """
            {
              "model": "active-embedding",
              "data": [{"embedding": [1.0, 2.0, 3.0]}]
            }
            """;
        OpenAiCompatibleEmbeddingClient client = new OpenAiCompatibleEmbeddingClient(
            transport,
            () -> activeProvider("active-embedding", 4)
        );

        ProviderException exception = assertThrows(
            ProviderException.class,
            () -> client.embed("healthcheck")
        );

        assertEquals("embedding.provider_dimension_mismatch", exception.getCode());
        assertEquals("Embedding provider returned 3 dimensions, expected 4", exception.getMessage());
    }

    private ActiveLlmProvider activeProvider(String embeddingModel, Integer expectedDimension) {
        return new ActiveLlmProvider(
            "embedding-provider-id",
            "Embedding Provider",
            LlmProviderType.OPENAI_COMPATIBLE,
            "http://active-embedding.local",
            "secret-key",
            "X-Embedding-Token",
            "Token",
            "/chat/completions",
            "/models",
            "/embedding/vectorize",
            "chat-model",
            embeddingModel,
            0.2d,
            0.9d,
            11,
            expectedDimension,
            LlmProviderStatus.UP,
            false
        );
    }

    private static final class RecordingTransport extends OllamaApiTransport {

        private final ObjectMapper objectMapper;
        private String embeddingResponseJson;
        private String lastBaseUrl;
        private String lastPath;
        private Duration lastTimeout;
        private Map<String, String> lastHeaders;
        private Object lastPayload;

        private RecordingTransport(ObjectMapper objectMapper) {
            super(objectMapper);
            this.objectMapper = objectMapper;
        }

        @Override
        public <T> T postJson(
            String baseUrl,
            String path,
            Duration timeout,
            Map<String, String> headers,
            Object payload,
            Class<T> responseType,
            String unavailableCode,
            String unavailableMessage,
            String badResponseCode,
            String badResponseMessage,
            String parseFailedCode,
            String parseFailedMessage,
            String interruptedCode,
            String interruptedMessage,
            String invalidConfigurationCode,
            String invalidConfigurationMessage
        ) {
            lastBaseUrl = baseUrl;
            lastPath = path;
            lastTimeout = timeout;
            lastHeaders = headers;
            lastPayload = payload;
            try {
                return objectMapper.readValue(embeddingResponseJson, responseType);
            } catch (IOException exception) {
                throw new ProviderException(
                    ErrorType.PROVIDER_BAD_RESPONSE,
                    parseFailedCode,
                    parseFailedMessage,
                    exception
                );
            }
        }
    }
}
