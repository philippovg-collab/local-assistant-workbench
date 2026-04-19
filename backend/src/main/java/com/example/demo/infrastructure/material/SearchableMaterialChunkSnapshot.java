package com.example.demo.infrastructure.material;

public record SearchableMaterialChunkSnapshot(
    int chunkIndex,
    String chunkText,
    Integer page,
    String extractor,
    boolean ocrUsed,
    DocumentBlockType chunkType,
    java.util.List<String> sectionPath,
    java.util.List<String> headingTrail,
    String tableId,
    String slideId,
    DocumentBlockConfidence parserConfidence
) {

    public SearchableMaterialChunkSnapshot {
        extractor = extractor == null || extractor.isBlank() ? "legacy" : extractor.trim();
        chunkType = chunkType == null ? DocumentBlockType.NARRATIVE : chunkType;
        sectionPath = sectionPath == null ? java.util.List.of() : java.util.List.copyOf(sectionPath);
        headingTrail = headingTrail == null ? java.util.List.of() : java.util.List.copyOf(headingTrail);
        parserConfidence = parserConfidence == null
            ? ocrUsed ? DocumentBlockConfidence.LOW : DocumentBlockConfidence.HIGH
            : parserConfidence;
    }

    public SearchableMaterialChunkSnapshot(
        int chunkIndex,
        String chunkText,
        Integer page,
        String extractor,
        boolean ocrUsed
    ) {
        this(
            chunkIndex,
            chunkText,
            page,
            extractor,
            ocrUsed,
            DocumentBlockType.NARRATIVE,
            java.util.List.of(),
            java.util.List.of(),
            null,
            null,
            ocrUsed ? DocumentBlockConfidence.LOW : DocumentBlockConfidence.HIGH
        );
    }
}
