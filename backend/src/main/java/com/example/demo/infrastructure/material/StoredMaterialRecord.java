package com.example.demo.infrastructure.material;

import java.time.Instant;
import java.util.List;

public record StoredMaterialRecord(
    String id,
    String title,
    String sourceType,
    String originalFileName,
    String mediaType,
    String content,
    String normalizedContent,
    String contentHash,
    String sourceKey,
    String extractor,
    Boolean ocrUsed,
    Integer pageCount,
    List<StoredMaterialChunk> chunks,
    Instant createdAt,
    Instant updatedAt
) {
}
