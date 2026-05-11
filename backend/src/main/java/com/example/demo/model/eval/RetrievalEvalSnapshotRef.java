package com.example.demo.model.eval;

import java.time.Instant;

public record RetrievalEvalSnapshotRef(
    String corpusSnapshotId,
    String materialSetHash,
    String searchStateHash,
    Instant referenceInstant
) {
}
