package com.example.demo.infrastructure.instruction;

import com.example.demo.model.InstructionCategory;
import java.time.Instant;

public record StoredInstructionRecord(
    String id,
    String title,
    InstructionCategory category,
    String content,
    String normalizedContent,
    Instant createdAt,
    Instant updatedAt
) {
}
