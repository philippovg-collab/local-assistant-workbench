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
    Long latencyMsTotal,
    String workspaceKey
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
            null,
            null
        );
    }

    public ChatAuditRunSummary(
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
        this(
            id,
            mode,
            model,
            answerMode,
            promptPreview,
            answerPreview,
            createdAt,
            status,
            failureStage,
            failureCode,
            failedAt,
            latencyMsTotal,
            null
        );
    }
}
