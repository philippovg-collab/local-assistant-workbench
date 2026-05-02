package com.example.demo.service.instruction.port;

import com.example.demo.model.InstructionCategory;
import com.example.demo.model.InstructionScopeLevel;
import java.time.Instant;

public record StoredInstructionRevisionRecord(
    String instructionId,
    int revision,
    String title,
    InstructionCategory category,
    String content,
    String normalizedContent,
    InstructionScopeLevel scopeLevel,
    String scopeTargetId,
    boolean active,
    Integer restoredFromRevision,
    Instant createdAt,
    Instant updatedAt
) {
}
