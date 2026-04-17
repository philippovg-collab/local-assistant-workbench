package com.example.demo.model;

import java.time.Instant;

public record InstructionDetail(
    String id,
    String title,
    InstructionCategory category,
    String content,
    Instant createdAt,
    Instant updatedAt
) {
    public InstructionDetail(
        String id,
        String title,
        InstructionCategory category,
        String content,
        Instant createdAt
    ) {
        this(id, title, category, content, createdAt, createdAt);
    }
}
