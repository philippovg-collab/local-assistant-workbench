package com.example.demo.model.eval;

import java.time.Instant;
import java.util.List;

public record EvalDatasetSummary(
    String id,
    String datasetKey,
    EvalDatasetKind kind,
    String version,
    EvalLifecycleStatus status,
    String name,
    String description,
    List<String> tags,
    int caseCount,
    Instant createdAt,
    Instant updatedAt
) {
    public EvalDatasetSummary {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
