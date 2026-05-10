package com.example.demo.service.context;

import com.example.demo.model.ChatExecutionResponse;
import com.example.demo.model.ChatRunTraceDetail;
import com.example.demo.model.ContextAssemblyDroppedItem;
import com.example.demo.model.ContextAssemblyHistoryItem;
import com.example.demo.service.audit.port.ChatRunTraceRepository;
import com.example.demo.service.context.ContextTokenBudgeter.Budget;
import com.example.demo.service.conversation.StoredConversationRun;
import com.example.demo.service.conversation.port.ConversationRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class HistorySelector {

    private static final String STATUS_COMPLETED = "COMPLETED";

    private final ConversationRepository conversationRepository;
    private final ChatRunTraceRepository traceRepository;
    private final ContextTokenBudgeter tokenBudgeter;

    public HistorySelector(
        ConversationRepository conversationRepository,
        ChatRunTraceRepository traceRepository,
        ContextTokenBudgeter tokenBudgeter
    ) {
        this.conversationRepository = conversationRepository;
        this.traceRepository = traceRepository;
        this.tokenBudgeter = tokenBudgeter;
    }

    ContextSelection select(StoredConversationRun currentRun, Budget budget) {
        List<StoredConversationRun> candidates = conversationRepository.listRuns(currentRun.conversationId()).stream()
            .filter(run -> run.turnNo() < currentRun.turnNo())
            .filter(run -> STATUS_COMPLETED.equals(run.status()))
            .sorted(Comparator.comparingInt(StoredConversationRun::turnNo).reversed())
            .toList();

        List<HistoryPair> selectedPairs = new ArrayList<>();
        List<ContextAssemblyDroppedItem> droppedItems = new ArrayList<>();
        int selectedTokens = 0;
        int droppedTokens = 0;

        for (StoredConversationRun candidate : candidates) {
            HistoryPair pair = loadPair(candidate);
            if (pair == null) {
                droppedItems.add(drop(candidate, "result_unavailable", 0));
                continue;
            }
            if (selectedPairs.size() >= budget.maxHistoryTurns()) {
                droppedItems.add(drop(candidate, "max_turns", pair.estimatedTokens()));
                droppedTokens += pair.estimatedTokens();
                continue;
            }
            if (selectedTokens + pair.estimatedTokens() > budget.maxHistoryTokens()) {
                droppedItems.add(drop(candidate, "token_budget", pair.estimatedTokens()));
                droppedTokens += pair.estimatedTokens();
                continue;
            }
            selectedPairs.add(pair);
            selectedTokens += pair.estimatedTokens();
        }

        List<ContextAssemblyHistoryItem> selectedHistory = selectedPairs.stream()
            .sorted(Comparator.comparingInt(HistoryPair::turnNo))
            .flatMap(pair -> pair.items().stream())
            .toList();
        return new ContextSelection(
            selectedHistory,
            droppedItems,
            tokenBudgeter.snapshot(
                budget,
                selectedPairs.size(),
                selectedTokens,
                droppedItems.size(),
                droppedTokens
            )
        );
    }

    ContextSelection empty(Budget budget, String dropReason, StoredConversationRun currentRun) {
        List<ContextAssemblyDroppedItem> droppedItems = List.of();
        if (StringUtils.hasText(dropReason) && currentRun != null) {
            droppedItems = conversationRepository.listRuns(currentRun.conversationId()).stream()
                .filter(run -> run.turnNo() < currentRun.turnNo())
                .filter(run -> STATUS_COMPLETED.equals(run.status()))
                .map(run -> drop(run, dropReason, 0))
                .toList();
        }
        return new ContextSelection(
            List.of(),
            droppedItems,
            tokenBudgeter.snapshot(budget, 0, 0, droppedItems.size(), 0)
        );
    }

    private HistoryPair loadPair(StoredConversationRun run) {
        ChatExecutionResponse response = traceRepository.findResult(run.runId()).orElse(null);
        if (response != null) {
            return pairFrom(run, firstNonBlank(run.userPrompt(), response.prompt()), response.answer());
        }

        ChatRunTraceDetail trace = traceRepository.findTrace(run.runId()).orElse(null);
        if (trace == null || trace.output() == null) {
            return null;
        }
        String prompt = trace.requestSnapshot() == null ? run.userPrompt() : trace.requestSnapshot().prompt();
        return pairFrom(run, firstNonBlank(run.userPrompt(), prompt), trace.output().finalUserAnswer());
    }

    private HistoryPair pairFrom(StoredConversationRun run, String prompt, String answer) {
        if (!StringUtils.hasText(prompt) || !StringUtils.hasText(answer)) {
            return null;
        }
        int userTokens = tokenBudgeter.estimateTokens(prompt);
        int assistantTokens = tokenBudgeter.estimateTokens(answer);
        List<ContextAssemblyHistoryItem> items = List.of(
            new ContextAssemblyHistoryItem(run.runId(), run.turnNo(), "user", prompt, userTokens, run.createdAt()),
            new ContextAssemblyHistoryItem(run.runId(), run.turnNo(), "assistant", answer, assistantTokens, run.createdAt())
        );
        return new HistoryPair(run.turnNo(), userTokens + assistantTokens, items);
    }

    private ContextAssemblyDroppedItem drop(StoredConversationRun run, String reason, int estimatedTokens) {
        return new ContextAssemblyDroppedItem(run.runId(), run.turnNo(), reason, estimatedTokens);
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private record HistoryPair(
        int turnNo,
        int estimatedTokens,
        List<ContextAssemblyHistoryItem> items
    ) {
    }
}
