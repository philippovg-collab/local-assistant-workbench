package com.example.demo.model.eval;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record CorpusSnapshot(
    String id,
    String snapshotKey,
    EvalLifecycleStatus status,
    Instant referenceInstant,
    String materialSetHash,
    String searchStateHash,
    String configHash,
    Map<String, Object> manifest,
    int totalItemCount,
    int activeItemCount,
    int supersededItemCount,
    int readyItemCount,
    Map<String, Object> metadata,
    List<CorpusSnapshotItem> items,
    Instant createdAt,
    Instant updatedAt
) {
    public CorpusSnapshot {
        manifest = manifest == null ? Map.of() : manifest;
        metadata = metadata == null ? Map.of() : metadata;
        items = items == null ? List.of() : List.copyOf(items);
    }

    public CorpusSnapshot(
        String id,
        String snapshotKey,
        EvalLifecycleStatus status,
        Instant referenceInstant,
        String materialSetHash,
        String searchStateHash,
        String configHash,
        Map<String, Object> metadata,
        List<CorpusSnapshotItem> items,
        Instant createdAt,
        Instant updatedAt
    ) {
        this(
            id,
            snapshotKey,
            status,
            referenceInstant,
            materialSetHash,
            searchStateHash,
            configHash,
            metadata,
            items == null ? 0 : items.size(),
            items == null ? 0 : (int) items.stream().filter(item -> "ACTIVE".equals(item.versionState())).count(),
            items == null ? 0 : (int) items.stream().filter(item -> "SUPERSEDED".equals(item.versionState())).count(),
            items == null ? 0 : (int) items.stream().filter(item ->
                "READY".equals(item.indexingStatus()) || "PARTIAL_READY".equals(item.indexingStatus())
            ).count(),
            metadata,
            items,
            createdAt,
            updatedAt
        );
    }
}
