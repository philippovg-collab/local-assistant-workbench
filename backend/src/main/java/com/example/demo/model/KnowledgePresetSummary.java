package com.example.demo.model;

import java.time.Instant;

public record KnowledgePresetSummary(
    String id,
    SavedKnowledgeFilterKind kind,
    String name,
    String description,
    String workspaceKey,
    int revision,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {
    public KnowledgePresetSummary(
        String id,
        String name,
        String description,
        String workspaceKey,
        int revision,
        boolean active,
        Instant createdAt,
        Instant updatedAt
    ) {
        this(id, SavedKnowledgeFilterKind.PRESET, name, description, workspaceKey, revision, active, createdAt, updatedAt);
    }
}
