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
    SavedKnowledgeFilterKind kind,
    Boolean active
) {
    public CreateKnowledgePresetRequest(
        String name,
        String description,
        KnowledgeScope scope,
        Boolean active
    ) {
        this(name, description, scope, null, active);
    }
}
