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
    List<ChatSource> sources
) {
}
