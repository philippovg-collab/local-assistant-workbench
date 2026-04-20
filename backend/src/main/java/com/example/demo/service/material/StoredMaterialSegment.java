package com.example.demo.service.material;

public record StoredMaterialSegment(
    int index,
    String text,
    Integer page,
    String extractor,
    Boolean ocrUsed
) {
}
