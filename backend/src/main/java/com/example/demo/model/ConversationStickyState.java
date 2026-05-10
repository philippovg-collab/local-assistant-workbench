package com.example.demo.model;

import java.time.Instant;
import java.util.List;

public record ConversationStickyState(
    String model,
    AnswerMode answerMode,
    String instructionWorkspaceKey,
    KnowledgeScope knowledgeScope,
    RetrievalFilters retrievalFilters,
    List<String> instructionIds,
    List<String> scenarioInstructionIds,
    String updatedFromRunId,
    Integer updatedThroughTurnNo,
    Integer version,
    Instant createdAt,
    Instant updatedAt
) {
    public ConversationStickyState {
        instructionIds = instructionIds == null ? null : List.copyOf(instructionIds);
        scenarioInstructionIds = scenarioInstructionIds == null ? null : List.copyOf(scenarioInstructionIds);
    }
}
