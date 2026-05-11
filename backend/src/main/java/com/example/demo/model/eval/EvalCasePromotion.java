package com.example.demo.model.eval;

import java.time.Instant;
import java.util.Map;

public record EvalCasePromotion(
    String id,
    String sourceCaseId,
    int sourceCaseRevision,
    String targetDatasetId,
    String targetCaseId,
    int targetCaseRevision,
    String targetDatasetVersionId,
    String promotedBy,
    String note,
    Map<String, Object> metadata,
    Instant createdAt
) {
    public EvalCasePromotion {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
