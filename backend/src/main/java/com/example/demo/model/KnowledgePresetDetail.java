package com.example.demo.model;

import java.time.Instant;

public record KnowledgePresetDetail(
    String id,
    SavedKnowledgeFilterKind kind,
    String name,
    String description,
    KnowledgeScope scope,
    int revision,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {
    public KnowledgePresetDetail(
        String id,
        String name,
        String description,
        KnowledgeScope scope,
        int revision,
        boolean active,
        Instant createdAt,
        Instant updatedAt
    ) {
        this(id, SavedKnowledgeFilterKind.PRESET, name, description, scope, revision, active, createdAt, updatedAt);
    }
}
