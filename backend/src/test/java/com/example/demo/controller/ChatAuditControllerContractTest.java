package com.example.demo.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.api.ApiExceptionHandler;
import com.example.demo.config.MaterialProperties;
import com.example.demo.model.ChatMode;
import com.example.demo.model.ChatRunTraceDetail;
import com.example.demo.service.ChatAuditService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ChatAuditControllerContractTest {

    private MockMvc mockMvc;
    private ChatAuditService chatAuditService;

    @BeforeEach
    void setUp() {
        chatAuditService = mock(ChatAuditService.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new ChatAuditController(chatAuditService))
            .setControllerAdvice(new ApiExceptionHandler(new MaterialProperties()))
            .setMessageConverters(new MappingJackson2HttpMessageConverter())
            .build();
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
