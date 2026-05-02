package com.example.demo.service.knowledge.port;

import com.example.demo.model.KnowledgeScope;
import com.example.demo.model.SavedKnowledgeFilterKind;
import java.time.Instant;

public record StoredKnowledgePresetRevisionRecord(
    String presetId,
    SavedKnowledgeFilterKind kind,
    int revision,
    String name,
    String description,
    KnowledgeScope scope,
    boolean active,
    Integer restoredFromRevision,
    Instant createdAt,
    Instant updatedAt
) {
    public StoredKnowledgePresetRevisionRecord(
        String presetId,
        int revision,
        String name,
        String description,
        KnowledgeScope scope,
        boolean active,
        Integer restoredFromRevision,
        Instant createdAt,
        Instant updatedAt
    ) {
        this(
            presetId,
            SavedKnowledgeFilterKind.PRESET,
            revision,
            name,
            description,
            scope,
            active,
            restoredFromRevision,
            createdAt,
            updatedAt
        );
    }
}
