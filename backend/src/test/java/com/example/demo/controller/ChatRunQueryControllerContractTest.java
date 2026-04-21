package com.example.demo.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.api.ApiExceptionHandler;
import com.example.demo.config.MaterialProperties;
import com.example.demo.model.AnswerMode;
import com.example.demo.model.ChatAuditRunDetail;
import com.example.demo.model.ChatAuditRunSummary;
import com.example.demo.model.ChatExecutionResponse;
import com.example.demo.model.ChatMode;
import com.example.demo.model.ChatRunStatusResponse;
import com.example.demo.model.ChatRunTraceDetail;
import com.example.demo.model.KnowledgeScopeResolved;
import com.example.demo.model.RetrievalTrace;
import com.example.demo.service.ChatRunQueryService;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ChatRunQueryControllerContractTest {

    private MockMvc mockMvc;
    private ChatRunQueryService chatRunQueryService;

    @BeforeEach
    void setUp() {
        chatRunQueryService = mock(ChatRunQueryService.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new ChatRunQueryController(chatRunQueryService))
            .setControllerAdvice(new ApiExceptionHandler(new MaterialProperties()))
            .setMessageConverters(new MappingJackson2HttpMessageConverter(JsonMapper.builder()
                .findAndAddModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build()))
            .build();
    }

    @Test
    void listsChatRunsThroughQueryService() throws Exception {
        String runId = UUID.randomUUID().toString();
        when(chatRunQueryService.listRuns(null)).thenReturn(List.of(new ChatAuditRunSummary(
            runId,
            ChatMode.DIRECT,
            "qwen2.5:7b",
            AnswerMode.BRIEF,
            "Prompt",
            "Answer",
            Instant.parse("2026-04-19T00:00:00Z")
        )));

        mockMvc.perform(get("/api/chat-runs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(runId))
            .andExpect(jsonPath("$[0].mode").value("direct"))
            .andExpect(jsonPath("$[0].promptPreview").value("Prompt"));

        verify(chatRunQueryService).listRuns(null);
    }

    @Test
    void returnsChatRunDetailThroughQueryService() throws Exception {
        String runId = UUID.randomUUID().toString();
        when(chatRunQueryService.getRun(runId)).thenReturn(detail(runId));

        mockMvc.perform(get("/api/chat-runs/{id}", runId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(runId))
            .andExpect(jsonPath("$.mode").value("direct"))
            .andExpect(jsonPath("$.answer").value("Answer"));

        verify(chatRunQueryService).getRun(runId);
    }

    @Test
    void returnsTraceThroughQueryService() throws Exception {
        String runId = UUID.randomUUID().toString();
        when(chatRunQueryService.getTrace(runId)).thenReturn(trace(runId));

        mockMvc.perform(get("/api/chat-runs/{id}/trace", runId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(runId))
            .andExpect(jsonPath("$.mode").value("direct"))
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.llmCalls").isArray())
            .andExpect(jsonPath("$.events").isArray());

        verify(chatRunQueryService).getTrace(runId);
    }

    @Test
    void returnsHeaderOnlyStatusThroughQueryService() throws Exception {
        String runId = UUID.randomUUID().toString();
        when(chatRunQueryService.getStatus(runId)).thenReturn(new ChatRunStatusResponse(
            runId,
            "COMPLETED",
            Instant.parse("2026-04-19T00:00:00Z"),
            Instant.parse("2026-04-19T00:00:01Z"),
            null,
            1000L,
            null,
            null,
            null
        ));

        mockMvc.perform(get("/api/chat-runs/{id}/status", runId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(runId))
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.completedAt").value("2026-04-19T00:00:01Z"))
            .andExpect(jsonPath("$.latencyMsTotal").value(1000));

        verify(chatRunQueryService).getStatus(runId);
    }

    @Test
    void returnsResultThroughQueryService() throws Exception {
        String runId = UUID.randomUUID().toString();
        when(chatRunQueryService.getResult(runId)).thenReturn(response(runId));

        mockMvc.perform(get("/api/chat-runs/{id}/result", runId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.auditRunId").value(runId))
            .andExpect(jsonPath("$.answer").value("Answer"));

        verify(chatRunQueryService).getResult(runId);
    }

    private ChatAuditRunDetail detail(String runId) {
        return new ChatAuditRunDetail(
            runId,
            ChatMode.DIRECT,
            "qwen2.5:7b",
            "Prompt",
            "Answer",
            "ready",
            AnswerMode.BRIEF,
            Instant.parse("2026-04-19T00:00:00Z"),
            List.of(),
            KnowledgeScopeResolved.empty(),
            new RetrievalTrace(0, 0, 0, 0, 0, 0, 0, 0, 0),
            List.of()
        );
    }

    private ChatRunTraceDetail trace(String runId) {
        return new ChatRunTraceDetail(
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
        );
    }

    private ChatExecutionResponse response(String runId) {
        return new ChatExecutionResponse(
            ChatMode.DIRECT,
            "qwen2.5:7b",
            "Prompt",
            "Answer",
            "ready",
            "2026-04-19T00:00:00Z",
            1,
            2,
            3,
            AnswerMode.BRIEF,
            List.of(),
            List.of(),
            KnowledgeScopeResolved.empty(),
            new RetrievalTrace(0, 0, 0, 0, 0, 0, 0, 0, 0),
            null,
            List.of(),
            runId
        );
    }
}
