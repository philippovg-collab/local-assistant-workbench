package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;

public record ReferenceWorkspaceRequest(
    @Size(max = 128)
    String key,
    @Size(max = 256)
    String nameRu,
    Boolean active,
    Integer sortOrder,
    @JsonProperty("isDefault")
    Boolean isDefault
) {
}
