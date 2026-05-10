package com.example.demo.service.material;

import com.example.demo.model.DocumentBlockConfidence;
import com.example.demo.model.DocumentBlockType;

import java.util.List;

public record StoredMaterialChunk(
    int index,
    String text,
    List<String> tokens,
    Integer page,
    String extractor,
    Boolean ocrUsed,
    DocumentBlockType chunkType,
    List<String> sectionPath,
    List<String> headingTrail,
    String tableId,
    String slideId,
    DocumentBlockConfidence parserConfidence
) {

    public StoredMaterialChunk {
        tokens = tokens == null ? List.of() : List.copyOf(tokens);
        extractor = extractor == null || extractor.isBlank() ? "legacy" : extractor.trim();
        chunkType = chunkType == null ? DocumentBlockType.NARRATIVE : chunkType;
        sectionPath = sectionPath == null ? List.of() : List.copyOf(sectionPath);
        headingTrail = headingTrail == null ? List.of() : List.copyOf(headingTrail);
        parserConfidence = parserConfidence == null
            ? Boolean.TRUE.equals(ocrUsed) ? DocumentBlockConfidence.LOW : DocumentBlockConfidence.HIGH
            : parserConfidence;
    }

    public StoredMaterialChunk(
        int index,
        String text,
        List<String> tokens,
        Integer page,
        String extractor,
        Boolean ocrUsed
    ) {
        this(
            index,
            text,
            tokens,
            page,
            extractor,
            ocrUsed,
            DocumentBlockType.NARRATIVE,
            List.of(),
            List.of(),
            null,
            null,
            Boolean.TRUE.equals(ocrUsed) ? DocumentBlockConfidence.LOW : DocumentBlockConfidence.HIGH
        );
    }
}
