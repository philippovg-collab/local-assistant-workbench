package com.example.demo.service;

import com.example.demo.service.material.MaterialChunkSearchMatch;

import com.example.demo.config.RagProperties;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class LegacyRelevancePolicy implements RelevancePolicy {

    @Override
    public RelevanceProfile profile() {
        return RelevanceProfile.LEGACY;
    }

    @Override
    public List<HybridChunkRanker.RankedChunk> filterRankedMatches(
        List<MaterialChunkSearchMatch> semanticMatches,
        List<MaterialChunkSearchMatch> lexicalMatches,
        List<HybridChunkRanker.RankedChunk> rankedMatches,
        RagProperties ragProperties
    ) {
        Set<String> relevantChunkKeys = new LinkedHashSet<>();
        semanticMatches.stream()
            .filter(match -> match.semanticDistance() != null && match.semanticDistance() <= ragProperties.getMaxSemanticDistance())
            .map(this::chunkKeyOf)
            .forEach(relevantChunkKeys::add);
        lexicalMatches.stream()
            .filter(match -> match.lexicalScore() != null && match.lexicalScore() > 0.0d)
            .map(this::chunkKeyOf)
            .forEach(relevantChunkKeys::add);

        return rankedMatches.stream()
            .filter(chunk -> relevantChunkKeys.contains(chunkKeyOf(chunk.match())))
            .toList();
    }

    private String chunkKeyOf(MaterialChunkSearchMatch match) {
        return match.materialId() + ":" + match.chunkIndex();
    }
}
