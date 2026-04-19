package com.example.demo.infrastructure.material;

public record StoredMaterialSegment(
    int index,
    String text,
    Integer page,
    String extractor,
    Boolean ocrUsed
) {
}
