package com.example.demo.embedding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.demo.error.ErrorType;
import com.example.demo.error.ProviderException;
import com.example.demo.config.EmbeddingProperties;
import com.example.demo.config.LlmProperties;
import com.example.demo.llm.OllamaApiTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OllamaEmbeddingClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsConfiguredEmbeddingDimension() {
        RecordingTransport transport = new RecordingTransport(objectMapper);
        transport.embeddingResponseJson = """
            {
              "model": "test-embed",
              "data": [{"embedding": [1.0, 2.0, 3.0]}]
            }
            """;
        EmbeddingProperties embeddingProperties = embeddingProperties(3);
        OllamaEmbeddingClient client = new OllamaEmbeddingClient(transport, embeddingProperties, llmProperties());

        float[] embedding = client.embed("healthcheck");

        assertEquals(3, embedding.length);
    }

    @Test
    void rejectsProviderDimensionMismatchAgainstConfiguredContract() {
        RecordingTransport transport = new RecordingTransport(objectMapper);
        transport.embeddingResponseJson = """
            {
              "model": "test-embed",
              "data": [{"embedding": [1.0, 2.0, 3.0]}]
            }
            """;
        EmbeddingProperties embeddingProperties = embeddingProperties(4);
        OllamaEmbeddingClient client = new OllamaEmbeddingClient(transport, embeddingProperties, llmProperties());

        ProviderException exception = assertThrows(ProviderException.class, () -> client.embed("healthcheck"));

        assertEquals(ErrorType.PROVIDER_BAD_RESPONSE, exception.getType());
        assertEquals("embedding.provider_dimension_mismatch", exception.getCode());
        assertEquals("Embedding provider returned 3 dimensions, expected 4", exception.getMessage());
    }

    @Test
    void rejectsMissingEmbeddingModelBeforeCallingProvider() {
        RecordingTransport transport = new RecordingTransport(objectMapper);
        EmbeddingProperties embeddingProperties = embeddingProperties(3);
        embeddingProperties.setModel("");
        OllamaEmbeddingClient client = new OllamaEmbeddingClient(transport, embeddingProperties, llmProperties());

        ProviderException exception = assertThrows(ProviderException.class, () -> client.embed("healthcheck"));

        assertEquals("embedding.model_unavailable", exception.getCode());
    }

    private EmbeddingProperties embeddingProperties(int expectedDimension) {
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setModel("test-embed");
        properties.setTimeoutSeconds(5);
        properties.setExpectedDimension(expectedDimension);
        return properties;
    }

    private LlmProperties llmProperties() {
        LlmProperties properties = new LlmProperties();
        properties.setBaseUrl("http://127.0.0.1:11434");
        return properties;
    }

    private static final class RecordingTransport extends OllamaApiTransport {

        private final ObjectMapper objectMapper;
        private String embeddingResponseJson;

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
