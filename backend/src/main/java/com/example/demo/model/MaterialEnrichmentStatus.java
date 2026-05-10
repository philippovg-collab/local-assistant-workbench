package com.example.demo.model;

import java.time.Instant;

public record MaterialEnrichmentStatus(
    String taskId,
    MaterialEnrichmentState status,
    int attempts,
    Instant nextRetryAt,
    String failureCode,
    String failureMessage,
    String resultCode,
    Instant updatedAt
) {
    public MaterialEnrichmentStatus {
        status = status == null ? MaterialEnrichmentState.NOT_REQUESTED : status;
        attempts = Math.max(0, attempts);
    }

    public static MaterialEnrichmentStatus notRequested() {
        return new MaterialEnrichmentStatus(
            null,
            MaterialEnrichmentState.NOT_REQUESTED,
            0,
            null,
            null,
            null,
            null,
            null
        );
    }
}
