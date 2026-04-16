package com.example.demo.infrastructure.material;

public record ExtractedDocumentSegment(
    String text,
    Integer page,
    String extractor,
    boolean ocrUsed
) {
}
