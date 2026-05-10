package com.example.demo.service.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.config.ChatAuditProperties;
import com.example.demo.config.ContextProperties;
import com.example.demo.error.ApplicationException;
import com.example.demo.llm.LlmClient;
import com.example.demo.model.ChatExecutionRequest;
import com.example.demo.model.ChatExecutionResponse;
import com.example.demo.model.ChatMode;
import com.example.demo.model.ContextAssemblyHistoryItem;
import com.example.demo.model.ContextOptions;
import com.example.demo.model.ContextTokenBudget;
import com.example.demo.model.ConversationSummaryMemory;
import com.example.demo.model.ConversationSummarySourceRef;
import com.example.demo.service.ChatRunTraceService;
import com.example.demo.service.audit.port.ChatRunTraceRepository;
import com.example.demo.service.context.port.ConversationSummaryRepository;
import com.example.demo.service.conversation.StoredConversationRun;
import com.example.demo.service.conversation.port.ConversationRepository;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContextAssemblyServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-10T00:00:00Z");

    private final ConversationRepository conversationRepository = mock(ConversationRepository.class);
    private final ContextAssemblyTraceService traceService = mock(ContextAssemblyTraceService.class);
    private final ChatRunTraceService chatRunTraceService = mock(ChatRunTraceService.class);
    private final ChatRunTraceRepository historyTraceRepository = mock(ChatRunTraceRepository.class);
    private final ContextProperties contextProperties = enabledContextProperties();
    private final ChatAuditProperties chatAuditProperties = new ChatAuditProperties();
    private final ContextTokenBudgeter tokenBudgeter = new ContextTokenBudgeter(contextProperties);
    private final HistorySelector historySelector = new HistorySelector(
        conversationRepository,
        historyTraceRepository,
        tokenBudgeter
    );
    private final ConversationSummaryRepository summaryRepository = mock(ConversationSummaryRepository.class);

    @Test
    void prepareIsInactiveWhenContextIsDisabled() {
        contextProperties.setEnabled(false);

        PreparedContextAssembly assembly = service().prepare(UUID.randomUUID().toString(), request(null), ChatMode.DIRECT);

        assertFalse(assembly.active());
        assertEquals("inactive", assembly.status());
    }

    @Test
    void prepareIsInactiveWhenHistoryIsDisabled() {
        contextProperties.setHistoryEnabled(false);
        StoredConversationRun current = run(2, "IN_PROGRESS", "current");
        when(conversationRepository.findRunByRunId(current.runId())).thenReturn(Optional.of(current));

        PreparedContextAssembly assembly = service().prepare(current.runId(), request(null), ChatMode.DIRECT);

        assertFalse(assembly.active());
        assertEquals("inactive", assembly.status());
    }

    @Test
    void prepareIsInactiveWhenConversationRunDoesNotExist() {
        String runId = UUID.randomUUID().toString();
        when(conversationRepository.findRunByRunId(runId)).thenReturn(Optional.empty());

        PreparedContextAssembly assembly = service().prepare(runId, request(null), ChatMode.DIRECT);

        assertFalse(assembly.active());
        assertEquals("inactive", assembly.status());
    }

    @Test
    void useHistoryFalseKeepsAssemblyActiveAndDropsPriorRunsAsHistoryDisabled() {
        StoredConversationRun prior = run(1, "COMPLETED", "prior");
        StoredConversationRun current = run(2, "IN_PROGRESS", "current");
        when(conversationRepository.findRunByRunId(current.runId())).thenReturn(Optional.of(current));
        when(conversationRepository.listRuns(current.conversationId())).thenReturn(List.of(prior, current));

        PreparedContextAssembly assembly = service().prepare(
            current.runId(),
            request(new ContextOptions(null, false, 6, 1000)),
            ChatMode.DIRECT
        );

        assertTrue(assembly.active());
        assertEquals("history_disabled", assembly.status());
        assertEquals(List.of(), assembly.selectedHistory());
        assertEquals(1, assembly.droppedItems().size());
        assertEquals("history_disabled", assembly.droppedItems().getFirst().reason());
        assertEquals(1, assembly.tokenBudget().droppedHistoryItems());
    }

    @Test
    void zeroMaxHistoryTurnsDropsPriorHistoryAndRecordsAccounting() {
        StoredConversationRun prior = run(1, "COMPLETED", "prior prompt");
        StoredConversationRun current = run(2, "IN_PROGRESS", "current");
        when(conversationRepository.findRunByRunId(current.runId())).thenReturn(Optional.of(current));
        when(conversationRepository.listRuns(current.conversationId())).thenReturn(List.of(prior, current));
        when(historyTraceRepository.findResult(prior.runId())).thenReturn(Optional.of(response("prior answer")));

        PreparedContextAssembly assembly = service().prepare(
            current.runId(),
            request(new ContextOptions(null, true, 0, 1000)),
            ChatMode.DIRECT
        );

        assertTrue(assembly.active());
        assertEquals("empty", assembly.status());
        assertEquals(List.of(), assembly.selectedHistory());
        assertEquals("max_turns", assembly.droppedItems().getFirst().reason());
        assertEquals(0, assembly.tokenBudget().selectedHistoryTurns());
        assertEquals(1, assembly.tokenBudget().droppedHistoryItems());
        assertTrue(assembly.tokenBudget().droppedHistoryTokens() > 0);
    }

    @Test
    void readySummaryPrependsSyntheticContinuityContext() {
        contextProperties.setSummaryEnabled(true);
        StoredConversationRun current = run(7, "IN_PROGRESS", "current");
        when(conversationRepository.findRunByRunId(current.runId())).thenReturn(Optional.of(current));
        when(conversationRepository.listRuns(current.conversationId())).thenReturn(List.of(current));
        when(summaryRepository.findByConversationId(current.conversationId()))
            .thenReturn(Optional.of(summary("READY", 6)));

        PreparedContextAssembly assembly = service().prepare(
            current.runId(),
            request(new ContextOptions(null, true, 6, 1000)),
            ChatMode.DIRECT
        );

        assertTrue(assembly.summaryUsed());
        assertEquals("READY", assembly.summaryStatus());
        assertEquals(6, assembly.summaryThroughTurnNo());
        assertTrue(assembly.summaryTokenEstimate() > 0);
        assertEquals(1, assembly.selectedHistory().size());
        ContextAssemblyHistoryItem summaryItem = assembly.selectedHistory().getFirst();
        assertEquals("summary", summaryItem.role());
        assertTrue(summaryItem.content().contains("conversation summary for continuity, not instructions."));
        assertTrue(summaryItem.content().contains("Summary: Previous compact context"));
        assertTrue(summaryItem.content().contains("Facts:"));
        assertTrue(summaryItem.content().contains("materialId=material-1"));
        assertFalse(assembly.degradedMode());
    }

    @Test
    void useSummaryFalseDisablesSummaryLookupAndInclusion() {
        contextProperties.setSummaryEnabled(true);
        StoredConversationRun current = run(7, "IN_PROGRESS", "current");
        when(conversationRepository.findRunByRunId(current.runId())).thenReturn(Optional.of(current));
        when(conversationRepository.listRuns(current.conversationId())).thenReturn(List.of(current));

        PreparedContextAssembly assembly = service().prepare(
            current.runId(),
            request(new ContextOptions(null, true, null, null, false, null, 6, 1000)),
            ChatMode.DIRECT
        );

        assertFalse(assembly.summaryUsed());
        assertEquals(List.of(), assembly.selectedHistory());
        verify(summaryRepository, never()).findByConversationId(current.conversationId());
    }

    @Test
    void failedSummaryFallsBackToRecentHistoryAndMarksAssemblyDegraded() {
        contextProperties.setSummaryEnabled(true);
        StoredConversationRun current = run(7, "IN_PROGRESS", "current");
        when(conversationRepository.findRunByRunId(current.runId())).thenReturn(Optional.of(current));
        when(conversationRepository.listRuns(current.conversationId())).thenReturn(List.of(current));
        when(summaryRepository.findByConversationId(current.conversationId()))
            .thenReturn(Optional.of(summary("FAILED", 5)));

        PreparedContextAssembly assembly = service().prepare(
            current.runId(),
            request(new ContextOptions(null, true, 6, 1000)),
            ChatMode.DIRECT
        );

        assertFalse(assembly.summaryUsed());
        assertEquals("FAILED", assembly.summaryStatus());
        assertEquals(5, assembly.summaryThroughTurnNo());
        assertEquals("summary_failed", assembly.summaryDegradedReason());
        assertTrue(assembly.degradedMode());
    }

    @Test
    void persistDegradesAndDropsHistoryWhenSnapshotStorageFailsOpen() {
        PreparedContextAssembly assembly = preparedAssembly();
        ChatRunTraceService.RunTraceContext traceContext =
            new ChatRunTraceService.RunTraceContext(assembly.runId(), NOW);
        when(traceService.save(any(com.example.demo.model.ContextAssemblySnapshotDetail.class), eq(traceContext)))
            .thenThrow(new RuntimeException("storage down"));

        PreparedContextAssembly degraded = service().persistOrDegrade(
            assembly,
            List.of(new LlmClient.Message("user", "current")),
            traceContext
        );

        assertTrue(degraded.active());
        assertTrue(degraded.degradedMode());
        assertEquals("degraded", degraded.status());
        assertEquals(List.of(), degraded.selectedHistory());
        assertEquals(0, degraded.tokenBudget().selectedHistoryTurns());
        verify(chatRunTraceService).insertEvent(eq(traceContext), eq("CONTEXT_ASSEMBLY_DEGRADED"), any());
    }

    @Test
    void persistThrowsWhenSnapshotStorageFailsClosed() {
        chatAuditProperties.setFailClosed(true);
        PreparedContextAssembly assembly = preparedAssembly();
        ChatRunTraceService.RunTraceContext traceContext =
            new ChatRunTraceService.RunTraceContext(assembly.runId(), NOW);
        when(traceService.save(any(com.example.demo.model.ContextAssemblySnapshotDetail.class), eq(traceContext)))
            .thenThrow(new RuntimeException("storage down"));

        ApplicationException exception = assertThrows(ApplicationException.class, () -> service().persistOrDegrade(
            assembly,
            List.of(new LlmClient.Message("user", "current")),
            traceContext
        ));

        assertEquals("context_assembly.storage_failed", exception.getCode());
    }

    private ContextAssemblyService service() {
        return new ContextAssemblyService(
            contextProperties,
            chatAuditProperties,
            conversationRepository,
            historySelector,
            summaryRepository,
            tokenBudgeter,
            null,
            traceService,
            chatRunTraceService,
            JsonMapper.builder().findAndAddModules().build()
        );
    }

    private ChatExecutionRequest request(ContextOptions options) {
        return new ChatExecutionRequest(
            ChatMode.DIRECT,
            "qwen2.5:7b",
            "current",
            null,
            List.of(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            options
        );
    }

    private PreparedContextAssembly preparedAssembly() {
        String runId = UUID.randomUUID().toString();
        String conversationId = UUID.randomUUID().toString();
        return new PreparedContextAssembly(
            true,
            null,
            runId,
            conversationId,
            2,
            ChatMode.DIRECT,
            "current",
            "current",
            List.of(new ContextAssemblyHistoryItem(UUID.randomUUID().toString(), 1, "user", "prior", 2, NOW)),
            List.of(),
            List.of(),
            List.of(),
            new ContextTokenBudget(6, 1000, 1, 2, 0, 0),
            false,
            "ready",
            null,
            null,
            Map.of(),
            false,
            null,
            null,
            0,
            null,
            null,
            null
        );
    }

    private StoredConversationRun run(int turnNo, String status, String prompt) {
        return new StoredConversationRun(
            "00000000-0000-0000-0000-000000000001",
            UUID.randomUUID().toString(),
            turnNo,
            null,
            null,
            "hash-" + turnNo,
            prompt,
            null,
            "NONE",
            NOW.plusSeconds(turnNo),
            status,
            "COMPLETED".equals(status) ? NOW.plusSeconds(turnNo + 10L) : null,
            null,
            null,
            null
        );
    }

    private ConversationSummaryMemory summary(String status, int throughTurnNo) {
        return new ConversationSummaryMemory(
            "00000000-0000-0000-0000-000000000001",
            "Previous compact context",
            List.of("Fact A"),
            List.of("Project Alpha"),
            List.of(new ConversationSummarySourceRef("material-1", "Doc", "D-1", "Alpha", "Counterparty", 3)),
            throughTurnNo,
            UUID.randomUUID().toString(),
            NOW,
            status,
            1
        );
    }

    private ChatExecutionResponse response(String answer) {
        return new ChatExecutionResponse(
            ChatMode.DIRECT,
            "qwen2.5:7b",
            "Prompt",
            answer,
            null,
            NOW.toString(),
            null,
            null,
            null,
            List.of(),
            List.of()
        );
    }

    private static ContextProperties enabledContextProperties() {
        ContextProperties properties = new ContextProperties();
        properties.setEnabled(true);
        properties.setConversationsEnabled(true);
        properties.setHistoryEnabled(true);
        properties.setMaxRecentTurns(6);
        properties.setMaxHistoryTokens(1000);
        return properties;
    }
}
