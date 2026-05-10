package com.example.demo.service.material;

import java.time.Instant;

public record MaterialAutoTaggingTask(
    String id,
    String materialId,
    String contentHash,
    MaterialAutoTaggingStatus status,
    int attemptCount,
    Instant nextRetryAt,
    Instant claimedAt,
    String failureCode,
    String failureMessage,
    String resultCode,
    Instant createdAt,
    Instant updatedAt,
    Instant completedAt
) {
}
