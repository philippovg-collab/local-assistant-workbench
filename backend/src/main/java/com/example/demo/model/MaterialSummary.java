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
    MaterialMetadataSnapshot metadata
) {
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
            MaterialMetadataSnapshot.empty()
        );
    }
}
