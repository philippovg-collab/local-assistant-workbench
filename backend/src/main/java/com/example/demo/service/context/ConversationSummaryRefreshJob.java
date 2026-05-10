package com.example.demo.service.context;

import java.time.Instant;

public record ConversationSummaryRefreshJob(
    String conversationId,
    int requestedThroughTurnNo,
    String status,
    int attemptCount,
    Instant nextRetryAt,
    String leaseOwner,
    Instant leaseExpiresAt,
    String lastErrorCode,
    String lastErrorMessage,
    Instant createdAt,
    Instant updatedAt
) {
}
