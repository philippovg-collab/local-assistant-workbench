package com.example.demo.model;

import jakarta.validation.constraints.Size;

public record CreateInstructionRequest(
    @Size(max = 160)
    String title,
    String category,
    @Size(max = 20000)
    String content,
    InstructionScopeLevel scopeLevel,
    @Size(max = 128)
    String scopeTargetId,
    Boolean active
) {
    public CreateInstructionRequest(String title, String category, String content) {
        this(title, category, content, InstructionScopeLevel.CHAT_SCENARIO, null, Boolean.TRUE);
    }
}
