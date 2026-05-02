package com.example.demo.service.audit;

import java.time.Instant;

public record EnqueuedChatRun(
    String runId,
    Instant createdAt
) {
}
