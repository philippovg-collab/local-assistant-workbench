package com.example.demo.service.instruction.port;

import com.example.demo.model.InstructionCategory;
import com.example.demo.model.InstructionScopeLevel;
import java.time.Instant;

public record StoredInstructionRecord(
    String id,
    String title,
    InstructionCategory category,
    String content,
    String normalizedContent,
    InstructionScopeLevel scopeLevel,
    String scopeTargetId,
    int revision,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {
    public StoredInstructionRecord(
        String id,
        String title,
        InstructionCategory category,
        String content,
        String normalizedContent,
        Instant createdAt,
        Instant updatedAt
    ) {
        this(
            id,
            title,
            category,
            content,
            normalizedContent,
            InstructionScopeLevel.CHAT_SCENARIO,
            null,
            1,
            true,
            createdAt,
            updatedAt
        );
    }
}
