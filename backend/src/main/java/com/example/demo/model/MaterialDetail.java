package com.example.demo.model;

import java.time.Instant;
import java.util.List;

public record MaterialDetail(
    String id,
    String title,
    String sourceType,
    String originalFileName,
    String mediaType,
    String content,
    MaterialIndexingStatus status,
    MaterialVersionState versionState,
    Instant createdAt,
    Instant updatedAt,
    MaterialMetadataSnapshot metadata,
    List<MaterialChunkDetail> chunks
) {
}
