package com.example.demo.service.context;

import com.example.demo.model.ChatMode;
import com.example.demo.model.ContextAssemblyDroppedItem;
import com.example.demo.model.ContextAssemblyDroppedMemoryItem;
import com.example.demo.model.ContextAssemblyHistoryItem;
import com.example.demo.model.ContextAssemblyMemoryItem;
import com.example.demo.model.ContextSummary;
import com.example.demo.model.ContextTokenBudget;
import com.example.demo.model.RetrievalQueryResolution;
import java.util.List;
import java.util.Map;

public record PreparedContextAssembly(
    boolean active,
    String snapshotId,
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
    boolean degradedMode,
    String status,
    String finalMessagesHash,
    RetrievalQueryResolution retrievalQueryResolution,
    Map<String, Object> stickyResolution,
    boolean summaryUsed,
    Integer summaryThroughTurnNo,
    String summaryStatus,
    Integer summaryTokenEstimate,
    String summaryDegradedReason,
    String memoryStatus,
    String memoryDegradedReason
) {
    public PreparedContextAssembly {
        selectedHistory = selectedHistory == null ? List.of() : List.copyOf(selectedHistory);
        droppedItems = droppedItems == null ? List.of() : List.copyOf(droppedItems);
        selectedMemory = selectedMemory == null ? List.of() : List.copyOf(selectedMemory);
        droppedMemory = droppedMemory == null ? List.of() : List.copyOf(droppedMemory);
        stickyResolution = stickyResolution == null ? Map.of() : Map.copyOf(stickyResolution);
    }

    public static PreparedContextAssembly inactive() {
        return new PreparedContextAssembly(
            false,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            false,
            "inactive",
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

    PreparedContextAssembly withSnapshot(String snapshotId, String finalMessagesHash) {
        return new PreparedContextAssembly(
            active,
            snapshotId,
            runId,
            conversationId,
            turnNo,
            assemblyMode,
            originalPrompt,
            resolvedRetrievalQuery,
            selectedHistory,
            droppedItems,
            selectedMemory,
            droppedMemory,
            tokenBudget,
            degradedMode,
            status,
            finalMessagesHash,
            retrievalQueryResolution,
            stickyResolution,
            summaryUsed,
            summaryThroughTurnNo,
            summaryStatus,
            summaryTokenEstimate,
            summaryDegradedReason,
            memoryStatus,
            memoryDegradedReason
        );
    }

    public PreparedContextAssembly withRetrievalQueryResolution(RetrievalQueryResolution resolution) {
        if (!active || resolution == null) {
            return this;
        }
        return new PreparedContextAssembly(
            active,
            snapshotId,
            runId,
            conversationId,
            turnNo,
            assemblyMode,
            originalPrompt,
            resolution.queryForRetrieval(),
            selectedHistory,
            droppedItems,
            selectedMemory,
            droppedMemory,
            tokenBudget,
            degradedMode || Boolean.TRUE.equals(resolution.degraded()),
            status,
            finalMessagesHash,
            resolution,
            stickyResolution,
            summaryUsed,
            summaryThroughTurnNo,
            summaryStatus,
            summaryTokenEstimate,
            summaryDegradedReason,
            memoryStatus,
            memoryDegradedReason
        );
    }

    PreparedContextAssembly degradedWithoutHistory(String status) {
        ContextTokenBudget degradedBudget = tokenBudget == null
            ? new ContextTokenBudget(0, 0, 0, 0, droppedItems.size(), 0)
            : new ContextTokenBudget(
                tokenBudget.maxHistoryTurns(),
                tokenBudget.maxHistoryTokens(),
                0,
                0,
                droppedItems.size(),
                tokenBudget.droppedHistoryTokens()
            );
        return new PreparedContextAssembly(
            active,
            null,
            runId,
            conversationId,
            turnNo,
            assemblyMode,
            originalPrompt,
            resolvedRetrievalQuery,
            List.of(),
            droppedItems,
            List.of(),
            droppedMemory,
            degradedBudget,
            true,
            status,
            null,
            retrievalQueryResolution,
            stickyResolution,
            summaryUsed,
            summaryThroughTurnNo,
            summaryStatus,
            summaryTokenEstimate,
            summaryDegradedReason,
            memoryStatus,
            memoryDegradedReason
        );
    }

    public ContextSummary summary() {
        if (!active) {
            return null;
        }
        return new ContextSummary(
            status,
            tokenBudget == null ? 0 : tokenBudget.selectedHistoryTurns(),
            droppedItems.size(),
            tokenBudget == null ? 0 : tokenBudget.selectedHistoryTokens(),
            degradedMode,
            resolvedRetrievalQuery,
            retrievalQueryResolution == null ? null : retrievalQueryResolution.degradedReason(),
            retrievalQueryResolution == null ? null : retrievalQueryResolution.summary(),
            summaryUsed,
            summaryStatus,
            summaryThroughTurnNo,
            summaryDegradedReason
        );
    }
}
