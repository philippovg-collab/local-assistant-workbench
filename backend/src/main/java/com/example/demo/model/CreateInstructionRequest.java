package com.example.demo.model;

public record CreateInstructionRequest(
    String title,
    String category,
    String content,
    InstructionScopeLevel scopeLevel,
    String scopeTargetId,
    Boolean active
) {
    public CreateInstructionRequest(String title, String category, String content) {
        this(title, category, content, InstructionScopeLevel.CHAT_SCENARIO, null, Boolean.TRUE);
    }
}
