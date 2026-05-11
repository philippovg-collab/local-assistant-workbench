package com.example.demo.service.material;

import com.example.demo.model.MaterialMetadataSnapshot;
import com.example.demo.model.MaterialVersionState;
import java.time.Instant;
import java.util.List;

public record SearchableMaterialSnapshot(
    String materialId,
    boolean searchable,
    String sourceKey,
    MaterialVersionState versionState,
    Integer lineageVersion,
    String title,
    String sourceType,
    String originalFileName,
    String mediaType,
    Instant createdAt,
    Instant updatedAt,
    MaterialMetadataSnapshot metadata,
    List<SearchableMaterialChunkSnapshot> chunks
) {
    public SearchableMaterialSnapshot {
        metadata = metadata == null ? MaterialMetadataSnapshot.empty() : metadata;
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
    }

    public static SearchableMaterialSnapshot notSearchable(String materialId) {
        return new SearchableMaterialSnapshot(
            materialId,
            false,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            MaterialMetadataSnapshot.empty(),
            List.of()
        );
    }

    public SearchableMaterialSnapshot(
        String materialId,
        boolean searchable,
        String sourceKey,
        String title,
        String sourceType,
        String originalFileName,
        String mediaType,
        Instant createdAt,
        Instant updatedAt,
        MaterialMetadataSnapshot metadata,
        List<SearchableMaterialChunkSnapshot> chunks
    ) {
        this(
            materialId,
            searchable,
            sourceKey,
            null,
            null,
            title,
            sourceType,
            originalFileName,
            mediaType,
            createdAt,
            updatedAt,
            metadata,
            chunks
        );
    }

    public SearchableMaterialSnapshot(
        String materialId,
        boolean searchable,
        String sourceKey,
        String title,
        String sourceType,
        String originalFileName,
        String mediaType,
        Instant createdAt,
        Instant updatedAt,
        List<SearchableMaterialChunkSnapshot> chunks
    ) {
        this(
            materialId,
            searchable,
            sourceKey,
            null,
            null,
            title,
            sourceType,
            originalFileName,
            mediaType,
            createdAt,
            updatedAt,
            MaterialMetadataSnapshot.empty(),
            chunks
        );
    }
}
