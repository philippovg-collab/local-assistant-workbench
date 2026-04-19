package com.example.demo.model;

import java.time.Instant;
import java.util.List;

public record ChatRunTraceDetail(
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
    String failureMessage,
    ChatRunRequestSnapshot requestSnapshot,
    PromptPolicySnapshot promptSnapshot,
    RetrievalSummaryTrace retrievalSummary,
    List<LlmCallTrace> llmCalls,
    ChatRunOutputTrace output,
    List<ChatRunEventTrace> events
) {
    public ChatRunTraceDetail {
        llmCalls = llmCalls == null ? List.of() : List.copyOf(llmCalls);
        events = events == null ? List.of() : List.copyOf(events);
    }
}
