package com.example.demo.model;

import java.time.Instant;

public record ChatRunStatusResponse(
    String id,
    String status,
    Instant createdAt,
    Instant completedAt,
    Instant failedAt,
    Long latencyMsTotal,
    String failureStage,
    String failureCode,
    String failureMessage
) {
}
