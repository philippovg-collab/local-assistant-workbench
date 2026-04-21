package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record RagProjectSummary(
    String key,
    String name,
    String description,
    boolean active,
    @JsonProperty("isDefault")
    boolean isDefault,
    int sortOrder,
    int materialCount,
    int readyMaterialCount,
    Instant updatedAt
) {
}
