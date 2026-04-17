package com.example.demo.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.llm.LlmClient;
import com.example.demo.model.OllamaModelInfo;
import com.example.demo.service.MaterialService;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestOverrides.class)
class ChatControllerIT extends PostgresIntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MaterialService materialService;

    @Autowired
    private TestLlmClient llmClient;

    @BeforeEach
    void resetLlmClient() {
        llmClient.reset();
    }

    @Test
    void executesRagChatRequests() throws Exception {
        materialService.saveText(
            "Pricing FAQ",
            "Тариф Премиум стоит 12000 тенге в месяц и включает приоритетную поддержку."
        );
        llmClient.setNextResult(new LlmClient.ChatResult(
            "qwen2.5:7b",
            "Тариф Премиум стоит 12000 тенге в месяц.",
            "2026-04-16T10:00:00Z",
            12,
            8,
            20
        ));

        mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "mode": "rag",
                      "model": "qwen2.5:7b",
                      "prompt": "Сколько стоит тариф Премиум?",
                      "instructionIds": []
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mode").value("rag"))
            .andExpect(jsonPath("$.answer").value(containsString("12000")))
            .andExpect(jsonPath("$.sources[0].title").value("Pricing FAQ"));
    }

    @Test
    void executesSemanticRagChatRequestsForParaphrasedQuestions() throws Exception {
        materialService.saveText(
            "Pricing FAQ",
            "Тариф Премиум стоит 12000 тенге в месяц и включает приоритетную поддержку."
        );
        llmClient.setNextResult(new LlmClient.ChatResult(
            "qwen2.5:7b",
            "Премиальный план стоит 12000 тенге в месяц.",
            "2026-04-16T10:00:00Z",
            14,
            10,
            24
        ));

        mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "mode": "rag",
                      "model": "qwen2.5:7b",
                      "prompt": "Сколько стоит премиальный план?",
                      "instructionIds": []
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mode").value("rag"))
            .andExpect(jsonPath("$.answer").value(containsString("12000")))
            .andExpect(jsonPath("$.sources[0].title").value("Pricing FAQ"))
            .andExpect(jsonPath("$.sources[0].score").isNumber());
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        @Primary
        TestLlmClient llmClient() {
            return new TestLlmClient();
        }
    }

    static final class TestLlmClient implements LlmClient {

        private ChatResult nextResult = new ChatResult(
            "qwen2.5:7b",
            "ok",
            "2026-04-16T10:00:00Z",
            1,
            1,
            2
        );
        private int chatCalls = 0;

        void reset() {
            chatCalls = 0;
            nextResult = new ChatResult(
                "qwen2.5:7b",
                "ok",
                "2026-04-16T10:00:00Z",
                1,
                1,
                2
            );
        }

        void setNextResult(ChatResult nextResult) {
            this.nextResult = nextResult;
        }

        int chatCalls() {
            return chatCalls;
        }

        @Override
        public List<OllamaModelInfo> listModels() {
            return List.of(new OllamaModelInfo("qwen2.5:7b"));
        }

        @Override
        public ChatResult chat(ChatRequest request) {
            chatCalls++;
            return nextResult;
        }
    }
}
