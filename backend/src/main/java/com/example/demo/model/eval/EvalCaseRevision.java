package com.example.demo.model.eval;

import java.time.Instant;

public record EvalCaseRevision(
    String id,
    EvalCase caseSnapshot,
    String contentHash,
    String createdBy,
    Instant createdAt
) {
}
