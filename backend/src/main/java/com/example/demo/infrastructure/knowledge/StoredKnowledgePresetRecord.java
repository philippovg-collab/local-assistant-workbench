package com.example.demo.infrastructure.knowledge;

import com.example.demo.model.KnowledgeScope;
import java.time.Instant;

public record StoredKnowledgePresetRecord(
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
