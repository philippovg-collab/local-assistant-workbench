package com.example.demo.model.eval;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record EvalRun(
    String id,
    String datasetId,
    String snapshotId,
    EvalRunKind runKind,
    EvalRunStatus status,
    EvalExecutionConfig executionConfig,
    String configHash,
    Instant startedAt,
    Instant completedAt,
    Map<String, Object> summary,
    List<EvalRunItem> items,
    Instant createdAt,
    Instant updatedAt
) {
    public EvalRun {
        runKind = runKind == null ? EvalRunKind.E2E : runKind;
        executionConfig = executionConfig == null ? EvalExecutionConfig.empty() : executionConfig;
        summary = summary == null ? Map.of() : summary;
        items = items == null ? List.of() : List.copyOf(items);
    }

    public EvalRun(
        String id,
        String datasetId,
        String snapshotId,
        EvalRunStatus status,
        EvalExecutionConfig executionConfig,
        String configHash,
        Instant startedAt,
        Instant completedAt,
        Map<String, Object> summary,
        List<EvalRunItem> items,
        Instant createdAt,
        Instant updatedAt
    ) {
        this(
            id,
            datasetId,
            snapshotId,
            EvalRunKind.E2E,
            status,
            executionConfig,
            configHash,
            startedAt,
            completedAt,
            summary,
            items,
            createdAt,
            updatedAt
        );
    }
}
