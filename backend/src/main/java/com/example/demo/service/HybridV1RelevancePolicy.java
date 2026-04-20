package com.example.demo.service;

import com.example.demo.service.material.MaterialChunkSearchMatch;

import com.example.demo.config.RagProperties;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class HybridV1RelevancePolicy implements RelevancePolicy {

    private static final int MIN_SEMANTIC_ONLY_SCORE = 35;

    @Override
    public RelevanceProfile profile() {
        return RelevanceProfile.HYBRID_V1;
    }

    @Override
    public List<HybridChunkRanker.RankedChunk> filterRankedMatches(
        List<MaterialChunkSearchMatch> semanticMatches,
        List<MaterialChunkSearchMatch> lexicalMatches,
        List<HybridChunkRanker.RankedChunk> rankedMatches,
        RagProperties ragProperties
    ) {
        Set<String> lexicalChunkKeys = new LinkedHashSet<>();
        lexicalMatches.stream()
            .filter(match -> match.lexicalScore() != null && match.lexicalScore() > 0.0d)
            .map(this::chunkKeyOf)
            .forEach(lexicalChunkKeys::add);

        return rankedMatches.stream()
            .filter(chunk -> lexicalChunkKeys.contains(chunkKeyOf(chunk.match())) || semanticOnlyAccepted(chunk.match(), chunk.score(), ragProperties))
            .toList();
    }

    private boolean semanticOnlyAccepted(MaterialChunkSearchMatch match, int fusedScore, RagProperties ragProperties) {
        return match.semanticDistance() != null
            && match.semanticDistance() <= ragProperties.getMaxSemanticDistance()
            && fusedScore >= MIN_SEMANTIC_ONLY_SCORE;
    }

    private String chunkKeyOf(MaterialChunkSearchMatch match) {
        return match.materialId() + ":" + match.chunkIndex();
    }
}
