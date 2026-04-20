package com.example.demo.infrastructure.audit;

import java.time.Instant;

public record EnqueuedChatRun(
    String runId,
    Instant createdAt
) {
}
