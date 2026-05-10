package com.example.demo.infrastructure.audit;

import com.example.demo.model.AnswerMode;
import com.example.demo.model.ChatMode;
import java.time.Instant;

record PostgresChatRunHeaderRow(
    String id,
    ChatMode mode,
    String status,
    String requestedModel,
    String resolvedModel,
    AnswerMode requestedAnswerMode,
    AnswerMode appliedAnswerMode,
    String contextStatus,
    Instant createdAt,
    Instant completedAt,
    Instant failedAt,
    Long latencyMsTotal,
    String failureStage,
    String failureCode,
    String failureMessage
) {
}
