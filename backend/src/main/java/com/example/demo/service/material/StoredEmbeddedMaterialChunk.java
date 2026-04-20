package com.example.demo.service.material;

public record StoredEmbeddedMaterialChunk(
    int index,
    String text,
    Integer page,
    String extractor,
    boolean ocrUsed,
    float[] embedding,
    DocumentBlockType chunkType,
    java.util.List<String> sectionPath,
    java.util.List<String> headingTrail,
    String tableId,
    String slideId,
    DocumentBlockConfidence parserConfidence
) {

    public StoredEmbeddedMaterialChunk {
        extractor = extractor == null || extractor.isBlank() ? "legacy" : extractor.trim();
        sectionPath = sectionPath == null ? java.util.List.of() : java.util.List.copyOf(sectionPath);
        headingTrail = headingTrail == null ? java.util.List.of() : java.util.List.copyOf(headingTrail);
        chunkType = chunkType == null ? DocumentBlockType.NARRATIVE : chunkType;
        parserConfidence = parserConfidence == null
            ? ocrUsed ? DocumentBlockConfidence.LOW : DocumentBlockConfidence.HIGH
            : parserConfidence;
    }

    public StoredEmbeddedMaterialChunk(
        int index,
        String text,
        Integer page,
        String extractor,
        boolean ocrUsed,
        float[] embedding
    ) {
        this(
            index,
            text,
            page,
            extractor,
            ocrUsed,
            embedding,
            DocumentBlockType.NARRATIVE,
            java.util.List.of(),
            java.util.List.of(),
            null,
            null,
            ocrUsed ? DocumentBlockConfidence.LOW : DocumentBlockConfidence.HIGH
        );
    }
}
