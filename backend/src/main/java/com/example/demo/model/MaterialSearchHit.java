package com.example.demo.model;

import com.example.demo.infrastructure.material.DocumentBlockType;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MaterialSearchHit(
    String materialId,
    String chunkId,
    String title,
    String chunkText,
    int chunkIndex,
    Integer page,
    DocumentBlockType chunkType,
    int score,
    Double semanticDistance,
    Double lexicalScore,
    List<String> matchedTerms,
    String openSourceUrl,
    MaterialMetadataSnapshot metadata,
    List<MaterialSearchHitNeighbor> neighbors,
    ChunkScoreBreakdown scoreBreakdown
) {

    public MaterialSearchHit(
        String materialId,
        String chunkId,
        String title,
        String chunkText,
        int chunkIndex,
        Integer page,
        DocumentBlockType chunkType,
        int score,
        Double semanticDistance,
        Double lexicalScore,
        List<String> matchedTerms,
        String openSourceUrl,
        MaterialMetadataSnapshot metadata,
        List<MaterialSearchHitNeighbor> neighbors
    ) {
        this(
            materialId,
            chunkId,
            title,
            chunkText,
            chunkIndex,
            page,
            chunkType,
            score,
            semanticDistance,
            lexicalScore,
            matchedTerms,
            openSourceUrl,
            metadata,
            neighbors,
            null
        );
    }

    public MaterialSearchHit {
        matchedTerms = matchedTerms == null ? List.of() : List.copyOf(matchedTerms);
        neighbors = neighbors == null ? null : List.copyOf(neighbors);
        metadata = metadata == null ? MaterialMetadataSnapshot.empty() : metadata;
    }
}
