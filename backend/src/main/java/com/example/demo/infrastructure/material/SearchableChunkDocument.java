package com.example.demo.infrastructure.material;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.example.demo.model.MaterialMetadataSnapshot;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record SearchableChunkDocument(
    @JsonIgnore String documentId,
    String chunkId,
    String materialId,
    String sourceKey,
    String title,
    String chunkText,
    Integer page,
    String extractor,
    boolean ocrUsed,
    String chunkType,
    List<String> sectionPath,
    List<String> headingTrail,
    String tableId,
    String slideId,
    String parserConfidence,
    String documentNumber,
    LocalDate documentDate,
    String department,
    String project,
    String counterparty,
    String businessStatus,
    String language,
    List<String> tags,
    String sourceTrust,
    String sourceType,
    Instant updatedAt
) {
    public SearchableChunkDocument(
        String documentId,
        String chunkId,
        String materialId,
        String sourceKey,
        String title,
        String chunkText,
        Integer page,
        String extractor,
        boolean ocrUsed,
        String sourceType,
        Instant updatedAt
    ) {
        this(
            documentId,
            chunkId,
            materialId,
            sourceKey,
            title,
            chunkText,
            page,
            extractor,
            ocrUsed,
            DocumentBlockType.NARRATIVE.name(),
            List.of(),
            List.of(),
            null,
            null,
            ocrUsed ? DocumentBlockConfidence.LOW.name() : DocumentBlockConfidence.HIGH.name(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(),
            null,
            sourceType,
            updatedAt
        );
    }

    public SearchableChunkDocument(
        String documentId,
        String chunkId,
        String materialId,
        String sourceKey,
        String title,
        String chunkText,
        int page,
        String extractor,
        boolean ocrUsed,
        String sourceType,
        Instant updatedAt
    ) {
        this(
            documentId,
            chunkId,
            materialId,
            sourceKey,
            title,
            chunkText,
            Integer.valueOf(page),
            extractor,
            ocrUsed,
            sourceType,
            updatedAt
        );
    }

    public static List<SearchableChunkDocument> fromSnapshot(SearchableMaterialSnapshot snapshot) {
        if (snapshot == null || !snapshot.searchable()) {
            return List.of();
        }

        MaterialMetadataSnapshot metadata = snapshot.metadata() == null ? MaterialMetadataSnapshot.empty() : snapshot.metadata();
        return snapshot.chunks().stream()
            .map(chunk -> {
                String chunkId = snapshot.materialId() + ":" + chunk.chunkIndex();
                return new SearchableChunkDocument(
                    chunkId,
                    chunkId,
                    snapshot.materialId(),
                    snapshot.sourceKey(),
                    snapshot.title(),
                    chunk.chunkText(),
                    chunk.page(),
                    chunk.extractor(),
                    chunk.ocrUsed(),
                    chunk.chunkType().name(),
                    chunk.sectionPath(),
                    chunk.headingTrail(),
                    chunk.tableId(),
                    chunk.slideId(),
                    chunk.parserConfidence().name(),
                    metadata.documentNumber(),
                    metadata.documentDate(),
                    metadata.department(),
                    metadata.project(),
                    metadata.counterparty(),
                    metadata.businessStatus(),
                    metadata.language(),
                    metadata.tags(),
                    metadata.sourceTrust().name(),
                    snapshot.sourceType(),
                    snapshot.updatedAt()
                );
            })
            .toList();
    }
}
