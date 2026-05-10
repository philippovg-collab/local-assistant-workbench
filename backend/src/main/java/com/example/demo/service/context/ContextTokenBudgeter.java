package com.example.demo.service.context;

import com.example.demo.config.ContextProperties;
import com.example.demo.model.ContextOptions;
import com.example.demo.model.ContextTokenBudget;
import org.springframework.stereotype.Service;

@Service
public class ContextTokenBudgeter {

    private static final int CHARS_PER_TOKEN_ESTIMATE = 4;

    private final ContextProperties properties;

    public ContextTokenBudgeter(ContextProperties properties) {
        this.properties = properties;
    }

    int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(text.trim().length() / (double) CHARS_PER_TOKEN_ESTIMATE));
    }

    Budget resolveBudget(ContextOptions options) {
        int maxTurns = Math.max(0, properties.getMaxRecentTurns());
        int maxTokens = Math.max(0, properties.getMaxHistoryTokens());
        if (options != null && options.maxHistoryTurns() != null) {
            maxTurns = Math.min(maxTurns, Math.max(0, options.maxHistoryTurns()));
        }
        if (options != null && options.maxContextTokens() != null) {
            maxTokens = Math.min(maxTokens, Math.max(0, options.maxContextTokens()));
        }
        return new Budget(maxTurns, maxTokens);
    }

    ContextTokenBudget snapshot(
        Budget budget,
        int selectedTurns,
        int selectedTokens,
        int droppedItems,
        int droppedTokens
    ) {
        return new ContextTokenBudget(
            budget.maxHistoryTurns(),
            budget.maxHistoryTokens(),
            selectedTurns,
            selectedTokens,
            droppedItems,
            droppedTokens
        );
    }

    record Budget(int maxHistoryTurns, int maxHistoryTokens) {
    }
}
