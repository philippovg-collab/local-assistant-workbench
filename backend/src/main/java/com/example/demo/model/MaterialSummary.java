package com.example.demo.model;

import java.time.Instant;

public record MaterialSummary(
    String id,
    String title,
    String sourceType,
    String originalFileName,
    boolean extractable,
    Instant createdAt,
    int contentLength,
    String preview
) {
}
