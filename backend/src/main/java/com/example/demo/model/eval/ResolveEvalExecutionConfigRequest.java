package com.example.demo.model.eval;

import java.time.Instant;
import java.util.Map;

public record ResolveEvalExecutionConfigRequest(
    String gitCommitSha,
    String datasetId,
    String datasetVersion,
    String corpusSnapshotId,
    Instant referenceInstant,
    String promptVersion,
    String judgePromptVersion,
    Map<String, Object> options
) {
    public ResolveEvalExecutionConfigRequest {
        options = options == null ? Map.of() : options;
    }
}
