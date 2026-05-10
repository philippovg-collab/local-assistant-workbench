package com.example.demo.service.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.example.demo.config.ContextProperties;
import com.example.demo.model.ChatMode;
import com.example.demo.model.ChatRunContextDetail;
import com.example.demo.model.ContextAssemblyDroppedItem;
import com.example.demo.model.ContextAssemblyHistoryItem;
import com.example.demo.model.ContextAssemblySnapshotDetail;
import com.example.demo.model.ContextTokenBudget;
import com.example.demo.service.audit.port.ChatRunTraceRepository;
import com.example.demo.service.context.port.ContextAssemblyTraceRepository;
import com.example.demo.service.conversation.StoredConversationRun;
import com.example.demo.service.conversation.port.ConversationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ContextAssemblyQueryServiceTest {

    private final ContextProperties contextProperties = contextProperties();
    private final ContextAssemblyTraceRepository traceRepository = Mockito.mock(ContextAssemblyTraceRepository.class);
    private final ConversationRepository conversationRepository = Mockito.mock(ConversationRepository.class);
    private final ChatRunTraceRepository chatRunTraceRepository = Mockito.mock(ChatRunTraceRepository.class);
    private final ContextAssemblyQueryService service = new ContextAssemblyQueryService(
        contextProperties,
        traceRepository,
        conversationRepository,
        chatRunTraceRepository
    );

    @Test
    void capsInspectorPreviewsAndGroupsHistoryWithoutRawMessages() {
        String conversationId = UUID.randomUUID().toString();
        String runId = UUID.randomUUID().toString();
        String priorRunId = UUID.randomUUID().toString();
        String longPrompt = "prompt " + "x".repeat(600);
        String longAnswer = "answer " + "y".repeat(600);
        when(traceRepository.findByRunId(runId)).thenReturn(Optional.of(new ContextAssemblySnapshotDetail(
            UUID.randomUUID().toString(),
            runId,
            conversationId,
            2,
            ChatMode.DIRECT,
            longPrompt,
            longPrompt,
            List.of(
                new ContextAssemblyHistoryItem(priorRunId, 1, "user", longPrompt, 80, Instant.parse("2026-05-10T00:00:00Z")),
                new ContextAssemblyHistoryItem(priorRunId, 1, "assistant", longAnswer, 90, Instant.parse("2026-05-10T00:00:01Z"))
            ),
            List.of(new ContextAssemblyDroppedItem(UUID.randomUUID().toString(), 0, "token_budget", 20)),
            List.of(),
            List.of(),
            new ContextTokenBudget(4, 1000, 1, 170, 1, 20),
            "hash",
            false,
            null,
            Map.of("fieldSources", Map.of("model", "sticky")),
            true,
            1,
            "READY",
            30,
            null,
            null,
            null,
            Instant.parse("2026-05-10T00:00:02Z")
        )));
        when(conversationRepository.listRuns(conversationId)).thenReturn(List.of(run(priorRunId, conversationId, 1)));

        ChatRunContextDetail detail = service.getByRunId(runId);

        assertEquals("AVAILABLE", detail.status());
        assertTrue(detail.promptPreview().length() <= 323);
        assertEquals(1, detail.selectedHistory().size());
        assertTrue(detail.selectedHistory().get(0).promptPreview().length() <= 323);
        assertTrue(detail.selectedHistory().get(0).answerPreview().length() <= 323);
        assertEquals(200, detail.tokenBudget().used());
        assertEquals(20, detail.tokenBudget().dropped());
        assertEquals("/api/chat-runs/" + detail.droppedItems().get(0).runId() + "/trace", detail.droppedItems().get(0).links().trace());
        assertNotNull(detail.stickyStateResolution().get("fieldSources"));
    }

    @Test
    void returnsExpiredPayloadWhenConversationRunExistsWithoutSnapshot() {
        String conversationId = UUID.randomUUID().toString();
        String runId = UUID.randomUUID().toString();
        when(traceRepository.findByRunId(runId)).thenReturn(Optional.empty());
        when(conversationRepository.findRunByRunId(runId)).thenReturn(Optional.of(run(runId, conversationId, 2, "EXPIRED")));

        ChatRunContextDetail detail = service.getByRunId(runId);

        assertEquals("EXPIRED", detail.status());
        assertEquals(runId, detail.runId());
        assertEquals("context_assembly.expired", detail.reasonCode());
    }

    @Test
    void returnsNotAvailablePayloadWhenRunNeverHadSnapshot() {
        String conversationId = UUID.randomUUID().toString();
        String runId = UUID.randomUUID().toString();
        when(traceRepository.findByRunId(runId)).thenReturn(Optional.empty());
        when(conversationRepository.findRunByRunId(runId)).thenReturn(Optional.of(run(runId, conversationId, 2, "NONE")));

        ChatRunContextDetail detail = service.getByRunId(runId);

        assertEquals("NOT_AVAILABLE", detail.status());
        assertEquals(runId, detail.runId());
        assertEquals("context_assembly.not_available", detail.reasonCode());
    }

    private static StoredConversationRun run(String runId, String conversationId, int turnNo) {
        return run(runId, conversationId, turnNo, "NONE");
    }

    private static StoredConversationRun run(String runId, String conversationId, int turnNo, String contextAssemblyStatus) {
        return new StoredConversationRun(
            conversationId,
            runId,
            turnNo,
            null,
            null,
            "hash",
            "prompt",
            null,
            contextAssemblyStatus,
            Instant.parse("2026-05-10T00:00:00Z"),
            "COMPLETED",
            Instant.parse("2026-05-10T00:00:01Z"),
            null,
            null,
            null
        );
    }

    private static ContextProperties contextProperties() {
        ContextProperties properties = new ContextProperties();
        properties.setEnabled(true);
        properties.setConversationsEnabled(true);
        properties.setHistoryEnabled(true);
        properties.setStickyStateEnabled(true);
        properties.setRetrievalQueryResolutionEnabled(true);
        properties.setSummaryEnabled(true);
        return properties;
    }
}
