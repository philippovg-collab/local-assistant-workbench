package com.example.demo.model;

public record MaterialChunkDetail(
    String chunkId,
    int chunkIndex,
    String text,
    Integer page,
    String extractor,
    boolean ocrUsed
) {
}
