package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record ReferenceWorkspace(
    String key,
    String nameRu,
    boolean active,
    int sortOrder,
    @JsonProperty("isDefault")
    boolean isDefault,
    Instant createdAt,
    Instant updatedAt
) {
}
