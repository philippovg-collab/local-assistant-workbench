package com.example.demo.model;

import java.util.List;

public record MaterialSearchDebug(
    RetrievalFilters appliedFilters,
    RetrievalFilters manualFilters,
    RetrievalFilters effectiveFilters,
    RetrievalQueryHints queryHints,
    int semanticCandidateCount,
    int lexicalCandidateCount,
    int rerankCandidateCount,
    int rankedHitCount,
    String configuredLexicalMode,
    String effectiveLexicalProvider,
    boolean fallbackApplied,
    String fallbackReasonCode,
    String supportVerdict,
    boolean rerankerApplied,
    String rerankerProfile,
    String relevanceProfile,
    QualityLayerFlags activeRolloutFlags,
    List<String> appliedCapabilities
) {
    public MaterialSearchDebug(
        RetrievalFilters appliedFilters,
        RetrievalFilters manualFilters,
        RetrievalFilters effectiveFilters,
        RetrievalQueryHints queryHints,
        int semanticCandidateCount,
        int lexicalCandidateCount,
        int rerankCandidateCount,
        int rankedHitCount,
        String configuredLexicalMode,
        String effectiveLexicalProvider,
        boolean fallbackApplied,
        String fallbackReasonCode,
        String supportVerdict,
        boolean rerankerApplied,
        String rerankerProfile,
        String relevanceProfile
    ) {
        this(
            appliedFilters,
            manualFilters,
            effectiveFilters,
            queryHints,
            semanticCandidateCount,
            lexicalCandidateCount,
            rerankCandidateCount,
            rankedHitCount,
            configuredLexicalMode,
            effectiveLexicalProvider,
            fallbackApplied,
            fallbackReasonCode,
            supportVerdict,
            rerankerApplied,
            rerankerProfile,
            relevanceProfile,
            QualityLayerFlags.none(),
            List.of()
        );
    }

    public MaterialSearchDebug(
        RetrievalFilters appliedFilters,
        int semanticCandidateCount,
        int lexicalCandidateCount,
        int rankedHitCount,
        String configuredLexicalMode,
        String effectiveLexicalProvider,
        boolean fallbackApplied,
        String fallbackReasonCode,
        String supportVerdict
    ) {
        this(
            appliedFilters,
            RetrievalFilters.empty(),
            appliedFilters,
            RetrievalQueryHints.empty(),
            semanticCandidateCount,
            lexicalCandidateCount,
            rankedHitCount,
            rankedHitCount,
            configuredLexicalMode,
            effectiveLexicalProvider,
            fallbackApplied,
            fallbackReasonCode,
            supportVerdict,
            false,
            null,
            null,
            QualityLayerFlags.none(),
            List.of()
        );
    }

    public MaterialSearchDebug {
        appliedFilters = appliedFilters == null ? RetrievalFilters.empty() : appliedFilters;
        manualFilters = manualFilters == null ? RetrievalFilters.empty() : manualFilters;
        effectiveFilters = effectiveFilters == null ? RetrievalFilters.empty() : effectiveFilters;
        queryHints = queryHints == null ? RetrievalQueryHints.empty() : queryHints;
        supportVerdict = supportVerdict == null ? "none" : supportVerdict;
        activeRolloutFlags = activeRolloutFlags == null ? QualityLayerFlags.none() : activeRolloutFlags;
        appliedCapabilities = appliedCapabilities == null ? List.of() : List.copyOf(appliedCapabilities);
    }
}
