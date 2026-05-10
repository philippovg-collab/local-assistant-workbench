package com.example.demo.model;

public record ContextSummary(
    String status,
    Integer historyTurns,
    Integer droppedItems,
    Integer estimatedTokens,
    Boolean degraded,
    String resolvedRetrievalQuery,
    String degradedReason,
    RetrievalQueryResolutionSummary retrievalQueryResolution,
    Boolean summaryUsed,
    String summaryStatus,
    Integer summaryThroughTurnNo,
    String summaryDegradedReason
) {
    public ContextSummary(
        String status,
        Integer historyTurns,
        Integer estimatedTokens
    ) {
        this(status, historyTurns, 0, estimatedTokens, false, null, null, null, false, null, null, null);
    }

    public ContextSummary(
        String status,
        Integer historyTurns,
        Integer droppedItems,
        Integer estimatedTokens,
        Boolean degraded,
        String resolvedRetrievalQuery
    ) {
        this(status, historyTurns, droppedItems, estimatedTokens, degraded, resolvedRetrievalQuery, null, null, false, null, null, null);
    }

    public ContextSummary(
        String status,
        Integer historyTurns,
        Integer droppedItems,
        Integer estimatedTokens,
        Boolean degraded,
        String resolvedRetrievalQuery,
        String degradedReason,
        RetrievalQueryResolutionSummary retrievalQueryResolution
    ) {
        this(
            status,
            historyTurns,
            droppedItems,
            estimatedTokens,
            degraded,
            resolvedRetrievalQuery,
            degradedReason,
            retrievalQueryResolution,
            false,
            null,
            null,
            null
        );
    }
}
