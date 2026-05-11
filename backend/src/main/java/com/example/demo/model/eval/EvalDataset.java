package com.example.demo.model.eval;

import java.time.Instant;
import java.util.List;

public record EvalDataset(
    String id,
    String datasetKey,
    EvalDatasetKind kind,
    String version,
    EvalLifecycleStatus status,
    String name,
    String description,
    List<String> tags,
    Instant createdAt,
    Instant updatedAt
) {
    public EvalDataset {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
