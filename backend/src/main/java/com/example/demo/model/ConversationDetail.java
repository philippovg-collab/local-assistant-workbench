package com.example.demo.model;

import java.time.Instant;

public record ConversationDetail(
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
    int turnCount,
    ConversationStickyState stickyState
) {
}
