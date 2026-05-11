package com.example.demo.model.eval;

import java.time.Instant;
import java.util.Map;

public record EvalExecutionConfig(
    String gitCommitSha,
    String datasetId,
    String datasetVersion,
    String corpusSnapshotId,
    Instant referenceInstant,
    String materialSetHash,
    String searchStateHash,
    String configHash,
    String promptVersion,
    String judgePromptVersion,
    Map<String, Object> providerPins,
    Map<String, Object> retrievalLimits,
    Map<String, Object> rolloutFlags,
    Map<String, Object> revisionPins,
    Map<String, Object> options
) {
    public EvalExecutionConfig {
        providerPins = providerPins == null ? Map.of() : providerPins;
        retrievalLimits = retrievalLimits == null ? Map.of() : retrievalLimits;
        rolloutFlags = rolloutFlags == null ? Map.of() : rolloutFlags;
        revisionPins = revisionPins == null ? Map.of() : revisionPins;
        options = options == null ? Map.of() : options;
    }

    public EvalExecutionConfig(
        String gitCommitSha,
        String datasetVersion,
        String corpusSnapshotId,
        String configHash,
        String promptVersion,
        String judgePromptVersion,
        Instant referenceTime,
        Map<String, Object> options
    ) {
        this(
            gitCommitSha,
            null,
            datasetVersion,
            corpusSnapshotId,
            referenceTime,
            null,
            null,
            configHash,
            promptVersion,
            judgePromptVersion,
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            options
        );
    }

    public static EvalExecutionConfig empty() {
        return new EvalExecutionConfig(null, null, null, null, null, null, null, null, null, null, Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }

    public Instant referenceTime() {
        return referenceInstant;
    }
}
