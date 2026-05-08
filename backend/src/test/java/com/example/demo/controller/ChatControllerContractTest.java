package com.example.demo.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.api.ApiException;
import com.example.demo.api.ApiExceptionHandler;
import com.example.demo.config.ChatExecutionProperties;
import com.example.demo.config.MaterialProperties;
import com.example.demo.llm.LlmClient;
import com.example.demo.model.ChatExecutionResponse;
import com.example.demo.model.ChatMode;
import com.example.demo.model.KnowledgeScopeResolved;
import com.example.demo.model.RetrievalTrace;
import com.example.demo.model.RetrievalDebug;
import com.example.demo.model.RetrievalFilters;
import com.example.demo.model.RetrievalQueryHints;
import com.example.demo.service.ChatRunExecutionService;
import com.example.demo.service.ModelCatalogService;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;

class ChatControllerContractTest {

    private MockMvc mockMvc;
    private ChatRunExecutionService chatRunExecutionService;
    private LlmClient llmClient;

    @BeforeEach
    void setUp() {
        chatRunExecutionService = mock(ChatRunExecutionService.class);
        llmClient = mock(LlmClient.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new ChatController(
                chatRunExecutionService,
                new ModelCatalogService(llmClient),
                new ChatExecutionProperties()
            ))
            .setControllerAdvice(new ApiExceptionHandler(new MaterialProperties()))
            .setMessageConverters(new MappingJackson2HttpMessageConverter())
            .build();
    }

    @Test
    void rejectsMalformedChatPayloadsWithoutCallingTheService() throws Exception {
        mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("request.invalid_payload"));

        verifyNoInteractions(chatRunExecutionService);
    }

    @Test
    void rejectsInvalidModeValuesWithoutCallingTheService() throws Exception {
        mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "mode": "broken",
                      "model": "qwen2.5:7b",
                      "prompt": "Сколько стоит тариф Премиум?",
                      "instructionIds": []
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("request.invalid_payload"));

        verifyNoInteractions(chatRunExecutionService);
    }

    @Test
    void acceptsRetrievalFiltersInChatPayload() throws Exception {
        org.mockito.Mockito.when(chatRunExecutionService.submitAndWait(any(), any(Duration.class))).thenReturn(new ChatExecutionResponse(
            ChatMode.RAG,
            "qwen2.5:7b",
            "Какая цена?",
            "12000",
            "ready",
            "2026-04-19T00:00:00Z",
            1,
            1,
            2,
            null,
            List.of(),
            List.of(),
            KnowledgeScopeResolved.empty(),
            new RetrievalTrace(1, 1, 1, 1, 1, 1, 2, 2, 1, "sufficient"),
            new RetrievalDebug(
                new RetrievalQueryHints("KZ-2026-0415-ENERGY", null, null, null, "ru", "North Upgrade", null, null, null),
                new RetrievalFilters(
                    "KZ-2026-0415-ENERGY",
                    null,
                    null,
                    "Grid operations",
                    "North Upgrade",
                    null,
                    null,
                    "ru",
                    List.of(),
                    null
                ),
                new RetrievalFilters(
                    "KZ-2026-0415-ENERGY",
                    null,
                    null,
                    "Grid operations",
                    "North Upgrade",
                    null,
                    null,
                    "ru",
                    List.of(),
                    null
                ),
                2,
                2,
                2,
                1,
                "sufficient",
                "hybrid-rerank-v1"
            ),
            List.of(),
            null
        ));

        mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "mode": "rag",
                      "model": "qwen2.5:7b",
                      "prompt": "Какая цена?",
                      "instructionIds": [],
                      "retrievalFilters": {
                        "documentNumber": "KZ-2026-0415-ENERGY",
                        "documentDateFrom": "2026-04-01",
                        "documentDateTo": "2026-04-30",
                        "department": "Grid operations",
                        "project": "North Upgrade",
                        "counterparty": "GridBuild LLP",
                        "businessStatus": "APPROVED",
                        "language": "ru",
                        "tags": ["dispatch"],
                        "sourceTrustMin": "MEDIUM"
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(header().string("Deprecation", "true"))
            .andExpect(header().string("Link", "</api/chat-runs>; rel=\"successor-version\""))
            .andExpect(jsonPath("$.mode").value("rag"))
            .andExpect(jsonPath("$.answer").value("12000"))
            .andExpect(jsonPath("$.retrievalDebug.effectiveFilters.project").value("North Upgrade"))
            .andExpect(jsonPath("$.retrievalDebug.queryHints.documentNumber").value("KZ-2026-0415-ENERGY"));

        verify(chatRunExecutionService).submitAndWait(any(), any(Duration.class));
    }

    @Test
    void returnsCompatibilityTimeoutWithDurableRunLinks() throws Exception {
        org.mockito.Mockito.when(chatRunExecutionService.submitAndWait(any(), any(Duration.class))).thenThrow(
            new ApiException(
                HttpStatus.REQUEST_TIMEOUT,
                "chat.run_still_processing",
                "Chat run 'run-1' is still processing. Poll /api/chat-runs/run-1/status or fetch /api/chat-runs/run-1/result when it completes."
            )
        );

        mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "mode": "direct",
                      "model": "qwen2.5:7b",
                      "prompt": "ping",
                      "instructionIds": []
                    }
                    """))
            .andExpect(status().isRequestTimeout())
            .andExpect(header().string("Deprecation", "true"))
            .andExpect(header().string("Link", "</api/chat-runs>; rel=\"successor-version\""))
            .andExpect(jsonPath("$.code").value("chat.run_still_processing"))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("/api/chat-runs/run-1/status")));
    }

    @Test
    void rejectsMalformedRetrievalFiltersWithoutCallingTheService() throws Exception {
        mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "mode": "rag",
                      "model": "qwen2.5:7b",
                      "prompt": "Какая цена?",
                      "instructionIds": [],
                      "retrievalFilters": {
                        "sourceTrustMin": "BROKEN"
                      }
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("request.invalid_payload"));

        verifyNoInteractions(chatRunExecutionService);
    }

    @Test
    void rejectsInvalidRetrievalFilterDateRangeWithoutCallingTheService() throws Exception {
        mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "mode": "rag",
                      "model": "qwen2.5:7b",
                      "prompt": "Какая цена?",
                      "instructionIds": [],
                      "retrievalFilters": {
                        "documentDateFrom": "2026-05-01",
                        "documentDateTo": "2026-04-01"
                      }
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("request.invalid_payload"));

        verifyNoInteractions(chatRunExecutionService);
    }
}
