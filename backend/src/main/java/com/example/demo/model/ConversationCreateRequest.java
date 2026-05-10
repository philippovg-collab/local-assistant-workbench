package com.example.demo.model;

import jakarta.validation.constraints.Size;

public record ConversationCreateRequest(
    @Size(max = 128)
    String workspaceKey,
    @Size(max = 160)
    String title,
    ChatMode mode,
    @Size(max = 128)
    String defaultModel,
    AnswerMode defaultAnswerMode
) {
}
