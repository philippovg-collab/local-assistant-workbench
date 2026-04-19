package com.example.demo.model;

public record InstructionTraceEntry(
    String instructionId,
    String title,
    InstructionCategory category,
    InstructionScopeLevel scopeLevel,
    String scopeTargetId,
    int revision,
    boolean active,
    boolean temporary,
    String contentPreview
) {
}
