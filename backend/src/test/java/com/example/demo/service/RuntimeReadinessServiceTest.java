package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.error.ApplicationException;
import com.example.demo.error.ErrorType;
import com.example.demo.error.ProviderException;
import com.example.demo.config.EmbeddingProperties;
import com.example.demo.config.HealthProperties;
import com.example.demo.config.LlmProperties;
import com.example.demo.embedding.EmbeddingClient;
import com.example.demo.llm.OllamaApiTransport;
import com.example.demo.llm.OllamaLlmClient;
import com.example.demo.llm.LlmClient;
import com.example.demo.llmprovider.ActiveLlmProvider;
import com.example.demo.llmprovider.ActiveLlmProviderResolver;
import com.example.demo.llmprovider.LlmProviderErrorSanitizer;
import com.example.demo.llmprovider.LlmProviderService;
import com.example.demo.model.LlmProviderStatus;
import com.example.demo.model.LlmProviderType;
import com.example.demo.model.OllamaModelInfo;
import com.example.demo.service.cancellation.ChatCancellationToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RuntimeReadinessServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void cachesReadinessWithinConfiguredTtl() {
        CountingLlmClient llmClient = new CountingLlmClient();
        HealthProperties healthProperties = new HealthProperties();
        healthProperties.setReadinessCacheSeconds(60);

        RuntimeReadinessService service = new RuntimeReadinessService(
            llmClient,
            new StaticEmbeddingClient(false),
            new LlmProperties(),
            healthProperties
        );

        RuntimeReadinessService.RuntimeReadiness first = service.currentReadiness();
        RuntimeReadinessService.RuntimeReadiness second = service.currentReadiness();

        assertEquals(1, llmClient.listModelCalls);
        assertEquals(1, llmClient.chatCalls);
        assertEquals(first.cachedAt(), second.cachedAt());
        assertEquals("UP", second.directStatus());
        assertEquals("UP", second.llmStatus());
        assertEquals("UP", second.embeddingStatus());
    }

    @Test
    void reportsEmbeddingDownWhenEmbeddingProbeFails() {
        RuntimeReadinessService service = new RuntimeReadinessService(
            new StaticLlmClient(),
            new StaticEmbeddingClient(true),
            new LlmProperties(),
            new HealthProperties()
        );

        RuntimeReadinessService.RuntimeReadiness readiness = service.currentReadiness();

        assertEquals("UP", readiness.directStatus());
        assertNull(readiness.directReasonCode());
        assertEquals("DOWN", readiness.ragStatus());
        assertEquals("DOWN", readiness.embeddingStatus());
        assertEquals("embedding.provider_unavailable", readiness.embeddingReasonCode());
    }

    @Test
    void reportsDirectDownWhenChatProbeFailsButModelCatalogIsHealthy() {
        RuntimeReadinessService service = new RuntimeReadinessService(
            new StaticLlmClient(true),
            new StaticEmbeddingClient(false),
            new LlmProperties(),
            new HealthProperties()
        );

        RuntimeReadinessService.RuntimeReadiness readiness = service.currentReadiness();

        assertEquals("UP", readiness.llmStatus());
        assertEquals("DOWN", readiness.directStatus());
        assertEquals("llm.provider_unavailable", readiness.directReasonCode());
        assertEquals("Unable to reach the local LLM provider", readiness.directReasonMessage());
    }

    @Test
    void skipsChatProbeWhenConfiguredModelIsMissingFromCatalog() {
        CountingLlmClient llmClient = new CountingLlmClient();
        llmClient.availableModels = List.of(new OllamaModelInfo("phi4-mini"));
        LlmProperties llmProperties = new LlmProperties();
        llmProperties.setModel("qwen2.5:7b");

        RuntimeReadinessService service = new RuntimeReadinessService(
            llmClient,
            new StaticEmbeddingClient(false),
            llmProperties,
            new HealthProperties()
        );

        RuntimeReadinessService.RuntimeReadiness readiness = service.currentReadiness();

        assertEquals("DOWN", readiness.llmStatus());
        assertEquals("DOWN", readiness.directStatus());
        assertEquals("llm.model_unavailable", readiness.llmReasonCode());
        assertEquals("llm.model_unavailable", readiness.directReasonCode());
        assertEquals(1, llmClient.listModelCalls);
        assertEquals(0, llmClient.chatCalls);
    }

    @Test
    void coalescesConcurrentRefreshesOnCacheMiss() throws Exception {
        SlowCountingLlmClient llmClient = new SlowCountingLlmClient();
        RuntimeReadinessService service = new RuntimeReadinessService(
            llmClient,
            new StaticEmbeddingClient(false),
            new LlmProperties(),
            new HealthProperties()
        );
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(8);
        try {
            List<java.util.concurrent.Future<RuntimeReadinessService.RuntimeReadiness>> futures =
                java.util.stream.IntStream.range(0, 8)
                    .mapToObj(index -> executor.submit(() -> {
                        start.await();
                        return service.currentReadiness();
                    }))
                    .toList();

            start.countDown();
            for (var future : futures) {
                assertEquals("UP", future.get(2, TimeUnit.SECONDS).directStatus());
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, llmClient.listModelCalls.get());
        assertEquals(1, llmClient.chatCalls.get());
    }

    @Test
    void doesNotPersistReadinessWhenActiveProviderChangesDuringProbe() {
        ActiveLlmProvider oldProvider = activeProvider(UUID.randomUUID(), "old-model");
        ActiveLlmProvider newProvider = activeProvider(UUID.randomUUID(), "new-model");
        ActiveLlmProviderResolver resolver = mock(ActiveLlmProviderResolver.class);
        when(resolver.resolveChatProvider()).thenReturn(oldProvider, newProvider);
        when(resolver.resolveEmbeddingProvider()).thenReturn(fallbackEmbeddingProvider());
        LlmProviderService providerService = mock(LlmProviderService.class);
        RuntimeReadinessService service = new RuntimeReadinessService(
            new StaticLlmClient(),
            new StaticEmbeddingClient(false),
            new LlmProperties(),
            resolver,
            providerService,
            new LlmProviderErrorSanitizer(),
            new HealthProperties()
        );

        service.refreshReadiness();

        verify(providerService, never()).persistProbeResult(any(), any(), any(), any(), any());
    }

    @Test
    void keepsUnsupportedModelsEndpointDegradedInReadinessStatus() {
        UUID providerId = UUID.randomUUID();
        ActiveLlmProvider provider = activeProvider(providerId, "qwen2.5:7b");
        ActiveLlmProviderResolver resolver = mock(ActiveLlmProviderResolver.class);
        when(resolver.resolveChatProvider()).thenReturn(provider);
        when(resolver.resolveEmbeddingProvider()).thenReturn(fallbackEmbeddingProvider());
        LlmProviderService providerService = mock(LlmProviderService.class);
        RecordingTransport transport = new RecordingTransport(objectMapper);
        transport.getFailure = new ProviderException(
            ErrorType.PROVIDER_BAD_RESPONSE,
            "llm.provider_bad_response",
            "LLM provider returned an invalid status while listing models: returned HTTP 404"
        );
        transport.chatResponseJson = """
            {
              "id": "chatcmpl-test",
              "model": "qwen2.5:7b",
              "choices": [{"message": {"role": "assistant", "content": "ok"}}]
            }
            """;

        RuntimeReadinessService service = new RuntimeReadinessService(
            new OllamaLlmClient(transport, new LlmProperties()),
            new StaticEmbeddingClient(false),
            new LlmProperties(),
            resolver,
            providerService,
            new LlmProviderErrorSanitizer(),
            new HealthProperties()
        );

        RuntimeReadinessService.RuntimeReadiness readiness = service.refreshReadiness();

        assertEquals("DEGRADED", readiness.llmStatus());
        assertEquals("UP", readiness.directStatus());
        verify(providerService).persistProbeResult(
            eq(providerId),
            eq(LlmProviderStatus.DEGRADED),
            any(),
            eq("llm_provider.models_unsupported"),
            eq("Provider models endpoint is unavailable or unsupported")
        );
    }

    private static final class CountingLlmClient implements LlmClient {

        private int listModelCalls = 0;
        private int chatCalls = 0;
        private List<OllamaModelInfo> availableModels = List.of(new OllamaModelInfo("qwen2.5:7b"));

        @Override
        public List<OllamaModelInfo> listModels() {
            listModelCalls++;
            return availableModels;
        }

        @Override
        public ChatResult chat(ChatRequest request) {
            chatCalls++;
            return new ChatResult("qwen2.5:7b", "ok", "2026-04-16T10:00:00Z", 1, 1, 2);
        }
    }

    private static final class StaticLlmClient implements LlmClient {

        private final boolean failChat;

        private StaticLlmClient() {
            this(false);
        }

        private StaticLlmClient(boolean failChat) {
            this.failChat = failChat;
        }

        @Override
        public List<OllamaModelInfo> listModels() {
            return List.of(new OllamaModelInfo("qwen2.5:7b"));
        }

        @Override
        public ChatResult chat(ChatRequest request) {
            if (failChat) {
                throw new ApplicationException(
                    ErrorType.PROVIDER_UNAVAILABLE,
                    "llm.provider_unavailable",
                    "Unable to reach the local LLM provider"
                );
            }
            return new ChatResult("qwen2.5:7b", "ok", "2026-04-16T10:00:00Z", 1, 1, 2);
        }
    }

    private static final class SlowCountingLlmClient implements LlmClient {

        private final AtomicInteger listModelCalls = new AtomicInteger();
        private final AtomicInteger chatCalls = new AtomicInteger();

        @Override
        public List<OllamaModelInfo> listModels() {
            listModelCalls.incrementAndGet();
            sleepBriefly();
            return List.of(new OllamaModelInfo("qwen2.5:7b"));
        }

        @Override
        public ChatResult chat(ChatRequest request) {
            chatCalls.incrementAndGet();
            sleepBriefly();
            return new ChatResult("qwen2.5:7b", "ok", "2026-04-16T10:00:00Z", 1, 1, 2);
        }

        private void sleepBriefly() {
            try {
                Thread.sleep(50);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static final class StaticEmbeddingClient implements EmbeddingClient {

        private final boolean fail;

        private StaticEmbeddingClient(boolean fail) {
            this.fail = fail;
        }

        @Override
        public float[] embed(String input) {
            if (fail) {
                throw new com.example.demo.error.ApplicationException(
                    ErrorType.PROVIDER_UNAVAILABLE,
                    "embedding.provider_unavailable",
                    "Embedding provider is unavailable"
                );
            }
            return new float[] { 1.0f, 2.0f, 3.0f };
        }

        @Override
        public List<float[]> embedAll(List<String> inputs) {
            throw new UnsupportedOperationException("embedAll() is not needed in this test");
        }
    }

    private ActiveLlmProvider activeProvider(UUID id, String model) {
        return new ActiveLlmProvider(
            id.toString(),
            "Provider",
            LlmProviderType.OPENAI_COMPATIBLE,
            "http://10.10.20.15:8000",
            null,
            "Authorization",
            "Bearer",
            "/v1/chat/completions",
            "/v1/models",
            "/v1/embeddings",
            model,
            null,
            0.2,
            600,
            null,
            LlmProviderStatus.UNKNOWN,
            false
        );
    }

    private ActiveLlmProvider fallbackEmbeddingProvider() {
        EmbeddingProperties embeddingProperties = new EmbeddingProperties();
        embeddingProperties.setModel("embed");
        LlmProperties llmProperties = new LlmProperties();
        llmProperties.setBaseUrl("http://127.0.0.1:11434");
        return ActiveLlmProvider.fromEmbeddingProperties(embeddingProperties, llmProperties);
    }

    private static final class RecordingTransport extends OllamaApiTransport {

        private final ObjectMapper objectMapper;
        private ProviderException getFailure;
        private String chatResponseJson;

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
            throw new AssertionError("Models endpoint should not be successful in this test");
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
            return readChat(responseType, parseFailedCode, parseFailedMessage);
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
            return readChat(responseType, parseFailedCode, parseFailedMessage);
        }

        private <T> T readChat(Class<T> responseType, String parseFailedCode, String parseFailedMessage) {
            try {
                return objectMapper.readValue(chatResponseJson, responseType);
            } catch (IOException exception) {
                throw new ProviderException(ErrorType.PROVIDER_BAD_RESPONSE, parseFailedCode, parseFailedMessage, exception);
            }
        }
    }
}
