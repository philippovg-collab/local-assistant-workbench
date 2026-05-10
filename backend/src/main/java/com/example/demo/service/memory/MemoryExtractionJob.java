package com.example.demo.service.memory;

import java.time.Instant;

public record MemoryExtractionJob(
    String id,
    String conversationId,
    String runId,
    int turnNo,
    String status,
    int attemptCount,
    Instant nextRetryAt,
    String lastErrorCode,
    String lastErrorMessage,
    String leaseOwner,
    Instant leaseExpiresAt,
    Instant createdAt,
    Instant updatedAt,
    Instant completedAt
) {
}
