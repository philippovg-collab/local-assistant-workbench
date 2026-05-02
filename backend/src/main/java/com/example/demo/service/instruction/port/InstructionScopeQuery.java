package com.example.demo.service.instruction.port;

import com.example.demo.model.InstructionScopeLevel;

public record InstructionScopeQuery(
    InstructionScopeLevel scopeLevel,
    String scopeTargetId,
    boolean activeOnly
) {
}
