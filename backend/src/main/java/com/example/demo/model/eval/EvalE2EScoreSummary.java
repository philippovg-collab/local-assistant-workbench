package com.example.demo.model.eval;

import java.util.Map;

public record EvalE2EScoreSummary(
    boolean passed,
    int scoredMetricCount,
    int notScorableMetricCount,
    Map<String, Double> metrics,
    Map<String, Object> details
) {
    public EvalE2EScoreSummary {
        metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}
