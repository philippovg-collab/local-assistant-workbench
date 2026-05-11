package com.example.demo.model.eval;

import java.time.Instant;
import java.util.Map;

public record EvalCaseReview(
    String id,
    String caseId,
    Integer caseRevision,
    EvalReviewStatus status,
    String reviewer,
    String note,
    Map<String, Object> metadata,
    Instant createdAt,
    Instant updatedAt
) {
    public EvalCaseReview {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public EvalCaseReview(
        String id,
        String caseId,
        EvalReviewStatus status,
        String reviewer,
        String note,
        Instant createdAt,
        Instant updatedAt
    ) {
        this(id, caseId, null, status, reviewer, note, Map.of(), createdAt, updatedAt);
    }
}
