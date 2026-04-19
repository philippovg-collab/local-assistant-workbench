package com.example.demo.model;

import java.time.Instant;

public record InstructionSummary(
    String id,
    String title,
    InstructionCategory category,
    InstructionScopeLevel scopeLevel,
    String scopeTargetId,
    int revision,
    boolean active,
    Instant createdAt,
    Instant updatedAt,
    String preview
) {
    public InstructionSummary(
        String id,
        String title,
        InstructionCategory category,
        Instant createdAt,
        Instant updatedAt,
        String preview
    ) {
        this(
            id,
            title,
            category,
            InstructionScopeLevel.CHAT_SCENARIO,
            null,
            1,
            true,
            createdAt,
            updatedAt,
            preview
        );
    }

    public InstructionSummary(
        String id,
        String title,
        InstructionCategory category,
        InstructionScopeLevel scopeLevel,
        String scopeTargetId,
        int revision,
        boolean active,
        Instant createdAt,
        String preview
    ) {
        this(id, title, category, scopeLevel, scopeTargetId, revision, active, createdAt, createdAt, preview);
    }
}
