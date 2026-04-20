package com.example.demo.infrastructure.audit;

import com.example.demo.model.ChatExecutionRequest;
import java.time.Instant;

public record ChatRunQueueLease(
    String runId,
    ChatExecutionRequest request,
    Instant createdAt,
    Instant claimedAt,
    int attemptCount
) {
}
