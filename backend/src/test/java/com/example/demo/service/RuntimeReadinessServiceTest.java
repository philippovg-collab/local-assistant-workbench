package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.example.demo.api.ApiException;
import com.example.demo.config.HealthProperties;
import com.example.demo.config.LlmProperties;
import com.example.demo.embedding.EmbeddingClient;
import com.example.demo.llm.LlmClient;
import com.example.demo.model.OllamaModelInfo;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuntimeReadinessServiceTest {

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
                throw new ApiException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "llm.provider_unavailable",
                    "Unable to reach the local LLM provider"
                );
            }
            return new ChatResult("qwen2.5:7b", "ok", "2026-04-16T10:00:00Z", 1, 1, 2);
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
                throw new com.example.demo.api.ApiException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
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
}
