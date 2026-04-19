package com.example.demo.infrastructure.material;

import java.time.Instant;

public record MaterialSearchSyncQueueEntry(
    String materialId,
    SearchSyncDeliveryState deliveryState,
    int attemptCount,
    Instant nextAttemptAt,
    Instant claimedAt,
    String lastErrorCode,
    String lastErrorMessage,
    Instant requestedAt,
    Instant createdAt,
    Instant updatedAt
) {
}
