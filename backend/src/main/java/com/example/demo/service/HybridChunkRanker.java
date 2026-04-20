package com.example.demo.service;

import com.example.demo.service.material.MaterialChunkSearchMatch;

import com.example.demo.model.ChunkScoreBreakdown;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class HybridChunkRanker {

    private static final int RRF_K = 60;
    private static final double MAX_FUSED_SCORE = (1.0d / (RRF_K + 1)) * 2.0d;

    public List<RankedChunk> fuse(
        List<MaterialChunkSearchMatch> semanticMatches,
        List<MaterialChunkSearchMatch> lexicalMatches,
        int limit
    ) {
        if (limit <= 0) {
            return List.of();
        }

        Map<String, CandidateAccumulator> byKey = new LinkedHashMap<>();
        accumulate(byKey, semanticMatches, true);
        accumulate(byKey, lexicalMatches, false);

        return byKey.values().stream()
            .sorted(Comparator
                .comparingDouble(CandidateAccumulator::fusedScore)
                .reversed()
                .thenComparingInt(CandidateAccumulator::lexicalRankOrMax)
                .thenComparingInt(candidate -> candidate.match().page() == null ? Integer.MAX_VALUE : candidate.match().page())
                .thenComparingInt(candidate -> candidate.match().chunkIndex()))
            .limit(limit)
            .map(candidate -> {
                int score = normalizeScore(candidate.fusedScore());
                return new RankedChunk(
                    candidate.match(),
                    score,
                    candidate.semanticRank,
                    candidate.lexicalRank,
                    null
                );
            })
            .toList();
    }

    private void accumulate(
        Map<String, CandidateAccumulator> byKey,
        List<MaterialChunkSearchMatch> matches,
        boolean semantic
    ) {
        for (int index = 0; index < matches.size(); index++) {
            MaterialChunkSearchMatch match = matches.get(index);
            String key = keyOf(match);
            CandidateAccumulator candidate = byKey.computeIfAbsent(key, ignored -> new CandidateAccumulator(match));
            candidate.fusedScore += 1.0d / (RRF_K + index + 1);
            if (semantic) {
                candidate.semanticRank = index + 1;
            } else {
                candidate.lexicalRank = index + 1;
            }
        }
    }

    private int normalizeScore(double fusedScore) {
        int normalized = (int) Math.round((fusedScore / MAX_FUSED_SCORE) * 100.0d);
        return Math.max(0, Math.min(100, normalized));
    }

    private String keyOf(MaterialChunkSearchMatch match) {
        return match.materialId() + ":" + match.chunkIndex();
    }

    public record RankedChunk(
        MaterialChunkSearchMatch match,
        int score,
        Integer semanticRank,
        Integer lexicalRank,
        ChunkScoreBreakdown scoreBreakdown
    ) {
        public RankedChunk(MaterialChunkSearchMatch match, int score) {
            this(match, score, null, null, null);
        }

        public RankedChunk withScoreAndBreakdown(int updatedScore, ChunkScoreBreakdown updatedBreakdown) {
            return new RankedChunk(
                match,
                updatedScore,
                semanticRank,
                lexicalRank,
                updatedBreakdown
            );
        }
    }

    private static final class CandidateAccumulator {

        private final MaterialChunkSearchMatch match;
        private double fusedScore = 0.0d;
        private Integer semanticRank;
        private Integer lexicalRank;

        private CandidateAccumulator(MaterialChunkSearchMatch match) {
            this.match = match;
        }

        private MaterialChunkSearchMatch match() {
            return match;
        }

        private double fusedScore() {
            return fusedScore;
        }

        private int lexicalRankOrMax() {
            return lexicalRank == null ? Integer.MAX_VALUE : lexicalRank;
        }
    }
}
