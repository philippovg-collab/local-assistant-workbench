package com.example.demo.model;

import java.util.List;

public record RetrievalDebug(
    RetrievalQueryHints queryHints,
    RetrievalFilters manualFilters,
    RetrievalFilters effectiveFilters,
    int semanticCandidateCount,
    int lexicalCandidateCount,
    int rerankCandidateCount,
    int finalChunkCount,
    String supportVerdict,
    String relevanceProfile,
    QualityLayerFlags activeRolloutFlags,
    List<String> appliedCapabilities
) {
    public RetrievalDebug(
        RetrievalQueryHints queryHints,
        RetrievalFilters manualFilters,
        RetrievalFilters effectiveFilters,
        int semanticCandidateCount,
        int lexicalCandidateCount,
        int rerankCandidateCount,
        int finalChunkCount,
        String supportVerdict,
        String relevanceProfile
    ) {
        this(
            queryHints,
            manualFilters,
            effectiveFilters,
            semanticCandidateCount,
            lexicalCandidateCount,
            rerankCandidateCount,
            finalChunkCount,
            supportVerdict,
            relevanceProfile,
            QualityLayerFlags.none(),
            List.of()
        );
    }

    public RetrievalDebug {
        queryHints = queryHints == null ? RetrievalQueryHints.empty() : queryHints;
        manualFilters = manualFilters == null ? RetrievalFilters.empty() : manualFilters;
        effectiveFilters = effectiveFilters == null ? RetrievalFilters.empty() : effectiveFilters;
        supportVerdict = supportVerdict == null ? "none" : supportVerdict;
        activeRolloutFlags = activeRolloutFlags == null ? QualityLayerFlags.none() : activeRolloutFlags;
        appliedCapabilities = appliedCapabilities == null ? List.of() : List.copyOf(appliedCapabilities);
    }
}
