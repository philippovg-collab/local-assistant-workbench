package com.example.demo.model.eval;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record EvalRunCompare(
    String id,
    String baselineRunId,
    String candidateRunId,
    EvalCompareStatus status,
    EvalCompatibilityStatus compatibilityStatus,
    List<EvalCompatibilityReason> compatibilityReasons,
    String compatibilityReason,
    String baselineDatasetVersion,
    String candidateDatasetVersion,
    String baselineSnapshotId,
    String candidateSnapshotId,
    String baselineMaterialSetHash,
    String candidateMaterialSetHash,
    String baselineSearchStateHash,
    String candidateSearchStateHash,
    String baselineConfigHash,
    String candidateConfigHash,
    Map<String, Object> summary,
    Instant createdAt,
    Instant updatedAt
) {
    public EvalRunCompare {
        compatibilityStatus = compatibilityStatus == null ? compatibilityStatusFrom(status) : compatibilityStatus;
        compatibilityReasons = compatibilityReasons == null ? List.of() : List.copyOf(compatibilityReasons);
        summary = summary == null ? Map.of() : summary;
    }

    public EvalRunCompare(
        String id,
        String baselineRunId,
        String candidateRunId,
        EvalCompareStatus status,
        String compatibilityReason,
        Map<String, Object> summary,
        Instant createdAt,
        Instant updatedAt
    ) {
        this(
            id,
            baselineRunId,
            candidateRunId,
            status,
            compatibilityStatusFrom(status),
            compatibilityReason == null || compatibilityReason.isBlank()
                ? List.of()
                : List.of(new EvalCompatibilityReason("legacy.reason", compatibilityReason, compatibilityStatusFrom(status), Map.of())),
            compatibilityReason,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            summary,
            createdAt,
            updatedAt
        );
    }

    public static EvalCompareStatus compareStatusFrom(EvalCompatibilityStatus compatibilityStatus) {
        if (compatibilityStatus == EvalCompatibilityStatus.COMPATIBLE
            || compatibilityStatus == EvalCompatibilityStatus.WARNING_ONLY) {
            return EvalCompareStatus.COMPATIBLE;
        }
        return EvalCompareStatus.INCOMPATIBLE;
    }

    private static EvalCompatibilityStatus compatibilityStatusFrom(EvalCompareStatus status) {
        return status == EvalCompareStatus.COMPATIBLE
            ? EvalCompatibilityStatus.COMPATIBLE
            : EvalCompatibilityStatus.BLOCKED;
    }
}
