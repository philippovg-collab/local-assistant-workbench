package com.example.demo.service.context;

import com.example.demo.model.AnswerMode;
import com.example.demo.model.KnowledgeScope;
import com.example.demo.model.RetrievalFilters;
import java.time.Instant;
import java.util.List;

public record StoredConversationStickyState(
    String conversationId,
    String model,
    AnswerMode answerMode,
    String instructionWorkspaceKey,
    KnowledgeScope knowledgeScope,
    RetrievalFilters retrievalFilters,
    List<String> instructionIds,
    List<String> scenarioInstructionIds,
    String updatedFromRunId,
    Integer updatedThroughTurnNo,
    int version,
    Instant createdAt,
    Instant updatedAt
) {
    public StoredConversationStickyState {
        instructionIds = instructionIds == null ? null : List.copyOf(instructionIds);
        scenarioInstructionIds = scenarioInstructionIds == null ? null : List.copyOf(scenarioInstructionIds);
    }
}
