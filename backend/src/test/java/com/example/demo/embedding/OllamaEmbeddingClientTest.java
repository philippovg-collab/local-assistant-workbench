package com.example.demo.embedding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.demo.api.ApiException;
import com.example.demo.config.EmbeddingProperties;
import com.example.demo.config.LlmProperties;
import com.example.demo.llm.OllamaApiTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

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

        ApiException exception = assertThrows(ApiException.class, () -> client.embed("healthcheck"));

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatus());
        assertEquals("embedding.provider_dimension_mismatch", exception.getCode());
        assertEquals("Embedding provider returned 3 dimensions, expected 4", exception.getMessage());
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
                throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    parseFailedCode,
                    parseFailedMessage,
                    exception
                );
            }
        }
    }
}
