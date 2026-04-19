package com.example.demo.model;

import java.time.Instant;

public record ChatAuditRunSummary(
    String id,
    ChatMode mode,
    String model,
    AnswerMode answerMode,
    String promptPreview,
    String answerPreview,
    Instant createdAt
) {
}
