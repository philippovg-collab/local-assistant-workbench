package com.example.demo.model.eval;

import java.util.List;

public record RetrievalEvalPreviewResponse(
    String query,
    RetrievalEvalCaseRef caseRef,
    RetrievalEvalSnapshotRef snapshotRef,
    String configHash,
    RetrievalEvalReproducibilityStatus reproducibilityStatus,
    RetrievalEvalTrace trace,
    RetrievalEvalStageTrace stages,
    List<RetrievalEvalMetric> metrics,
    List<String> warnings
) {
    public RetrievalEvalPreviewResponse {
        metrics = metrics == null ? List.of() : List.copyOf(metrics);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
