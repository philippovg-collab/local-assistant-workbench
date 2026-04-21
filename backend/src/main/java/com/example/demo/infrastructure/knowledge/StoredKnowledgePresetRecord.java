package com.example.demo.infrastructure.knowledge;

import com.example.demo.model.KnowledgeScope;
import com.example.demo.model.SavedKnowledgeFilterKind;
import java.time.Instant;

public record StoredKnowledgePresetRecord(
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
    public StoredKnowledgePresetRecord(
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
