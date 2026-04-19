package com.example.demo.model;

import java.time.Instant;

public record KnowledgePresetDetail(
    String id,
    String name,
    String description,
    KnowledgeScope scope,
    int revision,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {
}
