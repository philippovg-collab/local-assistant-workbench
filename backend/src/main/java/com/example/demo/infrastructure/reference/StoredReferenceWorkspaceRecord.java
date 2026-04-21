package com.example.demo.infrastructure.reference;

import java.time.Instant;

public record StoredReferenceWorkspaceRecord(
    String key,
    String nameRu,
    boolean active,
    int sortOrder,
    boolean isDefault,
    Instant createdAt,
    Instant updatedAt
) {
}
