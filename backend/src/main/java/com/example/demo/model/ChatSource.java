package com.example.demo.model;

public record ChatSource(
    String materialId,
    String title,
    String excerpt,
    int score,
    Integer page,
    String extractor,
    boolean ocrUsed
) {
}
