package com.example.demo.model.eval;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record EvalDatasetVersion(
    String id,
    String datasetId,
    String version,
    String datasetHash,
    int caseCount,
    List<Map<String, Object>> caseRevisionRefs,
    String createdBy,
    String note,
    Instant createdAt
) {
    public EvalDatasetVersion {
        caseRevisionRefs = caseRevisionRefs == null ? List.of() : List.copyOf(caseRevisionRefs);
    }
}
