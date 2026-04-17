package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.infrastructure.material.MaterialChunkSearchMatch;
import java.util.List;
import org.junit.jupiter.api.Test;

class HybridChunkRankerTest {

    private final HybridChunkRanker ranker = new HybridChunkRanker();

    @Test
    void fusesSemanticAndLexicalResultsAndNormalizesScores() {
        MaterialChunkSearchMatch premium = new MaterialChunkSearchMatch(
            "material-1",
            0,
            "Pricing FAQ",
            "Тариф Премиум стоит 12000 тенге.",
            1,
            "direct-text",
            false,
            0.12d,
            0.9d
        );
        MaterialChunkSearchMatch support = new MaterialChunkSearchMatch(
            "material-2",
            0,
            "Support FAQ",
            "Премиум включает приоритетную поддержку.",
            2,
            "direct-text",
            false,
            0.22d,
            null
        );

        List<HybridChunkRanker.RankedChunk> ranked = ranker.fuse(
            List.of(premium, support),
            List.of(premium),
            4
        );

        assertEquals(2, ranked.size());
        assertEquals("material-1", ranked.getFirst().match().materialId());
        assertTrue(ranked.getFirst().score() > ranked.get(1).score());
        assertTrue(ranked.getFirst().score() <= 100);
        assertTrue(ranked.get(1).score() >= 0);
    }
}
