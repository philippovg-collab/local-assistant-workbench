package com.example.demo.service;

import com.example.demo.config.RagProperties;
import com.example.demo.infrastructure.material.MaterialChunkSearchMatch;
import java.util.List;

public interface RelevancePolicy {

    RelevanceProfile profile();

    List<HybridChunkRanker.RankedChunk> filterRankedMatches(
        List<MaterialChunkSearchMatch> semanticMatches,
        List<MaterialChunkSearchMatch> lexicalMatches,
        List<HybridChunkRanker.RankedChunk> rankedMatches,
        RagProperties ragProperties
    );
}
