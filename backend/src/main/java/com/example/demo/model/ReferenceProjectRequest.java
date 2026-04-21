package com.example.demo.model;

import jakarta.validation.constraints.Size;

public record ReferenceProjectRequest(
    @Size(max = 128)
    String key,
    @Size(max = 128)
    String workspaceKey,
    @Size(max = 256)
    String nameRu,
    Boolean active,
    Integer sortOrder
) {
}
