package com.example.demo.service;

import com.example.demo.service.material.MaterialChunkSearchMatch;

import com.example.demo.config.RagProperties;
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
