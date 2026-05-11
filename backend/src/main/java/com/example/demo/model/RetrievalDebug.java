package com.example.demo.model;

import java.time.Instant;
import java.time.LocalDate;
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
    List<String> appliedCapabilities,
    List<String> suppressedCapabilities,
    Instant referenceInstant,
    LocalDate effectiveDate,
    Instant uploadedAfterInclusive,
    Instant uploadedBeforeExclusive,
    String retrievalConfigHash,
    String lexicalProvider,
    String embeddingModel,
    String chunkProfile
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
            List.of(),
            List.of(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }

    public RetrievalDebug(
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
            activeRolloutFlags,
            appliedCapabilities,
            List.of(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }

    public RetrievalDebug(
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
        List<String> appliedCapabilities,
        List<String> suppressedCapabilities
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
            activeRolloutFlags,
            appliedCapabilities,
            suppressedCapabilities,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }

    public RetrievalDebug {
        queryHints = queryHints == null ? RetrievalQueryHints.empty() : queryHints;
        manualFilters = manualFilters == null ? RetrievalFilters.empty() : manualFilters;
        effectiveFilters = effectiveFilters == null ? RetrievalFilters.empty() : effectiveFilters;
        supportVerdict = supportVerdict == null ? "none" : supportVerdict;
        activeRolloutFlags = activeRolloutFlags == null ? QualityLayerFlags.none() : activeRolloutFlags;
        appliedCapabilities = appliedCapabilities == null ? List.of() : List.copyOf(appliedCapabilities);
        suppressedCapabilities = suppressedCapabilities == null ? List.of() : List.copyOf(suppressedCapabilities);
    }
}
