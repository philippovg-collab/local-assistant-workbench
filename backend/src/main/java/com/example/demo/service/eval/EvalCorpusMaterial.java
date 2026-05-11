package com.example.demo.service.eval;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record EvalCorpusMaterial(
    String materialId,
    String title,
    String sourceKey,
    String versionState,
    int lineageVersion,
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
    String chunkProfile,
    Map<String, Object> metadata,
    Instant materialCreatedAt,
    Instant materialUpdatedAt,
    List<EvalCorpusChunk> chunks
) {
    public EvalCorpusMaterial {
        metadata = metadata == null ? Map.of() : metadata;
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
    }
}
