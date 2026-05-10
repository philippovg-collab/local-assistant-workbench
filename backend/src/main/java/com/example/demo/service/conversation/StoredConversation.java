package com.example.demo.service.conversation;

import com.example.demo.model.AnswerMode;
import com.example.demo.model.ChatMode;
import java.time.Instant;

public record StoredConversation(
    String id,
    String workspaceKey,
    String title,
    ChatMode mode,
    String status,
    String defaultModel,
    AnswerMode defaultAnswerMode,
    Instant createdAt,
    Instant updatedAt,
    Instant lastRunAt,
    int turnCount
) {
}
