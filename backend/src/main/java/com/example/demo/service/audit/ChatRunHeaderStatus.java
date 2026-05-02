package com.example.demo.service.audit;

import java.time.Instant;

public record ChatRunHeaderStatus(
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
    public ChatRunHeaderStatus(
        String id,
        String status,
        Instant createdAt,
        Instant completedAt,
        String failureMessage
    ) {
        this(id, status, createdAt, completedAt, null, null, null, null, failureMessage);
    }
}
