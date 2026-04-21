package com.example.demo.infrastructure.reference;

import java.time.Instant;

public record StoredReferenceProjectRecord(
    String key,
    String workspaceKey,
    String nameRu,
    boolean active,
    int sortOrder,
    Instant createdAt,
    Instant updatedAt
) {
}
