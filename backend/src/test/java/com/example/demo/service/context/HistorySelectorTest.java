package com.example.demo.service.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.demo.config.ContextProperties;
import com.example.demo.model.ChatExecutionResponse;
import com.example.demo.model.ChatMode;
import com.example.demo.model.ChatRunOutputTrace;
import com.example.demo.model.ChatRunRequestSnapshot;
import com.example.demo.model.ChatRunTraceDetail;
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
        assertTrue(selection.tokenBudget().selectedHistoryTokens() > 0);
        assertEquals(0, selection.tokenBudget().droppedHistoryItems());
        assertEquals(0, selection.tokenBudget().droppedHistoryTokens());
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
        assertEquals(1, selection.tokenBudget().selectedHistoryTurns());
        assertEquals(1, selection.tokenBudget().droppedHistoryItems());
        assertTrue(selection.tokenBudget().droppedHistoryTokens() > 0);
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
        assertEquals(0, selection.tokenBudget().selectedHistoryTurns());
        assertEquals(0, selection.tokenBudget().selectedHistoryTokens());
        assertEquals(1, selection.tokenBudget().droppedHistoryItems());
        assertTrue(selection.tokenBudget().droppedHistoryTokens() > 8);
    }

    @Test
    void fallsBackToTraceOutputWhenResultJsonIsAbsent() {
        properties.setMaxRecentTurns(4);
        properties.setMaxHistoryTokens(1000);
        StoredConversationRun prior = run(1, "COMPLETED", "Сохраненный вопрос");
        StoredConversationRun current = run(2, "IN_PROGRESS", "Продолжи");
        when(conversationRepository.listRuns(current.conversationId())).thenReturn(List.of(prior, current));
        when(traceRepository.findResult(prior.runId())).thenReturn(Optional.empty());
        when(traceRepository.findTrace(prior.runId())).thenReturn(Optional.of(trace(prior.runId(), "Trace prompt", "Trace answer")));

        ContextSelection selection = selector.select(current, tokenBudgeter.resolveBudget(new ContextOptions(null, 4, 1000)));

        assertEquals(2, selection.selectedHistory().size());
        assertEquals("Сохраненный вопрос", selection.selectedHistory().get(0).content());
        assertEquals("Trace answer", selection.selectedHistory().get(1).content());
        assertEquals(0, selection.droppedItems().size());
    }

    @Test
    void dropsCompletedRunsWhenResultAndTraceOutputAreUnavailable() {
        properties.setMaxRecentTurns(4);
        properties.setMaxHistoryTokens(1000);
        StoredConversationRun prior = run(1, "COMPLETED", "Вопрос без результата");
        StoredConversationRun current = run(2, "IN_PROGRESS", "Продолжи");
        when(conversationRepository.listRuns(current.conversationId())).thenReturn(List.of(prior, current));
        when(traceRepository.findResult(prior.runId())).thenReturn(Optional.empty());
        when(traceRepository.findTrace(prior.runId())).thenReturn(Optional.empty());

        ContextSelection selection = selector.select(current, tokenBudgeter.resolveBudget(new ContextOptions(null, 4, 1000)));

        assertEquals(0, selection.selectedHistory().size());
        assertEquals(1, selection.droppedItems().size());
        assertEquals("result_unavailable", selection.droppedItems().get(0).reason());
        assertEquals(1, selection.tokenBudget().droppedHistoryItems());
        assertEquals(0, selection.tokenBudget().droppedHistoryTokens());
    }

    @Test
    void excludesCurrentFutureAndNonCompletedRuns() {
        properties.setMaxRecentTurns(4);
        properties.setMaxHistoryTokens(1000);
        StoredConversationRun priorCompleted = run(1, "COMPLETED", "Готовый вопрос");
        StoredConversationRun priorRunning = run(2, "LLM_DONE", "Еще не финальный вопрос");
        StoredConversationRun current = run(3, "COMPLETED", "Текущий вопрос");
        StoredConversationRun futureCompleted = run(4, "COMPLETED", "Будущий вопрос");
        when(conversationRepository.listRuns(current.conversationId()))
            .thenReturn(List.of(priorCompleted, priorRunning, current, futureCompleted));
        when(traceRepository.findResult(priorCompleted.runId())).thenReturn(Optional.of(response("Готовый ответ")));

        ContextSelection selection = selector.select(current, tokenBudgeter.resolveBudget(new ContextOptions(null, 4, 1000)));

        assertEquals(2, selection.selectedHistory().size());
        assertEquals(priorCompleted.runId(), selection.selectedHistory().get(0).runId());
        assertEquals(priorCompleted.runId(), selection.selectedHistory().get(1).runId());
        assertEquals(0, selection.droppedItems().size());
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

    private ChatRunTraceDetail trace(String runId, String prompt, String answer) {
        return new ChatRunTraceDetail(
            runId,
            ChatMode.DIRECT,
            "COMPLETED",
            null,
            "qwen2.5:7b",
            null,
            null,
            null,
            Instant.parse("2026-05-10T00:00:00Z"),
            Instant.parse("2026-05-10T00:00:01Z"),
            null,
            null,
            null,
            null,
            null,
            new ChatRunRequestSnapshot(null, null, prompt, null, null),
            null,
            null,
            List.of(),
            new ChatRunOutputTrace(null, answer, List.of(), null, false, false),
            List.of()
        );
    }
}
