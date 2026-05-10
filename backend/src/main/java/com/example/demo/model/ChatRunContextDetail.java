package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatRunContextDetail(
    String status,
    String runId,
    String conversationId,
    Integer turnNo,
    String contextAssemblyId,
    Instant createdAt,
    ContextFeatureState featureState,
    String promptPreview,
    List<ContextHistoryItem> selectedHistory,
    List<ContextDroppedItem> droppedItems,
    List<ContextMemoryItem> selectedMemory,
    List<ContextDroppedMemoryItem> droppedMemory,
    Map<String, Object> stickyStateResolution,
    RetrievalQueryResolution retrievalQueryResolution,
    ContextSummaryState summaryState,
    ContextMemoryState memoryState,
    ContextInspectorTokenBudget tokenBudget,
    ContextLinks links,
    String reasonCode,
    String reasonMessage
) {
    public ChatRunContextDetail {
        selectedHistory = selectedHistory == null ? List.of() : List.copyOf(selectedHistory);
        droppedItems = droppedItems == null ? List.of() : List.copyOf(droppedItems);
        selectedMemory = selectedMemory == null ? List.of() : List.copyOf(selectedMemory);
        droppedMemory = droppedMemory == null ? List.of() : List.copyOf(droppedMemory);
        stickyStateResolution = stickyStateResolution == null ? Map.of() : Map.copyOf(stickyStateResolution);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ContextFeatureState(
        Boolean context,
        Boolean conversations,
        Boolean history,
        Boolean sticky,
        Boolean rewrite,
        Boolean summary,
        Boolean longTermMemory
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ContextHistoryItem(
        String runId,
        Integer turnNo,
        ChatMode mode,
        String promptPreview,
        String answerPreview,
        String status,
        Integer tokenEstimate,
        ContextLinks links
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ContextDroppedItem(
        String itemType,
        String runId,
        Integer turnNo,
        String reason,
        Integer tokenEstimate,
        ContextLinks links
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ContextMemoryItem(
        String id,
        MemoryEntryType entryType,
        String contentPreview,
        String workspaceKey,
        String projectKey,
        Boolean pinned,
        Integer tokenEstimate
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ContextDroppedMemoryItem(
        String id,
        MemoryEntryType entryType,
        String workspaceKey,
        String projectKey,
        Boolean pinned,
        String reason,
        Integer tokenEstimate
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ContextSummaryState(
        Boolean used,
        String status,
        Integer throughTurnNo,
        Integer tokenEstimate,
        String degradedReason
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ContextMemoryState(
        Boolean enabled,
        Boolean requested,
        String status,
        String degradedReason
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ContextInspectorTokenBudget(
        Integer max,
        Integer used,
        Integer remaining,
        Integer history,
        Integer summary,
        Integer memory,
        Integer retrieval,
        Integer dropped
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ContextLinks(
        String run,
        String status,
        String trace,
        String result,
        String conversationRuns
    ) {
    }
}
