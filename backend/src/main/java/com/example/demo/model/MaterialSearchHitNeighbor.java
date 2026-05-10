package com.example.demo.model;

import com.example.demo.model.DocumentBlockType;

public record MaterialSearchHitNeighbor(
    String chunkId,
    int chunkIndex,
    String chunkText,
    Integer page,
    DocumentBlockType chunkType
) {
}
