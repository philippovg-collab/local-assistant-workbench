package com.example.demo.infrastructure.material;

import com.example.demo.model.MaterialIndexingStatus;
import com.example.demo.model.MaterialVersionState;
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
    MaterialIndexingStatus status,
    MaterialVersionState versionState,
    String statusReasonCode,
    String statusReasonMessage,
    Instant createdAt,
    Instant updatedAt,
    int indexingAttempts,
    Instant nextRetryAt,
    String supersededByMaterialId,
    String supersedeReason
) {
    public StoredMaterialRecord(
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
        MaterialIndexingStatus status,
        MaterialVersionState versionState,
        String statusReasonCode,
        String statusReasonMessage,
        Instant createdAt,
        Instant updatedAt
    ) {
        this(
            id,
            title,
            sourceType,
            originalFileName,
            mediaType,
            content,
            normalizedContent,
            contentHash,
            sourceKey,
            extractor,
            ocrUsed,
            pageCount,
            chunks,
            status,
            versionState,
            statusReasonCode,
            statusReasonMessage,
            createdAt,
            updatedAt,
            0,
            status == MaterialIndexingStatus.PENDING ? updatedAt : null,
            null,
            null
        );
    }
}
