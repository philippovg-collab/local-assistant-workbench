package com.example.demo.model;

import java.time.Instant;

public record InstructionDetail(
    String id,
    String title,
    InstructionCategory category,
    String content,
    InstructionScopeLevel scopeLevel,
    String scopeTargetId,
    int revision,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {
    public InstructionDetail(
        String id,
        String title,
        InstructionCategory category,
        String content,
        Instant createdAt,
        Instant updatedAt
    ) {
        this(
            id,
            title,
            category,
            content,
            InstructionScopeLevel.CHAT_SCENARIO,
            null,
            1,
            true,
            createdAt,
            updatedAt
        );
    }

    public InstructionDetail(
        String id,
        String title,
        InstructionCategory category,
        String content,
        InstructionScopeLevel scopeLevel,
        String scopeTargetId,
        int revision,
        boolean active,
        Instant createdAt
    ) {
        this(id, title, category, content, scopeLevel, scopeTargetId, revision, active, createdAt, createdAt);
    }
}
