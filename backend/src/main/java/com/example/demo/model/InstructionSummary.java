package com.example.demo.model;

import java.time.Instant;

public record InstructionSummary(
    String id,
    String title,
    String category,
    String content,
    Instant createdAt
) {
}
