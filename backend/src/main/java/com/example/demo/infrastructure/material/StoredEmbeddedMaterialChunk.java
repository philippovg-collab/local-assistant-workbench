package com.example.demo.infrastructure.material;

public record StoredEmbeddedMaterialChunk(
    int index,
    String text,
    Integer page,
    String extractor,
    boolean ocrUsed,
    float[] embedding
) {
}
