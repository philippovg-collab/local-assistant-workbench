package com.example.demo.service;

import com.example.demo.service.material.MaterialChunkSearchMatch;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

final class RetrievalTraceBuilder {

    private RetrievalTraceBuilder() {
    }

    static String supportVerdictOf(
        List<MaterialChunkSearchMatch> semanticMatches,
        List<MaterialChunkSearchMatch> lexicalMatches,
        List<HybridChunkRanker.RankedChunk> rankedMatches
    ) {
        if (rankedMatches == null || rankedMatches.isEmpty()) {
            return "none";
        }

        Set<String> semanticKeys = semanticMatches.stream().map(RetrievalTraceBuilder::chunkKeyOf).collect(Collectors.toSet());
        Set<String> lexicalKeys = lexicalMatches.stream().map(RetrievalTraceBuilder::chunkKeyOf).collect(Collectors.toSet());
        HybridChunkRanker.RankedChunk topMatch = rankedMatches.getFirst();
        String topKey = chunkKeyOf(topMatch.match());
        boolean corroboratedByBothSearches = semanticKeys.contains(topKey) && lexicalKeys.contains(topKey);

        if (corroboratedByBothSearches || topMatch.score() >= 70 || (rankedMatches.size() >= 2 && topMatch.score() >= 45)) {
            return "sufficient";
        }
        return "weak";
    }

    static String chunkKeyOf(MaterialChunkSearchMatch match) {
        return match.materialId() + ":" + match.chunkIndex();
    }
}
