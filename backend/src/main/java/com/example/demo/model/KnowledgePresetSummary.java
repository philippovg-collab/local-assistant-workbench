package com.example.demo.model;

import java.time.Instant;

public record KnowledgePresetSummary(
    String id,
    String name,
    String description,
    String workspaceKey,
    int revision,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {
}
