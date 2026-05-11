package com.example.demo.model.eval;

public record RetrievalEvalMetric(
    String name,
    Integer k,
    RetrievalEvalMetricStatus status,
    Double value,
    String reason
) {
    public static RetrievalEvalMetric scored(String name, Integer k, double value) {
        return new RetrievalEvalMetric(name, k, RetrievalEvalMetricStatus.SCORED, value, null);
    }

    public static RetrievalEvalMetric notScorable(String name, Integer k, String reason) {
        return new RetrievalEvalMetric(name, k, RetrievalEvalMetricStatus.NOT_SCORABLE, null, reason);
    }
}
