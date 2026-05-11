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
    List<MaterialChunkDetail> chunks,
    String sourceKey,
    Integer lineageVersion,
    MaterialLineageOverrideInfo lineageOverride,
    MaterialEnrichmentStatus enrichmentStatus
) {
    public MaterialDetail {
        metadata = metadata == null ? MaterialMetadataSnapshot.empty() : metadata;
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
        enrichmentStatus = enrichmentStatus == null ? MaterialEnrichmentStatus.notRequested() : enrichmentStatus;
    }

    public MaterialDetail(
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
        this(
            id,
            title,
            sourceType,
            originalFileName,
            mediaType,
            content,
            status,
            versionState,
            createdAt,
            updatedAt,
            metadata,
            chunks,
            null,
            null,
            null,
            MaterialEnrichmentStatus.notRequested()
        );
    }

    public MaterialDetail(
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
        List<MaterialChunkDetail> chunks,
        MaterialEnrichmentStatus enrichmentStatus
    ) {
        this(
            id,
            title,
            sourceType,
            originalFileName,
            mediaType,
            content,
            status,
            versionState,
            createdAt,
            updatedAt,
            metadata,
            chunks,
            null,
            null,
            null,
            enrichmentStatus
        );
    }
}
