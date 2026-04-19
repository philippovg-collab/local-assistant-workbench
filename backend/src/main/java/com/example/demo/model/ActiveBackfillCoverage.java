package com.example.demo.model;

public record ActiveBackfillCoverage(
    int activeTotal,
    int structuredProfileActive,
    double ratio,
    int pendingBackfill,
    int partialReadyActive
) {
    public static ActiveBackfillCoverage empty() {
        return new ActiveBackfillCoverage(0, 0, 0.0d, 0, 0);
    }
}
