package com.example.demo.service;

import com.example.demo.model.MaterialSearchDebug;
import com.example.demo.model.MaterialSearchRequest;
import com.example.demo.model.MaterialSearchResponse;
import java.util.ArrayList;
import java.util.List;

final class RetrievalSearchResponseBuilder {

    private final RetrievalResultMapper resultMapper;

    RetrievalSearchResponseBuilder(RetrievalResultMapper resultMapper) {
        this.resultMapper = resultMapper;
    }

    MaterialSearchResponse build(MaterialSearchRequest request, RetrievalSearchExecution execution) {
        MaterialSearchDebug debug = request.debugOrDefault()
            ? new MaterialSearchDebug(
                execution.effectiveFilters(),
                execution.manualFilters(),
                execution.effectiveFilters(),
                execution.queryHints(),
                execution.semanticMatches().size(),
                execution.lexicalSearchResult().matches().size(),
                execution.rerankCandidateCount(),
                execution.rankedMatches().size(),
                execution.lexicalSearchResult().configuredMode().propertyValue(),
                execution.lexicalSearchResult().effectiveProvider().propertyValue(),
                execution.lexicalSearchResult().fallbackApplied(),
                execution.lexicalSearchResult().fallbackReasonCode(),
                execution.retrievalTrace().supportVerdict(),
                execution.relevanceProfile() == RelevanceProfile.HYBRID_RERANK_V1,
                execution.relevanceProfile() == RelevanceProfile.HYBRID_RERANK_V1
                    ? RelevanceProfile.HYBRID_RERANK_V1.propertyValue()
                    : null,
                execution.relevanceProfile().propertyValue(),
                execution.activeRolloutFlags(),
                withCapability(execution.appliedCapabilities(), "search-api-v1"),
                execution.suppressedCapabilities()
            )
            : null;

        return new MaterialSearchResponse(
            request.query().trim(),
            resultMapper.buildSearchHits(execution, request.includeNeighborsOrDefault()),
            debug
        );
    }

    private List<String> withCapability(List<String> appliedCapabilities, String capability) {
        List<String> safeCapabilities = appliedCapabilities == null ? List.of() : appliedCapabilities;
        if (safeCapabilities.contains(capability)) {
            return safeCapabilities;
        }
        List<String> extended = new ArrayList<>(safeCapabilities);
        extended.add(capability);
        return List.copyOf(extended);
    }
}
