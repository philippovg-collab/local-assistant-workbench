package com.example.demo.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.api.ApiExceptionHandler;
import com.example.demo.config.MaterialProperties;
import com.example.demo.error.ApplicationException;
import com.example.demo.error.ErrorType;
import com.example.demo.model.ChatMode;
import com.example.demo.model.ConversationCreateRequest;
import com.example.demo.model.ConversationDetail;
import com.example.demo.model.ConversationPatchRequest;
import com.example.demo.model.ConversationRunDetail;
import com.example.demo.model.ConversationSummary;
import com.example.demo.service.ConversationService;
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

class ConversationControllerContractTest {

    private ConversationService conversationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        conversationService = mock(ConversationService.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new ConversationController(conversationService))
            .setControllerAdvice(new ApiExceptionHandler(new MaterialProperties()))
            .setMessageConverters(new MappingJackson2HttpMessageConverter())
            .build();
    }

    @Test
    void createsConversationFromJsonPayload() throws Exception {
        String conversationId = UUID.randomUUID().toString();
        when(conversationService.createConversation(any())).thenReturn(detail(conversationId, "Dispatch", "ACTIVE"));
        ArgumentCaptor<ConversationCreateRequest> requestCaptor = ArgumentCaptor.forClass(ConversationCreateRequest.class);

        mockMvc.perform(post("/api/conversations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "workspaceKey": "grid",
                      "title": "Dispatch",
                      "mode": "rag",
                      "defaultModel": "qwen2.5:7b",
                      "defaultAnswerMode": "brief"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(conversationId))
            .andExpect(jsonPath("$.title").value("Dispatch"))
            .andExpect(jsonPath("$.mode").value("rag"))
            .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(conversationService).createConversation(requestCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("grid", requestCaptor.getValue().workspaceKey());
        org.junit.jupiter.api.Assertions.assertEquals(ChatMode.RAG, requestCaptor.getValue().mode());
    }

    @Test
    void listsGetsPatchesAndListsRuns() throws Exception {
        String conversationId = UUID.randomUUID().toString();
        String runId = UUID.randomUUID().toString();
        when(conversationService.listConversations("grid", ChatMode.RAG))
            .thenReturn(List.of(summary(conversationId, "Dispatch")));
        when(conversationService.getConversation(conversationId))
            .thenReturn(detail(conversationId, "Dispatch", "ACTIVE"));
        when(conversationService.patchConversation(eq(conversationId), any()))
            .thenReturn(detail(conversationId, "Archived", "ARCHIVED"));
        when(conversationService.listRuns(conversationId))
            .thenReturn(List.of(run(conversationId, runId)));

        mockMvc.perform(get("/api/conversations")
                .param("workspaceKey", "grid")
                .param("mode", "rag"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(conversationId))
            .andExpect(jsonPath("$[0].turnCount").value(2));

        mockMvc.perform(get("/api/conversations/{id}", conversationId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(conversationId))
            .andExpect(jsonPath("$.title").value("Dispatch"));

        mockMvc.perform(patch("/api/conversations/{id}", conversationId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "Archived",
                      "status": "ARCHIVED"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Archived"))
            .andExpect(jsonPath("$.status").value("ARCHIVED"));

        mockMvc.perform(get("/api/conversations/{id}/runs", conversationId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].runId").value(runId))
            .andExpect(jsonPath("$[0].statusUrl").value("/api/chat-runs/" + runId + "/status"))
            .andExpect(jsonPath("$[0].resultUrl").value("/api/chat-runs/" + runId + "/result"));

        ArgumentCaptor<ConversationPatchRequest> patchCaptor = ArgumentCaptor.forClass(ConversationPatchRequest.class);
        verify(conversationService).patchConversation(eq(conversationId), patchCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("ARCHIVED", patchCaptor.getValue().status());
    }

    @Test
    void disabledConversationSurfaceReturnsContextDisabled() throws Exception {
        when(conversationService.listConversations(null, null)).thenThrow(contextDisabled());

        mockMvc.perform(get("/api/conversations"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("context.disabled"));
    }

    private static ApplicationException contextDisabled() {
        return new ApplicationException(
            ErrorType.INVALID_REQUEST,
            "context.disabled",
            "Conversation context is disabled"
        );
    }

    private static ConversationDetail detail(String id, String title, String status) {
        Instant now = Instant.parse("2026-05-10T00:00:00Z");
        return new ConversationDetail(
            id,
            "grid",
            title,
            ChatMode.RAG,
            status,
            "qwen2.5:7b",
            null,
            now,
            now,
            now,
            2,
            null
        );
    }

    private static ConversationSummary summary(String id, String title) {
        Instant now = Instant.parse("2026-05-10T00:00:00Z");
        return new ConversationSummary(
            id,
            "grid",
            title,
            ChatMode.RAG,
            "ACTIVE",
            "qwen2.5:7b",
            null,
            now,
            now,
            now,
            2
        );
    }

    private static ConversationRunDetail run(String conversationId, String runId) {
        Instant now = Instant.parse("2026-05-10T00:00:00Z");
        return new ConversationRunDetail(
            conversationId,
            runId,
            1,
            null,
            "turn-1",
            "Prompt",
            null,
            "NONE",
            now,
            "COMPLETED",
            now.plusSeconds(1),
            null,
            null,
            null,
            "/api/chat-runs/" + runId + "/status",
            "/api/chat-runs/" + runId + "/trace",
            "/api/chat-runs/" + runId + "/result",
            "/api/chat-runs/" + runId + "/cancel"
        );
    }
}
