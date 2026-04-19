package com.example.demo.infrastructure.knowledge;

import com.example.demo.model.KnowledgeScope;
import java.time.Instant;

public record StoredKnowledgePresetRevisionRecord(
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
}
