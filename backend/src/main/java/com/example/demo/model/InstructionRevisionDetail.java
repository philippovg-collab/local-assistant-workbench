package com.example.demo.model;

import java.time.Instant;

public record InstructionRevisionDetail(
    String instructionId,
    int revision,
    String title,
    InstructionCategory category,
    String content,
    InstructionScopeLevel scopeLevel,
    String scopeTargetId,
    boolean active,
    Integer restoredFromRevision,
    Instant createdAt,
    Instant updatedAt
) {
}
