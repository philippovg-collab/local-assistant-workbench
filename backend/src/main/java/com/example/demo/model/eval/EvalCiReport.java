package com.example.demo.model.eval;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record EvalCiReport(
    String schemaVersion,
    String mode,
    String gateStatus,
    String runId,
    EvalRunKind runKind,
    EvalDatasetKind datasetKind,
    String datasetVersion,
    String snapshotId,
    String configHash,
    Instant generatedAt,
    Map<String, Metric> metrics,
    List<Slice> slices,
    List<Failure> failures,
    List<Regression> worstRegressions,
    Compatibility compatibility,
    String runStatus,
    String mappingHash,
    String caseRevisionRefHash,
    String overallVerdict,
    Map<String, Object> metricSummary,
    Map<String, Long> itemCounts,
    List<ArtifactLink> artifacts
) {
    public EvalCiReport {
        metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
        slices = slices == null ? List.of() : List.copyOf(slices);
        failures = failures == null ? List.of() : List.copyOf(failures);
        worstRegressions = worstRegressions == null ? List.of() : List.copyOf(worstRegressions);
        compatibility = compatibility == null ? Compatibility.compatible() : compatibility;
        metricSummary = metricSummary == null ? Map.of() : Map.copyOf(metricSummary);
        itemCounts = itemCounts == null ? Map.of() : Map.copyOf(itemCounts);
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
    }

    public record Metric(
        double value,
        int count,
        String status,
        String reason
    ) {
    }

    public record Slice(
        String name,
        String severity,
        Map<String, Metric> metrics,
        List<Failure> failures
    ) {
        public Slice {
            metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
            failures = failures == null ? List.of() : List.copyOf(failures);
        }
    }

    public record Failure(
        String code,
        String message,
        String severity,
        String caseId,
        String metric,
        Double value,
        Double threshold
    ) {
    }

    public record Regression(
        String metric,
        String severity,
        String caseId,
        Double baseline,
        Double candidate,
        Double delta,
        String message
    ) {
    }

    public record Compatibility(
        String status,
        List<Map<String, Object>> reasons
    ) {
        public Compatibility {
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
        }

        public static Compatibility compatible() {
            return new Compatibility("COMPATIBLE", List.of());
        }
    }

    public record ArtifactLink(
        String label,
        String path,
        String type,
        boolean required
    ) {
    }
}
