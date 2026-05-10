package com.example.demo.service.context.port;

import com.example.demo.model.AnswerMode;
import com.example.demo.model.KnowledgeScope;
import com.example.demo.model.RetrievalFilters;
import com.example.demo.service.context.StoredConversationStickyState;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ConversationStickyStateRepository {

    Optional<StoredConversationStickyState> findByConversationId(String conversationId);

    Optional<StoredConversationStickyState> upsertCompletedTurn(
        String conversationId,
        String model,
        AnswerMode answerMode,
        String instructionWorkspaceKey,
        KnowledgeScope knowledgeScope,
        RetrievalFilters retrievalFilters,
        List<String> instructionIds,
        List<String> scenarioInstructionIds,
        String updatedFromRunId,
        int updatedThroughTurnNo,
        Instant updatedAt
    );
}
