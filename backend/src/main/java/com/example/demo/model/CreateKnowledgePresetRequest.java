package com.example.demo.model;

public record CreateKnowledgePresetRequest(
    String name,
    String description,
    KnowledgeScope scope,
    Boolean active
) {
}
