package com.example.demo.service.reference;

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
