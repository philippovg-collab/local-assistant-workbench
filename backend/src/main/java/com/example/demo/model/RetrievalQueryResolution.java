package com.example.demo.model;

import java.util.List;

public record RetrievalQueryResolution(
    String originalQuery,
    String queryForRetrieval,
    String resolvedQuery,
    RetrievalQueryResolutionDecision decision,
    Double confidence,
    List<String> markers,
    List<String> referencedRunIds,
    List<RetrievalQueryReferencedSource> referencedSources,
    Boolean degraded,
    String degradedReason
) {
    public RetrievalQueryResolution {
        queryForRetrieval = queryForRetrieval == null ? originalQuery : queryForRetrieval;
        decision = decision == null ? RetrievalQueryResolutionDecision.DISABLED : decision;
        confidence = confidence == null ? 0.0d : confidence;
        markers = markers == null ? List.of() : List.copyOf(markers);
        referencedRunIds = referencedRunIds == null ? List.of() : List.copyOf(referencedRunIds);
        referencedSources = referencedSources == null ? List.of() : List.copyOf(referencedSources);
        degraded = Boolean.TRUE.equals(degraded);
    }

    public static RetrievalQueryResolution disabled(String originalQuery) {
        return original(
            originalQuery,
            RetrievalQueryResolutionDecision.DISABLED,
            0.0d,
            false,
            null
        );
    }

    public static RetrievalQueryResolution original(
        String originalQuery,
        RetrievalQueryResolutionDecision decision,
        double confidence,
        boolean degraded,
        String degradedReason
    ) {
        return new RetrievalQueryResolution(
            originalQuery,
            originalQuery,
            null,
            decision,
            confidence,
            List.of(),
            List.of(),
            List.of(),
            degraded,
            degradedReason
        );
    }

    public static RetrievalQueryResolution errorFallback(String originalQuery, Throwable exception) {
        String reason = exception == null ? "resolver_error" : exception.getClass().getSimpleName();
        return original(
            originalQuery,
            RetrievalQueryResolutionDecision.ERROR_FALLBACK,
            0.0d,
            true,
            reason
        );
    }

    public RetrievalQueryResolution fallbackOriginal() {
        return new RetrievalQueryResolution(
            originalQuery,
            originalQuery,
            resolvedQuery,
            RetrievalQueryResolutionDecision.FALLBACK_ORIGINAL,
            confidence,
            markers,
            referencedRunIds,
            referencedSources,
            false,
            null
        );
    }

    public RetrievalQueryResolution degraded(String degradedReason) {
        return new RetrievalQueryResolution(
            originalQuery,
            queryForRetrieval,
            resolvedQuery,
            decision,
            confidence,
            markers,
            referencedRunIds,
            referencedSources,
            true,
            degradedReason
        );
    }

    public RetrievalQueryResolutionSummary summary() {
        return new RetrievalQueryResolutionSummary(
            decision,
            confidence,
            degraded,
            degradedReason
        );
    }
}
