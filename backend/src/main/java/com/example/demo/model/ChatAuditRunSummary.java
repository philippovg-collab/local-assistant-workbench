package com.example.demo.model;

import java.time.Instant;

public record ChatAuditRunSummary(
    String id,
    ChatMode mode,
    String model,
    AnswerMode answerMode,
    String promptPreview,
    String answerPreview,
    Instant createdAt,
    String status,
    String failureStage,
    String failureCode,
    Instant failedAt,
    Long latencyMsTotal
) {
    public ChatAuditRunSummary(
        String id,
        ChatMode mode,
        String model,
        AnswerMode answerMode,
        String promptPreview,
        String answerPreview,
        Instant createdAt
    ) {
        this(
            id,
            mode,
            model,
            answerMode,
            promptPreview,
            answerPreview,
            createdAt,
            null,
            null,
            null,
            null,
            null
        );
    }
}
