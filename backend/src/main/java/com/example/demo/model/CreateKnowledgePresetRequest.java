package com.example.demo.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

public record CreateKnowledgePresetRequest(
    @Size(max = 160)
    String name,
    @Size(max = 2000)
    String description,
    @Valid
    KnowledgeScope scope,
    Boolean active
) {
}
