package com.example.demo.infrastructure.material;

import com.example.demo.service.material.ExtractedDocumentSegment;

import java.util.List;

public record ExtractedDocument(
    List<ExtractedDocumentSegment> segments,
    String extractor,
    boolean ocrUsed,
    Integer pageCount,
    String warningCode,
    String warningMessage
) {
}
