package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;

public record RagProjectRequest(
    @Size(max = 128)
    String key,
    @Size(max = 256)
    String name,
    @Size(max = 2000)
    String description,
    Boolean active,
    Integer sortOrder,
    @JsonProperty("isDefault")
    Boolean isDefault
) {
}
