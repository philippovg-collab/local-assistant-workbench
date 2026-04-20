package com.example.demo.service.material;

public record ExtractedDocumentSegment(
    String text,
    Integer page,
    String extractor,
    boolean ocrUsed
) {
}
