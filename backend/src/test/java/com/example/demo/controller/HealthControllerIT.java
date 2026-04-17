package com.example.demo.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.infrastructure.material.OcrCapability;
import com.example.demo.infrastructure.material.OcrCapabilityProvider;
import com.example.demo.llm.LlmClient;
import com.example.demo.model.OllamaModelInfo;
import com.example.demo.support.IntegrationTestOverrides;
import com.example.demo.support.PostgresIntegrationTestSupport;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestOverrides.class)
class HealthControllerIT extends PostgresIntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestOcrCapabilityProvider ocrCapabilityProvider;

    @Autowired
    private TestLlmClient llmClient;

    @BeforeEach
    void resetCapability() {
        ocrCapabilityProvider.setCapability(OcrCapability.embeddedTextAndOcr(List.of("kaz", "rus", "eng"), 12));
        llmClient.setAvailable(true);
    }

    @Test
    void reportsLiveStorageReadinessWhenRuntimeIsHealthy() throws Exception {
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.runtimeCachedAt").isNotEmpty())
            .andExpect(jsonPath("$.directStatus").value("UP"))
            .andExpect(jsonPath("$.ragStatus").value("UP"))
            .andExpect(jsonPath("$.llmStatus").value("UP"))
            .andExpect(jsonPath("$.embeddingStatus").value("UP"))
            .andExpect(jsonPath("$.ocrStatus").value("UP"))
            .andExpect(jsonPath("$.databaseStatus").value("UP"))
            .andExpect(jsonPath("$.vectorStatus").value("UP"))
            .andExpect(jsonPath("$.ocrLanguages[0]").value("kaz"));
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        @Primary
        TestOcrCapabilityProvider testOcrCapabilityProvider() {
            return new TestOcrCapabilityProvider();
        }

        @Bean
        @Primary
        TestLlmClient testLlmClient() {
            return new TestLlmClient();
        }
    }

    static class TestOcrCapabilityProvider implements OcrCapabilityProvider {

        private volatile OcrCapability capability = OcrCapability.embeddedTextAndOcr(List.of("kaz", "rus", "eng"), 12);

        void setCapability(OcrCapability capability) {
            this.capability = capability;
        }

        @Override
        public OcrCapability currentCapability() {
            return capability;
        }
    }

    static class TestLlmClient implements LlmClient {

        private volatile boolean available = true;

        void setAvailable(boolean available) {
            this.available = available;
        }

        @Override
        public List<OllamaModelInfo> listModels() {
            if (!available) {
                throw new com.example.demo.api.ApiException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "llm.provider_unavailable",
                    "Unable to reach the local LLM provider"
                );
            }
            return List.of(new OllamaModelInfo("qwen2.5:7b"));
        }

        @Override
        public ChatResult chat(ChatRequest request) {
            return new ChatResult("qwen2.5:7b", "ok", "2026-04-16T10:00:00Z", 1, 1, 2);
        }
    }
}
