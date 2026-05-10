package com.example.demo.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ContextOptions(
    Boolean includeHistory,
    Boolean useHistory,
    Boolean useStickyState,
    Boolean resolveRetrievalQuery,
    Boolean useSummary,
    Boolean useLongTermMemory,
    @Min(0)
    @Max(200)
    Integer maxHistoryTurns,
    @Min(0)
    @Max(200000)
    Integer maxContextTokens
) {
    public ContextOptions(
        Boolean includeHistory,
        Integer maxHistoryTurns,
        Integer maxContextTokens
    ) {
        this(includeHistory, null, null, null, null, null, maxHistoryTurns, maxContextTokens);
    }

    public ContextOptions(
        Boolean includeHistory,
        Boolean useHistory,
        Integer maxHistoryTurns,
        Integer maxContextTokens
    ) {
        this(includeHistory, useHistory, null, null, null, null, maxHistoryTurns, maxContextTokens);
    }

    public ContextOptions(
        Boolean includeHistory,
        Boolean useHistory,
        Boolean useStickyState,
        Integer maxHistoryTurns,
        Integer maxContextTokens
    ) {
        this(includeHistory, useHistory, useStickyState, null, null, null, maxHistoryTurns, maxContextTokens);
    }

    public Boolean historyEnabled() {
        return useHistory == null ? includeHistory : useHistory;
    }
}
