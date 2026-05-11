package com.example.demo.model.eval;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

public record CorpusSnapshotItem(
    String id,
    String snapshotId,
    String materialId,
    String materialVersionId,
    String title,
    String sourceKey,
    String versionState,
    Integer lineageVersion,
    String versionLabel,
    String indexingStatus,
    String documentNumber,
    LocalDate documentDate,
    String documentType,
    String documentStatus,
    String workspaceKey,
    String projectKey,
    String languageCode,
    String contentHash,
    String metadataHash,
    String chunkProfile,
    Integer chunkCount,
    String chunkSetHash,
    Map<String, Object> metadata,
    Instant materialCreatedAt,
    Instant materialUpdatedAt,
    Instant createdAt
) {
    public CorpusSnapshotItem {
        metadata = metadata == null ? Map.of() : metadata;
    }

    public CorpusSnapshotItem(
        String id,
        String snapshotId,
        String materialId,
        String materialVersionId,
        String title,
        String sourceKey,
        String contentHash,
        Map<String, Object> metadata,
        Instant createdAt
    ) {
        this(
            id,
            snapshotId,
            materialId,
            materialVersionId,
            title,
            sourceKey,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            contentHash,
            null,
            null,
            null,
            null,
            metadata,
            null,
            null,
            createdAt
        );
    }
}
