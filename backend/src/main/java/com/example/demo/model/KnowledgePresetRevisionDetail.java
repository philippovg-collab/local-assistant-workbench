package com.example.demo.model;

import java.time.Instant;

public record KnowledgePresetRevisionDetail(
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
