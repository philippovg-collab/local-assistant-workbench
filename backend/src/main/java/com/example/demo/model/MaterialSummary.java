package com.example.demo.model;

import java.time.Instant;

public record MaterialSummary(
    String id,
    String title,
    String sourceType,
    String originalFileName,
    MaterialIndexingStatus status,
    MaterialVersionState versionState,
    String statusReasonCode,
    String statusReasonMessage,
    Instant createdAt,
    Instant updatedAt,
    int indexingAttempts,
    Instant nextRetryAt,
    int contentLength,
    String preview,
    MaterialMetadataSnapshot metadata,
    MaterialEnrichmentStatus enrichmentStatus
) {
    public MaterialSummary {
        metadata = metadata == null ? MaterialMetadataSnapshot.empty() : metadata;
        enrichmentStatus = enrichmentStatus == null ? MaterialEnrichmentStatus.notRequested() : enrichmentStatus;
    }

    public MaterialSummary(
        String id,
        String title,
        String sourceType,
        String originalFileName,
        MaterialIndexingStatus status,
        MaterialVersionState versionState,
        String statusReasonCode,
        String statusReasonMessage,
        Instant createdAt,
        Instant updatedAt,
        int indexingAttempts,
        Instant nextRetryAt,
        int contentLength,
        String preview,
        MaterialMetadataSnapshot metadata
    ) {
        this(
            id,
            title,
            sourceType,
            originalFileName,
            status,
            versionState,
            statusReasonCode,
            statusReasonMessage,
            createdAt,
            updatedAt,
            indexingAttempts,
            nextRetryAt,
            contentLength,
            preview,
            metadata,
            MaterialEnrichmentStatus.notRequested()
        );
    }

    public MaterialSummary(
        String id,
        String title,
        String sourceType,
        String originalFileName,
        MaterialIndexingStatus status,
        MaterialVersionState versionState,
        String statusReasonCode,
        String statusReasonMessage,
        Instant createdAt,
        int contentLength,
        String preview
    ) {
        this(
            id,
            title,
            sourceType,
            originalFileName,
            status,
            versionState,
            statusReasonCode,
            statusReasonMessage,
            createdAt,
            createdAt,
            0,
            null,
            contentLength,
            preview,
            MaterialMetadataSnapshot.empty(),
            MaterialEnrichmentStatus.notRequested()
        );
    }

    public MaterialSummary withEnrichmentStatus(MaterialEnrichmentStatus updatedEnrichmentStatus) {
        return new MaterialSummary(
            id,
            title,
            sourceType,
            originalFileName,
            status,
            versionState,
            statusReasonCode,
            statusReasonMessage,
            createdAt,
            updatedAt,
            indexingAttempts,
            nextRetryAt,
            contentLength,
            preview,
            metadata,
            updatedEnrichmentStatus
        );
    }
}
