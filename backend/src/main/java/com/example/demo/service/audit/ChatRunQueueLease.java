package com.example.demo.service.audit;

import com.example.demo.model.ChatExecutionRequest;
import java.time.Instant;

public record ChatRunQueueLease(
    String runId,
    ChatExecutionRequest request,
    Instant createdAt,
    Instant claimedAt,
    int attemptCount,
    String leaseOwner,
    Instant leaseExpiresAt
) {
    public ChatRunLeaseToken leaseToken() {
        return ChatRunLeaseToken.from(this);
    }
}
