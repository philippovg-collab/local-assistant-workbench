package com.example.demo.service.reference;

import java.time.Instant;

public record StoredReferenceWorkspaceRecord(
    String key,
    String nameRu,
    String description,
    boolean active,
    int sortOrder,
    boolean isDefault,
    Instant createdAt,
    Instant updatedAt
) {
}
