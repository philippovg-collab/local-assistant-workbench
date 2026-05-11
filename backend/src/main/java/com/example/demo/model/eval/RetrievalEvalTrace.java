package com.example.demo.model.eval;

import com.example.demo.model.QualityLayerFlags;
import com.example.demo.model.RetrievalFilters;
import com.example.demo.model.RetrievalQueryHints;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record RetrievalEvalTrace(
    RetrievalQueryHints queryHints,
    RetrievalFilters manualFilters,
    RetrievalFilters effectiveFilters,
    int semanticCandidateCount,
    int lexicalCandidateCount,
    int fusedCandidateCount,
    int preRerankCandidateCount,
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
    public RetrievalEvalTrace {
        queryHints = queryHints == null ? RetrievalQueryHints.empty() : queryHints;
        manualFilters = manualFilters == null ? RetrievalFilters.empty() : manualFilters;
        effectiveFilters = effectiveFilters == null ? RetrievalFilters.empty() : effectiveFilters;
        activeRolloutFlags = activeRolloutFlags == null ? QualityLayerFlags.none() : activeRolloutFlags;
        appliedCapabilities = appliedCapabilities == null ? List.of() : List.copyOf(appliedCapabilities);
        suppressedCapabilities = suppressedCapabilities == null ? List.of() : List.copyOf(suppressedCapabilities);
    }
}
