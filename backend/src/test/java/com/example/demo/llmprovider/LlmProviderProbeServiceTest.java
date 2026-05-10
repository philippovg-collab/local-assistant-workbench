package com.example.demo.llmprovider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.error.ErrorType;
import com.example.demo.error.ProviderException;
import com.example.demo.llm.OllamaApiTransport;
import com.example.demo.model.LlmProviderProbeResult;
import com.example.demo.model.LlmProviderPurpose;
import com.example.demo.model.LlmProviderStatus;
import com.example.demo.model.LlmProviderType;
import com.example.demo.service.cancellation.ChatCancellationToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;

class LlmProviderProbeServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void modelsEndpoint404ProducesDegradedWhenRequiredChecksPass() {
        LlmProviderConfig provider = provider(LlmProviderPurpose.CHAT, true, false);
        LlmProviderService providerService = mock(LlmProviderService.class);
        when(providerService.getProvider(provider.id())).thenReturn(provider);
        when(providerService.persistProbeResult(eq(provider.id()), any(), any(), any(), any())).thenReturn(provider);
        ActiveLlmProviderResolver resolver = mock(ActiveLlmProviderResolver.class);
        when(resolver.fromStoredChatProvider(provider)).thenReturn(runtimeProvider(provider));
        RecordingTransport transport = new RecordingTransport(objectMapper);
        transport.getFailure = new ProviderException(
            ErrorType.PROVIDER_BAD_RESPONSE,
            "llm.provider_bad_response",
            "LLM provider returned an invalid status while listing models: returned HTTP 404"
        );
        transport.chatResponseJson = successfulChatResponse();

        LlmProviderProbeResult result = new LlmProviderProbeService(providerService, resolver, transport, new LlmProviderErrorSanitizer())
            .probe(provider.id());

