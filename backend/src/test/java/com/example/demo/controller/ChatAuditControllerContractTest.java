package com.example.demo.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.api.ApiExceptionHandler;
import com.example.demo.config.MaterialProperties;
import com.example.demo.model.ChatExecutionRequest;
import com.example.demo.model.ChatMode;
import com.example.demo.model.ChatRunSubmissionResponse;
import com.example.demo.model.ChatRunTraceDetail;
import com.example.demo.service.ChatAuditService;
import com.example.demo.service.ChatRunExecutionService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ChatAuditControllerContractTest {

    private MockMvc mockMvc;
    private ChatAuditService chatAuditService;
    private ChatRunExecutionService chatRunExecutionService;

    @BeforeEach
    void setUp() {
        chatAuditService = mock(ChatAuditService.class);
        chatRunExecutionService = mock(ChatRunExecutionService.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new ChatAuditController(chatAuditService, chatRunExecutionService))
            .setControllerAdvice(new ApiExceptionHandler(new MaterialProperties()))
            .setMessageConverters(new MappingJackson2HttpMessageConverter())
            .build();
    }

    @Test
    void submitsAsyncChatRunAndPreservesDismissedRetrievalHintKeys() throws Exception {
        String runId = UUID.randomUUID().toString();
        Instant createdAt = Instant.parse("2026-04-19T00:00:00Z");
        when(chatRunExecutionService.submit(any())).thenReturn(new ChatRunSubmissionResponse(
            runId,
            "RECEIVED",
            createdAt,
            "/api/chat-runs/" + runId + "/trace",
            "/api/chat-runs/" + runId + "/result"
        ));

        mockMvc.perform(post("/api/chat-runs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "mode": "rag",
                      "model": "qwen2.5:7b",
                      "prompt": "Что по договору?",
                      "instructionIds": [],
                      "dismissedRetrievalHintKeys": ["documentNumber", "project"]
                    }
                    """))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.id").value(runId))
            .andExpect(jsonPath("$.status").value("RECEIVED"))
            .andExpect(jsonPath("$.traceUrl").value("/api/chat-runs/" + runId + "/trace"))
            .andExpect(jsonPath("$.resultUrl").value("/api/chat-runs/" + runId + "/result"));

        ArgumentCaptor<ChatExecutionRequest> requestCaptor = ArgumentCaptor.forClass(ChatExecutionRequest.class);
        verify(chatRunExecutionService).submit(requestCaptor.capture());
        assertEquals(List.of("documentNumber", "project"), requestCaptor.getValue().dismissedRetrievalHintKeys());
    }

    @Test
    void returnsP0TraceSectionsForChatRun() throws Exception {
        String runId = UUID.randomUUID().toString();
        when(chatAuditService.getTrace(runId)).thenReturn(new ChatRunTraceDetail(
            runId,
            ChatMode.DIRECT,
            "COMPLETED",
            "qwen2.5:7b",
            "qwen2.5:7b",
            null,
            null,
            null,
            Instant.parse("2026-04-19T00:00:00Z"),
            Instant.parse("2026-04-19T00:00:01Z"),
            null,
            1000L,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(),
            null,
            List.of()
        ));

        mockMvc.perform(get("/api/chat-runs/{id}/trace", runId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(runId))
            .andExpect(jsonPath("$.mode").value("direct"))
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.llmCalls").isArray())
            .andExpect(jsonPath("$.events").isArray());

        verify(chatAuditService).getTrace(runId);
    }
}
