package com.example.demo.model.eval;

import java.time.Instant;
import java.util.Map;

public record EvalRunItem(
    String id,
    String runId,
    String caseId,
    Integer caseRevision,
    String chatRunId,
    String contextAssemblyId,
    EvalRunItemStatus status,
    EvalFailureCode failureCode,
    String failureMessage,
    Map<String, Object> artifact,
    Map<String, Object> scorer,
    Map<String, Object> scoreSummary,
    Instant createdAt,
    Instant updatedAt
) {
    public EvalRunItem {
        artifact = artifact == null ? Map.of() : artifact;
        scorer = scorer == null ? Map.of() : scorer;
        scoreSummary = scoreSummary == null ? Map.of() : scoreSummary;
    }

    public EvalRunItem(
        String id,
        String runId,
        String caseId,
        String chatRunId,
        EvalRunItemStatus status,
        EvalFailureCode failureCode,
        Map<String, Object> artifact,
        Map<String, Object> scorer,
        Instant createdAt,
        Instant updatedAt
    ) {
        this(
            id,
            runId,
            caseId,
            null,
            chatRunId,
            null,
            status,
            failureCode,
            null,
            artifact,
            scorer,
            Map.of(),
            createdAt,
            updatedAt
        );
    }

    public EvalRunItem(
        String id,
        String runId,
        String caseId,
        String chatRunId,
        String contextAssemblyId,
        EvalRunItemStatus status,
        EvalFailureCode failureCode,
        String failureMessage,
        Map<String, Object> artifact,
        Map<String, Object> scorer,
        Map<String, Object> scoreSummary,
        Instant createdAt,
        Instant updatedAt
    ) {
        this(
            id,
            runId,
            caseId,
            null,
            chatRunId,
            contextAssemblyId,
            status,
            failureCode,
            failureMessage,
            artifact,
            scorer,
            scoreSummary,
            createdAt,
            updatedAt
        );
    }
}
