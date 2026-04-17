package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

        assertEquals(1, llmClient.chatCalls);
        assertEquals(first.cachedAt(), second.cachedAt());
        assertEquals("UP", second.directStatus());
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
        assertEquals("DOWN", readiness.ragStatus());
        assertEquals("DOWN", readiness.embeddingStatus());
        assertEquals("embedding.provider_unavailable", readiness.embeddingReasonCode());
    }

    private static final class CountingLlmClient implements LlmClient {

        private int chatCalls = 0;

        @Override
        public List<OllamaModelInfo> listModels() {
            return List.of(new OllamaModelInfo("qwen2.5:7b"));
        }

        @Override
        public ChatResult chat(ChatRequest request) {
            chatCalls++;
            return new ChatResult("qwen2.5:7b", "ok", "2026-04-16T10:00:00Z", 1, 1, 2);
        }
    }

    private static final class StaticLlmClient implements LlmClient {

        @Override
        public List<OllamaModelInfo> listModels() {
            return List.of(new OllamaModelInfo("qwen2.5:7b"));
        }

        @Override
        public ChatResult chat(ChatRequest request) {
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
