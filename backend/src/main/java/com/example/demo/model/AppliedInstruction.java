package com.example.demo.model;

public record AppliedInstruction(
    String id,
    String title,
    InstructionCategory category,
    InstructionScopeLevel scopeLevel,
    String scopeTargetId,
    int revision
) {
    public AppliedInstruction(
        String id,
        String title,
        InstructionCategory category
    ) {
        this(id, title, category, null, null, 0);
    }
}
