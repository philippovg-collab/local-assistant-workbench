package com.example.demo.model;

import java.time.Instant;

public record MaterialLineageVersion(
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
    String supersededByMaterialId,
    String supersedeReason,
    int contentLength,
    String preview
) {
}
