package com.example.demo.model;

import java.time.Instant;
import java.util.List;

public record ChatAuditRunDetail(
    String id,
    ChatMode mode,
    String model,
    String prompt,
    String answer,
    String contextStatus,
    AnswerMode answerMode,
    Instant createdAt,
    List<InstructionTraceEntry> instructionTrace,
    KnowledgeScopeResolved knowledgeScopeResolved,
    RetrievalTrace retrievalTrace,
    List<ChatSource> sources,
    String status,
    String failureStage,
    String failureCode,
    String failureMessage,
    Instant completedAt,
    Instant failedAt,
    Long latencyMsTotal
) {
    public ChatAuditRunDetail(
        String id,
        ChatMode mode,
        String model,
        String prompt,
        String answer,
        String contextStatus,
        AnswerMode answerMode,
        Instant createdAt,
        List<InstructionTraceEntry> instructionTrace,
        KnowledgeScopeResolved knowledgeScopeResolved,
        RetrievalTrace retrievalTrace,
        List<ChatSource> sources
    ) {
        this(
            id,
            mode,
            model,
            prompt,
            answer,
            contextStatus,
            answerMode,
            createdAt,
            instructionTrace,
            knowledgeScopeResolved,
            retrievalTrace,
            sources,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }

    public ChatAuditRunDetail {
        instructionTrace = instructionTrace == null ? List.of() : List.copyOf(instructionTrace);
        knowledgeScopeResolved = knowledgeScopeResolved == null ? KnowledgeScopeResolved.empty() : knowledgeScopeResolved;
        sources = sources == null ? List.of() : List.copyOf(sources);
    }
}
