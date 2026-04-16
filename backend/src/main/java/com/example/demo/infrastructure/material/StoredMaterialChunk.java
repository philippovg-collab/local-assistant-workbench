package com.example.demo.infrastructure.material;

import java.util.List;

public record StoredMaterialChunk(
    int index,
    String text,
    List<String> tokens,
    Integer page,
    String extractor,
    Boolean ocrUsed
) {
}
