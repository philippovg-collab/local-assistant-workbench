package com.example.demo.model;

import java.time.Instant;

public record ConversationRunDetail(
    String conversationId,
    String runId,
    int turnNo,
    String parentRunId,
    String clientTurnId,
    String userPrompt,
    String contextAssemblyId,
    String contextAssemblyStatus,
    Instant createdAt,
    String status,
    Instant completedAt,
    Instant failedAt,
    String failureCode,
    String failureMessage,
    String statusUrl,
    String traceUrl,
    String resultUrl,
    String cancelUrl
) {
}
