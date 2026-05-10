package com.example.demo.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ContextAssemblySnapshotDetail(
    String id,
    String runId,
    String conversationId,
    Integer turnNo,
    ChatMode assemblyMode,
    String originalPrompt,
    String resolvedRetrievalQuery,
    List<ContextAssemblyHistoryItem> selectedHistory,
    List<ContextAssemblyDroppedItem> droppedItems,
    List<ContextAssemblyMemoryItem> selectedMemory,
    List<ContextAssemblyDroppedMemoryItem> droppedMemory,
    ContextTokenBudget tokenBudget,
    String finalMessagesHash,
    Boolean degradedMode,
    RetrievalQueryResolution retrievalQueryResolution,
    Map<String, Object> stickyResolution,
    Boolean summaryUsed,
    Integer summaryThroughTurnNo,
    String summaryStatus,
    Integer summaryTokenEstimate,
    String summaryDegradedReason,
    String memoryStatus,
    String memoryDegradedReason,
    Instant createdAt
) {
    public ContextAssemblySnapshotDetail {
        selectedHistory = selectedHistory == null ? List.of() : List.copyOf(selectedHistory);
        droppedItems = droppedItems == null ? List.of() : List.copyOf(droppedItems);
        selectedMemory = selectedMemory == null ? List.of() : List.copyOf(selectedMemory);
        droppedMemory = droppedMemory == null ? List.of() : List.copyOf(droppedMemory);
        stickyResolution = stickyResolution == null ? Map.of() : Map.copyOf(stickyResolution);
    }

    public ContextAssemblySnapshotDetail(
        String id,
        String runId,
        String conversationId,
        Integer turnNo,
        ChatMode assemblyMode,
        String originalPrompt,
        String resolvedRetrievalQuery,
        List<ContextAssemblyHistoryItem> selectedHistory,
        List<ContextAssemblyDroppedItem> droppedItems,
        ContextTokenBudget tokenBudget,
        String finalMessagesHash,
        Boolean degradedMode,
        RetrievalQueryResolution retrievalQueryResolution,
        Map<String, Object> stickyResolution,
        Instant createdAt
    ) {
        this(
            id,
            runId,
            conversationId,
            turnNo,
            assemblyMode,
            originalPrompt,
            resolvedRetrievalQuery,
            selectedHistory,
            droppedItems,
            List.of(),
            List.of(),
            tokenBudget,
            finalMessagesHash,
            degradedMode,
            retrievalQueryResolution,
            stickyResolution,
            false,
            null,
            null,
            0,
            null,
            null,
            null,
            createdAt
        );
    }
}
