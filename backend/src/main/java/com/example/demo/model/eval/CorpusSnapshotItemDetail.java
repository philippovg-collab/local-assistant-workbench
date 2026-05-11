package com.example.demo.model.eval;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

public record CorpusSnapshotItemDetail(
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
    public CorpusSnapshotItemDetail {
        metadata = metadata == null ? Map.of() : metadata;
    }

    public static CorpusSnapshotItemDetail from(CorpusSnapshotItem item) {
        return new CorpusSnapshotItemDetail(
            item.id(),
            item.snapshotId(),
            item.materialId(),
            item.materialVersionId(),
            item.title(),
            item.sourceKey(),
            item.versionState(),
            item.lineageVersion(),
            item.versionLabel(),
            item.indexingStatus(),
            item.documentNumber(),
            item.documentDate(),
            item.documentType(),
            item.documentStatus(),
            item.workspaceKey(),
            item.projectKey(),
            item.languageCode(),
            item.contentHash(),
            item.metadataHash(),
            item.chunkProfile(),
            item.chunkCount(),
            item.chunkSetHash(),
            item.metadata(),
            item.materialCreatedAt(),
            item.materialUpdatedAt(),
            item.createdAt()
        );
    }
}
