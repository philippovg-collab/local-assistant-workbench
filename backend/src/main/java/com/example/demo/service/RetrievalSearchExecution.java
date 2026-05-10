package com.example.demo.service;

import com.example.demo.model.QualityLayerFlags;
import com.example.demo.model.RetrievalDebug;
import com.example.demo.model.RetrievalFilters;
import com.example.demo.model.RetrievalQueryHints;
import com.example.demo.model.RetrievalTrace;
import com.example.demo.service.material.MaterialChunkSearchMatch;
import com.example.demo.service.material.StoredMaterialChunk;
import com.example.demo.service.material.StoredMaterialRecord;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

record RetrievalSearchExecution(
    Set<String> queryTokens,
    RetrievalFilters manualFilters,
    RetrievalFilters effectiveFilters,
    RetrievalQueryHints queryHints,
    int materialCount,
    int activeMaterialCount,
    int readyMaterialCount,
    int scopedMaterialCount,
    int scopedActiveMaterialCount,
    int scopedReadyMaterialCount,
    List<MaterialChunkSearchMatch> semanticMatches,
    ProductionLexicalSearchRouter.LexicalSearchResult lexicalSearchResult,
    List<HybridChunkRanker.RankedChunk> rankedMatches,
    int rerankCandidateCount,
    List<RetrievedMaterialChunk> matches,
    Map<String, StoredMaterialRecord> recordsById,
    Map<String, List<StoredMaterialChunk>> chunksByMaterialId,
    RetrievalTrace retrievalTrace,
    RetrievalDebug retrievalDebug,
    RelevanceProfile relevanceProfile,
    QualityLayerFlags activeRolloutFlags,
    List<String> appliedCapabilities,
    List<String> suppressedCapabilities
) {
    RetrievalSearchExecution {
        queryTokens = queryTokens == null ? Set.of() : Set.copyOf(queryTokens);
        manualFilters = manualFilters == null ? RetrievalFilters.empty() : manualFilters;
        effectiveFilters = effectiveFilters == null ? RetrievalFilters.empty() : effectiveFilters;
        queryHints = queryHints == null ? RetrievalQueryHints.empty() : queryHints;
        semanticMatches = semanticMatches == null ? List.of() : List.copyOf(semanticMatches);
        rankedMatches = rankedMatches == null ? List.of() : List.copyOf(rankedMatches);
        matches = matches == null ? List.of() : List.copyOf(matches);
        recordsById = recordsById == null ? Map.of() : Map.copyOf(recordsById);
        chunksByMaterialId = copyChunksByMaterialId(chunksByMaterialId);
        activeRolloutFlags = activeRolloutFlags == null ? QualityLayerFlags.none() : activeRolloutFlags;
        appliedCapabilities = appliedCapabilities == null ? List.of() : List.copyOf(appliedCapabilities);
        suppressedCapabilities = suppressedCapabilities == null ? List.of() : List.copyOf(suppressedCapabilities);
        retrievalDebug = retrievalDebug == null
            ? new RetrievalDebug(
                queryHints,
                manualFilters,
                effectiveFilters,
                semanticMatches.size(),
                lexicalSearchResult == null ? 0 : lexicalSearchResult.matches().size(),
                rerankCandidateCount,
                matches.size(),
                retrievalTrace == null ? "none" : retrievalTrace.supportVerdict(),
                relevanceProfile == null ? RelevanceProfile.LEGACY.propertyValue() : relevanceProfile.propertyValue(),
                activeRolloutFlags,
                appliedCapabilities,
                suppressedCapabilities
            )
            : retrievalDebug;
        relevanceProfile = relevanceProfile == null ? RelevanceProfile.LEGACY : relevanceProfile;
    }

    private static Map<String, List<StoredMaterialChunk>> copyChunksByMaterialId(
        Map<String, List<StoredMaterialChunk>> chunksByMaterialId
    ) {
        if (chunksByMaterialId == null || chunksByMaterialId.isEmpty()) {
            return Map.of();
        }
        Map<String, List<StoredMaterialChunk>> copied = new LinkedHashMap<>();
        chunksByMaterialId.forEach((materialId, chunks) -> copied.put(
            materialId,
            chunks == null ? List.of() : List.copyOf(chunks)
        ));
        return Map.copyOf(copied);
    }

    MaterialRetrievalResult toMaterialRetrievalResult() {
        return new MaterialRetrievalResult(
            materialCount,
            activeMaterialCount,
            readyMaterialCount,
            scopedMaterialCount,
            scopedActiveMaterialCount,
            scopedReadyMaterialCount,
            matches,
            retrievalTrace,
            retrievalDebug
        );
    }
}
