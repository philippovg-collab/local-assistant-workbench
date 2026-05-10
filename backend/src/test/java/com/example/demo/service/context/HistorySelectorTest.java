package com.example.demo.service.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.demo.config.ContextProperties;
import com.example.demo.model.ChatExecutionResponse;
import com.example.demo.model.ChatMode;
import com.example.demo.model.ContextOptions;
import com.example.demo.service.audit.port.ChatRunTraceRepository;
import com.example.demo.service.conversation.StoredConversationRun;
import com.example.demo.service.conversation.port.ConversationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HistorySelectorTest {

    private final ConversationRepository conversationRepository = mock(ConversationRepository.class);
    private final ChatRunTraceRepository traceRepository = mock(ChatRunTraceRepository.class);
    private final ContextProperties properties = new ContextProperties();
    private final ContextTokenBudgeter tokenBudgeter = new ContextTokenBudgeter(properties);
    private final HistorySelector selector = new HistorySelector(
        conversationRepository,
        traceRepository,
        tokenBudgeter
    );

    @Test
    void selectsOnlyCompletedPriorRunsAndPreservesChronologicalMessageOrder() {
        properties.setMaxRecentTurns(4);
        properties.setMaxHistoryTokens(1000);
        StoredConversationRun first = run(1, "COMPLETED", "Первый вопрос");
        StoredConversationRun failed = run(2, "FAILED", "Неудачный вопрос");
        StoredConversationRun second = run(3, "COMPLETED", "Второй вопрос");
        StoredConversationRun current = run(4, "IN_PROGRESS", "Сделай короче");
        when(conversationRepository.listRuns(current.conversationId()))
            .thenReturn(List.of(first, failed, second, current));
        when(traceRepository.findResult(first.runId())).thenReturn(Optional.of(response("Первый ответ")));
        when(traceRepository.findResult(second.runId())).thenReturn(Optional.of(response("Второй ответ")));

        ContextSelection selection = selector.select(current, tokenBudgeter.resolveBudget(new ContextOptions(null, 4, 1000)));

        assertEquals(4, selection.selectedHistory().size());
        assertEquals("Первый вопрос", selection.selectedHistory().get(0).content());
        assertEquals("Первый ответ", selection.selectedHistory().get(1).content());
        assertEquals("Второй вопрос", selection.selectedHistory().get(2).content());
        assertEquals("Второй ответ", selection.selectedHistory().get(3).content());
        assertEquals(0, selection.droppedItems().size());
        assertEquals(2, selection.tokenBudget().selectedHistoryTurns());
    }

    @Test
    void dropsOlderRunsWhenMaxTurnsIsReducedByRequest() {
        properties.setMaxRecentTurns(4);
        properties.setMaxHistoryTokens(1000);
        StoredConversationRun first = run(1, "COMPLETED", "Первый вопрос");
        StoredConversationRun second = run(2, "COMPLETED", "Второй вопрос");
        StoredConversationRun current = run(3, "IN_PROGRESS", "Продолжи");
        when(conversationRepository.listRuns(current.conversationId())).thenReturn(List.of(first, second, current));
        when(traceRepository.findResult(first.runId())).thenReturn(Optional.of(response("Первый ответ")));
        when(traceRepository.findResult(second.runId())).thenReturn(Optional.of(response("Второй ответ")));

        ContextSelection selection = selector.select(current, tokenBudgeter.resolveBudget(new ContextOptions(null, 1, 1000)));

        assertEquals(2, selection.selectedHistory().size());
        assertEquals("Второй вопрос", selection.selectedHistory().get(0).content());
        assertEquals(1, selection.droppedItems().size());
        assertEquals("max_turns", selection.droppedItems().get(0).reason());
    }

    @Test
    void dropsRunsThatDoNotFitTokenBudget() {
        properties.setMaxRecentTurns(4);
        properties.setMaxHistoryTokens(8);
        StoredConversationRun first = run(1, "COMPLETED", "Очень длинный предыдущий вопрос");
        StoredConversationRun current = run(2, "IN_PROGRESS", "Продолжи");
        when(conversationRepository.listRuns(current.conversationId())).thenReturn(List.of(first, current));
        when(traceRepository.findResult(first.runId()))
            .thenReturn(Optional.of(response("Очень длинный предыдущий ответ")));

        ContextSelection selection = selector.select(current, tokenBudgeter.resolveBudget(new ContextOptions(null, 4, 8)));

        assertEquals(0, selection.selectedHistory().size());
        assertEquals(1, selection.droppedItems().size());
        assertEquals("token_budget", selection.droppedItems().get(0).reason());
    }

    private StoredConversationRun run(int turnNo, String status, String prompt) {
        String conversationId = "00000000-0000-0000-0000-000000000001";
        return new StoredConversationRun(
            conversationId,
            UUID.randomUUID().toString(),
            turnNo,
            null,
            null,
            "hash-" + turnNo,
            prompt,
            null,
            "NONE",
            Instant.parse("2026-05-10T00:00:00Z").plusSeconds(turnNo),
            status,
            "COMPLETED".equals(status) ? Instant.parse("2026-05-10T00:00:10Z").plusSeconds(turnNo) : null,
            null,
            null,
            null
        );
    }

    private ChatExecutionResponse response(String answer) {
        return new ChatExecutionResponse(
            ChatMode.DIRECT,
            "qwen2.5:7b",
            "Prompt",
            answer,
            null,
            "2026-05-10T00:00:00Z",
            null,
            null,
            null,
            List.of(),
            List.of()
        );
    }
}
