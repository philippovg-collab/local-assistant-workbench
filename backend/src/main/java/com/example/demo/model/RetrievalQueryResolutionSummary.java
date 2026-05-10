package com.example.demo.model;

public record RetrievalQueryResolutionSummary(
    RetrievalQueryResolutionDecision decision,
    Double confidence,
    Boolean degraded,
    String degradedReason
) {
}
