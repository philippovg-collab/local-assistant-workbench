package com.example.demo.infrastructure.material;

public record MaterialChunkSearchMatch(
    String materialId,
    int chunkIndex,
    String title,
    String chunkText,
    Integer page,
    String extractor,
    boolean ocrUsed,
    Double semanticDistance,
    Double lexicalScore
) {
}
