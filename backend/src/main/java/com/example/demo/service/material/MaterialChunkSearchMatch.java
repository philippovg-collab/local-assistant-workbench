package com.example.demo.service.material;

import com.example.demo.model.DocumentBlockType;

public record MaterialChunkSearchMatch(
    String materialId,
    int chunkIndex,
    String title,
    String chunkText,
    Integer page,
    String extractor,
    boolean ocrUsed,
    DocumentBlockType chunkType,
    Double semanticDistance,
    Double lexicalScore
) {
    public MaterialChunkSearchMatch(
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
        this(
            materialId,
            chunkIndex,
            title,
            chunkText,
            page,
            extractor,
            ocrUsed,
            DocumentBlockType.NARRATIVE,
            semanticDistance,
            lexicalScore
        );
    }
}
