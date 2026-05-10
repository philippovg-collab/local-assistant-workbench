package com.example.demo.service.rag;

import java.time.Instant;

public record StoredRagProjectSummary(
    String key,
    String name,
    String description,
    boolean active,
    boolean isDefault,
    int sortOrder,
    long materialCount,
    long readyMaterialCount,
    Instant updatedAt
) {
}
