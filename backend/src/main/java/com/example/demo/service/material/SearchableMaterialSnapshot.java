package com.example.demo.service.material;

import com.example.demo.model.MaterialMetadataSnapshot;
import java.time.Instant;
import java.util.List;

public record SearchableMaterialSnapshot(
    String materialId,
    boolean searchable,
    String sourceKey,
    String title,
    String sourceType,
    String originalFileName,
    String mediaType,
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
        Instant updatedAt,
        List<SearchableMaterialChunkSnapshot> chunks
    ) {
        this(
            materialId,
            searchable,
            sourceKey,
            title,
            sourceType,
            originalFileName,
            mediaType,
            updatedAt,
            MaterialMetadataSnapshot.empty(),
            chunks
        );
    }
}
