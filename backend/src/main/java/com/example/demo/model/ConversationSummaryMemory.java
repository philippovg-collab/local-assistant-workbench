package com.example.demo.model;

import java.time.Instant;
import java.util.List;

public record ConversationSummaryMemory(
    String conversationId,
    String summaryText,
    List<String> facts,
    List<String> activeEntities,
    List<ConversationSummarySourceRef> sourceRefs,
    Integer summaryThroughTurnNo,
    String updatedFromRunId,
    Instant updatedAt,
    String status,
    Integer version
) {
    public ConversationSummaryMemory {
        facts = facts == null ? List.of() : List.copyOf(facts);
        activeEntities = activeEntities == null ? List.of() : List.copyOf(activeEntities);
        sourceRefs = sourceRefs == null ? List.of() : List.copyOf(sourceRefs);
    }
}
