package com.example.demo.model;

public record ContextTokenBudget(
    Integer maxHistoryTurns,
    Integer maxHistoryTokens,
    Integer selectedHistoryTurns,
    Integer selectedHistoryTokens,
    Integer droppedHistoryItems,
    Integer droppedHistoryTokens
) {
}
