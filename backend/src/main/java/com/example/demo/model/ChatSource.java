package com.example.demo.model;

import com.example.demo.model.DocumentBlockType;

import java.util.List;

public record ChatSource(
    String materialId,
    String chunkId,
    String title,
    String excerpt,
    int score,
    double confidence,
    java.util.List<String> matchedTerms,
    String openSourceUrl,
    Integer chunkIndex,
    Integer page,
    String extractor,
    boolean ocrUsed,
    DocumentBlockType chunkType,
    MaterialMetadataSnapshot metadata,
    Double semanticDistance,
    Double lexicalScore,
    ChunkScoreBreakdown scoreBreakdown
) {
    public ChatSource {
        matchedTerms = matchedTerms == null ? List.of() : List.copyOf(matchedTerms);
        chunkType = chunkType == null ? DocumentBlockType.NARRATIVE : chunkType;
        metadata = metadata == null ? MaterialMetadataSnapshot.empty() : metadata;
    }

    public ChatSource(
        String materialId,
        String title,
        String excerpt,
        int score,
        Integer page,
        String extractor,
        boolean ocrUsed
    ) {
        this(
            materialId,
            null,
            title,
            excerpt,
            score,
            score <= 0 ? 0.0d : Math.min(1.0d, score / 100.0d),
            List.of(),
            null,
            null,
            page,
            extractor,
            ocrUsed,
            DocumentBlockType.NARRATIVE,
            MaterialMetadataSnapshot.empty(),
            null,
            null,
            null
        );
    }

    public ChatSource(
        String materialId,
        String chunkId,
        String title,
        String excerpt,
        int score,
        double confidence,
        java.util.List<String> matchedTerms,
        String openSourceUrl,
        Integer chunkIndex,
        Integer page,
        String extractor,
        boolean ocrUsed,
        Double semanticDistance,
        Double lexicalScore
    ) {
        this(
            materialId,
            chunkId,
            title,
            excerpt,
            score,
            confidence,
            matchedTerms,
            openSourceUrl,
            chunkIndex,
            page,
            extractor,
            ocrUsed,
            DocumentBlockType.NARRATIVE,
            MaterialMetadataSnapshot.empty(),
            semanticDistance,
            lexicalScore,
            null
        );
    }
}
