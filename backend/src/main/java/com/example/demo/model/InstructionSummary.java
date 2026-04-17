package com.example.demo.model;

import java.time.Instant;

public record InstructionSummary(
    String id,
    String title,
    InstructionCategory category,
    Instant createdAt,
    Instant updatedAt,
    String preview
) {
    public InstructionSummary(
        String id,
        String title,
        InstructionCategory category,
        Instant createdAt,
        String preview
    ) {
        this(id, title, category, createdAt, createdAt, preview);
    }
}
