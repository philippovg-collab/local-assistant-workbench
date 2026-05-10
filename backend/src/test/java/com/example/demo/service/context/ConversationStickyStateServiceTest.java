package com.example.demo.service.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.config.ContextProperties;
import com.example.demo.model.AnswerMode;
import com.example.demo.model.ChatExecutionRequest;
import com.example.demo.model.ChatExecutionResponse;
import com.example.demo.model.ChatMode;
import com.example.demo.model.KnowledgeScope;
import com.example.demo.model.RetrievalFilters;
import com.example.demo.service.ChatRunTraceService;
import com.example.demo.service.conversation.StoredConversation;
import com.example.demo.service.conversation.StoredConversationRun;
import com.example.demo.service.conversation.port.ConversationRepository;
import com.example.demo.service.context.ConversationStickyStateService.StickyResolution;
import com.example.demo.service.context.port.ConversationStickyStateRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConversationStickyStateServiceTest {

    private final ContextProperties properties = enabledProperties();
    private final ConversationRepository conversationRepository = mock(ConversationRepository.class);
    private final ConversationStickyStateRepository stickyRepository = mock(ConversationStickyStateRepository.class);
    private final ChatRunTraceService traceService = mock(ChatRunTraceService.class);
    private final ConversationStickyStateService service = new ConversationStickyStateService(
        properties,
        conversationRepository,
        stickyRepository,
        traceService
    );

    @Test
    void mergeUsesExplicitStickyConversationThenAppDefaults() {
        String conversationId = UUID.randomUUID().toString();
        String runId = UUID.randomUUID().toString();
        StoredConversationRun run = run(conversationId, runId, 2);
        when(conversationRepository.findRunByRunId(runId)).thenReturn(Optional.of(run));
        when(conversationRepository.findConversation(conversationId)).thenReturn(Optional.of(conversation(conversationId)));
        when(stickyRepository.findByConversationId(conversationId)).thenReturn(Optional.of(sticky(conversationId, runId)));

        ChatExecutionRequest request = new ChatExecutionRequest(
            ChatMode.RAG,
            null,
            "Prompt",
            null,
            List.of(),
            AnswerMode.BROADER_REASONING,
            null,
            null,
            null,
            null,
            null,
            null,
            conversationId,
            null,
            "turn-2",
            null,
            null,
            null
        );

        StickyResolution resolution = service.resolve(request, ChatMode.RAG, new ChatRunTraceService.RunTraceContext(runId, Instant.now()));

        assertEquals("sticky-model", resolution.request().model());
        assertEquals(AnswerMode.BROADER_REASONING, resolution.request().answerMode());
        assertEquals("grid", resolution.request().knowledgeScope().workspaceKey());
        assertEquals("sticky-doc", resolution.request().retrievalFilters().documentNumber());
        assertEquals(List.of(), resolution.request().instructionIds());
        assertEquals("request", resolution.fieldSources().get("answerMode"));
        assertEquals("sticky", resolution.fieldSources().get("model"));
        assertEquals("sticky", resolution.fieldSources().get("knowledgeScope"));
        Map<String, Object> metadata = resolution.metadata();
        @SuppressWarnings("unchecked")
        Map<String, Object> resolvedState = (Map<String, Object>) metadata.get("resolvedState");
        assertEquals("sticky-model", resolvedState.get("model"));
        assertEquals("broader_reasoning", resolvedState.get("answerMode"));
        assertEquals(1, metadata.get("stickyVersion"));
    }

    @Test
    void disabledRequestLeavesRequestUnchangedAndSkipsStickyRead() {
        String conversationId = UUID.randomUUID().toString();
        String runId = UUID.randomUUID().toString();
        ChatExecutionRequest request = new ChatExecutionRequest(
            ChatMode.DIRECT,
            "explicit",
            "Prompt",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            conversationId,
            null,
            null,
            null,
            null,
            new com.example.demo.model.ContextOptions(null, null, false, null, null)
        );
        when(conversationRepository.findRunByRunId(runId)).thenReturn(Optional.of(run(conversationId, runId, 1)));

        StickyResolution resolution = service.resolve(request, ChatMode.DIRECT, new ChatRunTraceService.RunTraceContext(runId, Instant.now()));

        assertEquals("disabled_by_request", resolution.disabledReason());
        assertEquals(request, resolution.request());
        verify(stickyRepository, org.mockito.Mockito.never()).findByConversationId(any());
    }

    @Test
    void updatePersistsOnlyCompletedEffectiveStickyFields() {
        String conversationId = UUID.randomUUID().toString();
        String runId = UUID.randomUUID().toString();
        StickyResolution resolution = new StickyResolution(
            true,
            false,
            null,
            null,
            run(conversationId, runId, 4),
            null,
            java.util.Map.of("instructionWorkspaceKey", "derived_knowledge_scope"),
            true
        );
        ChatExecutionRequest normalizedRequest = new ChatExecutionRequest(
            ChatMode.RAG,
            null,
            "Prompt",
            null,
            List.of("i1"),
            null,
            new KnowledgeScope(List.of(), List.of(), List.of(), "grid", false),
            "grid",
            RetrievalFilters.empty(),
            null,
            List.of("s1"),
            null
        );
        ChatExecutionResponse response = new ChatExecutionResponse(
            ChatMode.RAG,
            "resolved-model",
            "Prompt",
            "Answer",
            "ready",
            Instant.now().toString(),
            null,
            null,
            null,
            AnswerMode.STRICT_SOURCES_ONLY,
            List.of(),
            List.of(),
            null,
            null,
            null,
            List.of(),
            runId
        );

        service.updateAfterCompleted(resolution, normalizedRequest, response, new ChatRunTraceService.RunTraceContext(runId, Instant.now()));

        verify(stickyRepository).upsertCompletedTurn(
            eq(conversationId),
            eq("resolved-model"),
            eq(AnswerMode.STRICT_SOURCES_ONLY),
            eq(null),
            eq(normalizedRequest.knowledgeScope()),
            eq(normalizedRequest.retrievalFilters()),
            eq(List.of("i1")),
            eq(List.of("s1")),
            eq(runId),
            eq(4),
            any(Instant.class)
        );
    }

    @Test
    void derivedInstructionWorkspaceKeyIsNotStored() {
        String conversationId = UUID.randomUUID().toString();
        String runId = UUID.randomUUID().toString();
        StoredConversationRun run = run(conversationId, runId, 1);
        when(conversationRepository.findRunByRunId(runId)).thenReturn(Optional.of(run));
        when(conversationRepository.findConversation(conversationId)).thenReturn(Optional.of(conversation(conversationId)));
        when(stickyRepository.findByConversationId(conversationId)).thenReturn(Optional.empty());

        ChatExecutionRequest request = new ChatExecutionRequest(
            ChatMode.RAG,
            null,
            "Prompt",
            null,
            null,
            null,
            new KnowledgeScope(List.of(), List.of(), List.of(), "grid", false),
            null,
            null,
            null,
            null,
            null,
            conversationId,
            null,
            null,
            null,
            null,
            null
        );

        StickyResolution resolution = service.resolve(request, ChatMode.RAG, new ChatRunTraceService.RunTraceContext(runId, Instant.now()));

        assertEquals("grid", resolution.request().instructionWorkspaceKey());
        assertEquals("derived_knowledge_scope", resolution.fieldSources().get("instructionWorkspaceKey"));
        assertEquals(true, resolution.derivedInstructionWorkspaceKey());
    }

    private static ContextProperties enabledProperties() {
        ContextProperties properties = new ContextProperties();
        properties.setEnabled(true);
        properties.setConversationsEnabled(true);
        properties.setStickyStateEnabled(true);
        return properties;
    }

    private static StoredConversation conversation(String conversationId) {
        Instant now = Instant.parse("2026-05-10T00:00:00Z");
        return new StoredConversation(
            conversationId,
            "grid",
            "Conversation",
            ChatMode.RAG,
            "ACTIVE",
            "conversation-model",
            AnswerMode.BRIEF,
            now,
            now,
            null,
            1
        );
    }

    private static StoredConversationRun run(String conversationId, String runId, int turnNo) {
        return new StoredConversationRun(
            conversationId,
            runId,
            turnNo,
            null,
            null,
            "hash",
            "Prompt",
            null,
            "NONE",
            Instant.parse("2026-05-10T00:00:00Z"),
            "RECEIVED",
            null,
            null,
            null,
            null
        );
    }

    private static StoredConversationStickyState sticky(String conversationId, String runId) {
        Instant now = Instant.parse("2026-05-10T00:00:00Z");
        return new StoredConversationStickyState(
            conversationId,
            "sticky-model",
            AnswerMode.STRICT_SOURCES_ONLY,
            "sticky-workspace",
            new KnowledgeScope(List.of(), List.of(), List.of(), "grid", false),
            new RetrievalFilters("sticky-doc", null, null, null, null, null, null, null, List.of(), null),
            List.of("sticky-instruction"),
            null,
            runId,
            1,
            1,
            now,
            now
        );
    }
}
