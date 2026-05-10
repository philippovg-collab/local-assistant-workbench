package com.example.demo.service.conversation.port;

import com.example.demo.model.AnswerMode;
import com.example.demo.model.ChatMode;
import com.example.demo.service.conversation.StoredConversation;
import com.example.demo.service.conversation.StoredConversationRun;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ConversationRepository {

    StoredConversation createConversation(
        String id,
        String workspaceKey,
        String title,
        ChatMode mode,
        String defaultModel,
        AnswerMode defaultAnswerMode,
        Instant createdAt
    );

    List<StoredConversation> listConversations(String workspaceKey, ChatMode mode, int limit);

    Optional<StoredConversation> findConversation(String conversationId);

    Optional<StoredConversation> findConversationForUpdate(String conversationId);

    StoredConversation updateConversation(String conversationId, String title, String status, Instant updatedAt);

    Optional<StoredConversationRun> findRunByClientTurnId(String conversationId, String clientTurnId);

    Optional<StoredConversationRun> findRunByRunId(String runId);

    boolean runBelongsToConversation(String conversationId, String runId);

    int nextTurnNo(String conversationId);

    void insertRun(
        String conversationId,
        String runId,
        int turnNo,
        String parentRunId,
        String clientTurnId,
        String requestHash,
        String userPrompt,
        String contextAssemblyId,
        Instant createdAt
    );

    void touchConversation(String conversationId, Instant runAt);

    List<StoredConversationRun> listRuns(String conversationId);
}
