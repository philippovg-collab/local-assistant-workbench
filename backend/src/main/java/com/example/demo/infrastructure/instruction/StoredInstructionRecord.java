package com.example.demo.infrastructure.instruction;

import java.time.Instant;

public record StoredInstructionRecord(
    String id,
    String title,
    String category,
    String content,
    String normalizedContent,
    Instant createdAt,
    Instant updatedAt
) {
}
