package com.example.demo.model;

import java.time.Instant;

public record ContextAssemblyHistoryItem(
    String runId,
    Integer turnNo,
    String role,
    String content,
    Integer estimatedTokens,
    Instant createdAt
) {
}
