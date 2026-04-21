package com.example.demo.model;

import java.time.Instant;

public record ReferenceProject(
    String key,
    String workspaceKey,
    String nameRu,
    boolean active,
    int sortOrder,
    Instant createdAt,
    Instant updatedAt
) {
}
