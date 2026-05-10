package com.example.demo.service.conversation;

import java.time.Instant;

public record StoredConversationRun(
    String conversationId,
    String runId,
    int turnNo,
    String parentRunId,
    String clientTurnId,
    String requestHash,
    String userPrompt,
    String contextAssemblyId,
    String contextAssemblyStatus,
    Instant createdAt,
    String status,
    Instant completedAt,
    Instant failedAt,
    String failureCode,
    String failureMessage
) {
}
