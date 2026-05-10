package com.example.demo.service.context;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.config.ContextProperties;
import com.example.demo.model.ChatMode;
import com.example.demo.model.ContextAssemblyDroppedItem;
import com.example.demo.model.ContextTokenBudget;
import com.example.demo.model.ConversationSummaryMemory;
import com.example.demo.service.AfterCommitExecutor;
import com.example.demo.service.ChatRunTraceService;
import com.example.demo.service.context.port.ConversationSummaryRepository;
import com.example.demo.service.conversation.StoredConversationRun;
import com.example.demo.service.conversation.port.ConversationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConversationSummaryTriggerServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-10T00:00:00Z");
    private static final String CONVERSATION_ID = "00000000-0000-0000-0000-000000000001";

    private final ContextProperties properties = properties();
    private final ConversationSummaryRepository summaryRepository = mock(ConversationSummaryRepository.class);
    private final ConversationRepository conversationRepository = mock(ConversationRepository.class);
    private final ConversationSummaryWorkerService workerService = mock(ConversationSummaryWorkerService.class);
    private final ChatRunTraceService traceService = mock(ChatRunTraceService.class);
    private final ConversationSummaryTriggerService service = new ConversationSummaryTriggerService(
        properties,
        summaryRepository,
        conversationRepository,
        workerService,
        new AfterCommitExecutor(),
        traceService
    );

    @Test
    void enqueuesRefreshAfterConfiguredCompletedTurnThreshold() {
        when(summaryRepository.findByConversationId(CONVERSATION_ID))
            .thenReturn(Optional.of(summary(2)));
        when(conversationRepository.listRuns(CONVERSATION_ID))
            .thenReturn(List.of(
                run(1, "COMPLETED"),
                run(2, "COMPLETED"),
                run(3, "COMPLETED"),
                run(4, "COMPLETED")
            ));

        service.afterCompleted(assembly(4, List.of(), 0), traceContext());

        verify(summaryRepository).requestRefresh(eq(CONVERSATION_ID), eq(4), any(Instant.class));
        verify(workerService).requestProcessing();
    }

    @Test
    void ignoresFailedCancelledAndPendingTurnsWhenCheckingThreshold() {
        when(summaryRepository.findByConversationId(CONVERSATION_ID))
            .thenReturn(Optional.of(summary(2)));
        when(conversationRepository.listRuns(CONVERSATION_ID))
            .thenReturn(List.of(
                run(3, "FAILED"),
                run(4, "CANCELLED"),
                run(5, "IN_PROGRESS"),
                run(6, "COMPLETED")
            ));

        service.afterCompleted(assembly(6, List.of(), 0), traceContext());

        verify(summaryRepository, never()).requestRefresh(any(), anyInt(), any());
        verify(workerService, never()).requestProcessing();
    }

    @Test
    void tokenBudgetDropsTriggerRefreshBeforeThreshold() {
        when(summaryRepository.findByConversationId(CONVERSATION_ID))
            .thenReturn(Optional.of(summary(5)));
        when(conversationRepository.listRuns(CONVERSATION_ID))
            .thenReturn(List.of(run(6, "COMPLETED")));

        service.afterCompleted(
            assembly(6, List.of(new ContextAssemblyDroppedItem("run-2", 2, "token_budget", 200)), 200),
            traceContext()
        );

        verify(summaryRepository).requestRefresh(eq(CONVERSATION_ID), eq(6), any(Instant.class));
        verify(workerService).requestProcessing();
    }

    @Test
    void disabledSummaryDoesNotInspectOrEnqueue() {
        properties.setSummaryEnabled(false);

        service.afterCompleted(assembly(4, List.of(), 0), traceContext());

        verify(summaryRepository, never()).findByConversationId(any());
        verify(summaryRepository, never()).requestRefresh(any(), anyInt(), any());
        verify(workerService, never()).requestProcessing();
    }

    private PreparedContextAssembly assembly(
        int turnNo,
        List<ContextAssemblyDroppedItem> droppedItems,
        int droppedHistoryTokens
    ) {
        return new PreparedContextAssembly(
            true,
            null,
            UUID.randomUUID().toString(),
            CONVERSATION_ID,
            turnNo,
            ChatMode.DIRECT,
            "prompt",
            "prompt",
            List.of(),
            droppedItems,
            List.of(),
            List.of(),
            new ContextTokenBudget(6, 1000, 0, 0, droppedItems.size(), droppedHistoryTokens),
            droppedHistoryTokens > 0,
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

    private ConversationSummaryMemory summary(int throughTurnNo) {
        return new ConversationSummaryMemory(
            CONVERSATION_ID,
            "summary",
            List.of(),
            List.of(),
            List.of(),
            throughTurnNo,
            "run-" + throughTurnNo,
            NOW,
            "READY",
            1
        );
    }

    private StoredConversationRun run(int turnNo, String status) {
        return new StoredConversationRun(
            CONVERSATION_ID,
            UUID.randomUUID().toString(),
            turnNo,
            null,
            null,
            "hash-" + turnNo,
            "prompt-" + turnNo,
            null,
            "NONE",
            NOW.plusSeconds(turnNo),
            status,
            "COMPLETED".equals(status) ? NOW.plusSeconds(turnNo + 1L) : null,
            null,
            null,
            null
        );
    }

    private ChatRunTraceService.RunTraceContext traceContext() {
        return new ChatRunTraceService.RunTraceContext(UUID.randomUUID().toString(), NOW);
    }

    private static ContextProperties properties() {
        ContextProperties properties = new ContextProperties();
        properties.setEnabled(true);
        properties.setConversationsEnabled(true);
        properties.setHistoryEnabled(true);
        properties.setSummaryEnabled(true);
        properties.setSummaryRefreshTurns(2);
        return properties;
    }
}
