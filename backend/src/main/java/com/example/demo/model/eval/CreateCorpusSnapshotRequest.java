package com.example.demo.model.eval;

import java.time.Instant;
import java.util.Map;

public record CreateCorpusSnapshotRequest(
    String snapshotKey,
    Instant referenceInstant,
    Boolean includeSuperseded,
    Map<String, Object> metadata
) {
    public CreateCorpusSnapshotRequest {
        metadata = metadata == null ? Map.of() : metadata;
    }

    public boolean shouldIncludeSuperseded() {
        return includeSuperseded == null || includeSuperseded;
    }
}
