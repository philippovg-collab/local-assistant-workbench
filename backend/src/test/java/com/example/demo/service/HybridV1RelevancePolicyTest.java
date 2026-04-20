package com.example.demo.service;

import com.example.demo.service.material.MaterialChunkSearchMatch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.demo.config.RagProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class HybridV1RelevancePolicyTest {

    private final HybridV1RelevancePolicy policy = new HybridV1RelevancePolicy();
    private final RagProperties ragProperties = new RagProperties();

    @Test
    void acceptsSemanticOnlyChunkWhenFusedScorePassesThreshold() {
        MaterialChunkSearchMatch semanticOnly = semanticOnlyMatch("m-1", 0, 0.41d);

        List<HybridChunkRanker.RankedChunk> filtered = policy.filterRankedMatches(
            List.of(semanticOnly),
            List.of(),
            List.of(new HybridChunkRanker.RankedChunk(semanticOnly, 35)),
            ragProperties
        );

        assertEquals(List.of("m-1:0"), filtered.stream().map(chunk -> chunk.match().materialId() + ":" + chunk.match().chunkIndex()).toList());
    }

    @Test
    void rejectsSemanticOnlyChunkWhenFusedScoreIsTooLow() {
        MaterialChunkSearchMatch semanticOnly = semanticOnlyMatch("m-2", 1, 0.41d);

        List<HybridChunkRanker.RankedChunk> filtered = policy.filterRankedMatches(
            List.of(semanticOnly),
            List.of(),
            List.of(new HybridChunkRanker.RankedChunk(semanticOnly, 34)),
            ragProperties
        );

        assertEquals(List.of(), filtered);
    }

    @Test
    void keepsLexicalHitsEvenWithoutSemanticDistance() {
        MaterialChunkSearchMatch lexicalOnly = new MaterialChunkSearchMatch(
            "m-3",
            2,
            "Pricing FAQ",
            "Премиальный тариф",
            null,
            "direct-text",
            false,
            null,
            1.0d
        );

        List<HybridChunkRanker.RankedChunk> filtered = policy.filterRankedMatches(
            List.of(),
            List.of(lexicalOnly),
            List.of(new HybridChunkRanker.RankedChunk(lexicalOnly, 8)),
            ragProperties
        );

        assertEquals(List.of("m-3:2"), filtered.stream().map(chunk -> chunk.match().materialId() + ":" + chunk.match().chunkIndex()).toList());
    }

    private MaterialChunkSearchMatch semanticOnlyMatch(String materialId, int chunkIndex, double distance) {
        return new MaterialChunkSearchMatch(
            materialId,
            chunkIndex,
            "Pricing FAQ",
            "Priority support is included.",
            null,
            "direct-text",
            false,
            distance,
            null
        );
    }
}
