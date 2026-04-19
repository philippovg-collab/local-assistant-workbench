package com.example.demo.model;

public record ChunkScoreBreakdown(
    int baseRrf,
    int semanticRankBonus,
    int lexicalRankBonus,
    int identifierBonus,
    int headingBonus,
    int metadataBonus,
    int sourceTrustBoost,
    int appendixPenalty,
    int boilerplatePenalty,
    int lowConfidencePenalty,
    int finalScore
) {
}
