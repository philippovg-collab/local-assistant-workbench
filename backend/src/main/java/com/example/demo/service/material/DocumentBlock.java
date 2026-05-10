package com.example.demo.service.material;

import com.example.demo.model.DocumentBlockConfidence;
import com.example.demo.model.DocumentBlockType;

public record DocumentBlock(
    int index,
    DocumentBlockType type,
    String text,
    Integer page,
    String extractor,
    boolean ocrUsed,
    DocumentBlockConfidence confidence,
    Integer level
) {
    public DocumentBlock {
        type = type == null ? DocumentBlockType.NARRATIVE : type;
        extractor = extractor == null || extractor.isBlank() ? "unknown" : extractor.trim();
        confidence = confidence == null ? DocumentBlockConfidence.HIGH : confidence;
        text = text == null ? "" : text;
    }
}