        assertEquals(LlmProviderStatus.DEGRADED, result.status());
        assertFalse(result.modelsAvailable());
        assertTrue(result.chatAvailable());
        assertEquals("llm_provider.models_unsupported", result.errorCode());
        verify(providerService).persistProbeResult(
            eq(provider.id()),
            eq(LlmProviderStatus.DEGRADED),
            any(),
            eq("llm_provider.models_unsupported"),
            eq("Provider models endpoint is unavailable or unsupported")
        );
    }

    @Test
    void embeddingDimensionMismatchProducesDown() {
        LlmProviderConfig provider = provider(LlmProviderPurpose.EMBEDDING, false, true);
        LlmProviderService providerService = mock(LlmProviderService.class);
        when(providerService.getProvider(provider.id())).thenReturn(provider);
        when(providerService.persistProbeResult(eq(provider.id()), any(), any(), any(), any())).thenReturn(provider);
        ActiveLlmProviderResolver resolver = mock(ActiveLlmProviderResolver.class);
        when(resolver.fromStoredChatProvider(provider)).thenReturn(runtimeProvider(provider));
        RecordingTransport transport = new RecordingTransport(objectMapper);
        transport.modelsResponseJson = "{\"data\":[{\"id\":\"corp-model\"}]}";
        transport.embeddingResponseJson = "{\"data\":[{\"embedding\":[1.0,2.0]}]}";

        LlmProviderProbeResult result = new LlmProviderProbeService(providerService, resolver, transport, new LlmProviderErrorSanitizer())
            .probe(provider.id());

        assertEquals(LlmProviderStatus.DOWN, result.status());
        assertTrue(result.modelsAvailable());
        assertFalse(result.embeddingAvailable());
        assertEquals("embedding.provider_dimension_mismatch", result.errorCode());
    }

    @Test
    void probeResultAndStoredStatusSanitizeProviderSecrets() {
        LlmProviderConfig provider = provider(LlmProviderPurpose.CHAT, true, false);
        LlmProviderService providerService = mock(LlmProviderService.class);
        when(providerService.getProvider(provider.id())).thenReturn(provider);
        when(providerService.persistProbeResult(eq(provider.id()), any(), any(), any(), any())).thenReturn(provider);
        ActiveLlmProviderResolver resolver = mock(ActiveLlmProviderResolver.class);
        when(resolver.fromStoredChatProvider(provider)).thenReturn(runtimeProvider(provider));
        RecordingTransport transport = new RecordingTransport(objectMapper);
        transport.modelsResponseJson = "{\"data\":[{\"id\":\"corp-model\"}]}";
        transport.chatFailure = new ProviderException(
            ErrorType.PROVIDER_BAD_RESPONSE,
            "llm.provider_bad_response",
            "Authorization: Bearer leaked-remote-secret apiKey=stored-secret"
        );

        LlmProviderProbeResult result = new LlmProviderProbeService(providerService, resolver, transport, new LlmProviderErrorSanitizer())
            .probe(provider.id());

        assertEquals(LlmProviderStatus.DOWN, result.status());
        assertNotNull(result.errorMessage());
        assertFalse(result.errorMessage().contains("leaked-remote-secret"));
        assertFalse(result.errorMessage().contains("stored-secret"));
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(providerService).persistProbeResult(
            eq(provider.id()),
            eq(LlmProviderStatus.DOWN),
            any(),
            eq("llm.provider_bad_response"),
            messageCaptor.capture()
        );
        assertFalse(messageCaptor.getValue().contains("leaked-remote-secret"));
        assertFalse(messageCaptor.getValue().contains("stored-secret"));
    }

    private LlmProviderConfig provider(
        LlmProviderPurpose purpose,
        boolean activeChat,
        boolean activeEmbedding
    ) {
        Instant now = Instant.parse("2026-05-10T10:00:00Z");
        return new LlmProviderConfig(
            UUID.randomUUID(),
            "Corp Provider",
            LlmProviderType.OPENAI_COMPATIBLE,
            purpose,
            "http://10.10.20.15:8000",
            null,
            "Authorization",
            "Bearer",
            "/v1/chat/completions",
            "/v1/models",
            "/v1/embeddings",
            "corp-model",
            "corp-embedding",
            0.2,
            600,
            3,
            activeChat,
            activeEmbedding,
            LlmProviderStatus.UNKNOWN,
            null,
            null,
            null,
            null,
            now,
            now
        );
    }

    private ActiveLlmProvider runtimeProvider(LlmProviderConfig provider) {
        return new ActiveLlmProvider(
            provider.id().toString(),
            provider.name(),
            provider.providerType(),
            provider.baseUrl(),
            null,
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
            provider.status(),
            false
        );
    }

    private String successfulChatResponse() {
        return """
            {
              "id": "chatcmpl-test",
              "model": "corp-model",
              "choices": [
                {
                  "message": {
                    "role": "assistant",
                    "content": "ok"
                  }
                }
              ]
            }
            """;
    }

    private static final class RecordingTransport extends OllamaApiTransport {

        private final ObjectMapper objectMapper;
        private String modelsResponseJson;
        private String chatResponseJson;
        private String embeddingResponseJson;
        private ProviderException getFailure;
        private ProviderException chatFailure;

        private RecordingTransport(ObjectMapper objectMapper) {
            super(objectMapper);
            this.objectMapper = objectMapper;
        }

        @Override
        public <T> T get(
            String baseUrl,
            String path,
            Duration timeout,
            Map<String, String> headers,
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
            if (getFailure != null) {
                throw getFailure;
            }
            try {
                return objectMapper.readValue(modelsResponseJson, responseType);
            } catch (IOException exception) {
                throw new ProviderException(ErrorType.PROVIDER_BAD_RESPONSE, parseFailedCode, parseFailedMessage, exception);
            }
        }

        @Override
        public <T> T postJson(
            String baseUrl,
            String path,
            Duration timeout,
            Map<String, String> headers,
            Object payload,
            Class<T> responseType,
            ChatCancellationToken cancellationToken,
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
            if (!path.contains("embeddings") && chatFailure != null) {
                throw chatFailure;
            }
            return read(path.contains("embeddings") ? embeddingResponseJson : chatResponseJson, responseType, parseFailedCode, parseFailedMessage);
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
            return read(path.contains("embeddings") ? embeddingResponseJson : chatResponseJson, responseType, parseFailedCode, parseFailedMessage);
        }

        private <T> T read(String json, Class<T> responseType, String parseFailedCode, String parseFailedMessage) {
            try {
                return objectMapper.readValue(json, responseType);
            } catch (IOException exception) {
                throw new ProviderException(ErrorType.PROVIDER_BAD_RESPONSE, parseFailedCode, parseFailedMessage, exception);
            }
        }
    }
}
