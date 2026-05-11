package com.example.demo.service.material;

import com.example.demo.model.DocumentBlockConfidence;
import com.example.demo.model.DocumentBlockType;
import com.example.demo.model.MaterialMetadataSnapshot;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record SearchableChunkDocument(
    @JsonIgnore String documentId,
    String chunkId,
    String materialId,
    String sourceKey,
    String versionLabel,
    String versionState,
    Integer lineageVersion,
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
    String workspaceKey,
    String knowledgeDocumentClass,
    String documentType,
    String documentStatus,
    String projectKey,
    String documentNumber,
    LocalDate documentDate,
    LocalDate periodStart,
    LocalDate periodEnd,
    String department,
    String project,
    String counterparty,
    String businessStatus,
    String language,
    String languageCode,
    List<String> tags,
    String sourceTrust,
    String sourceType,
    Instant createdAt,
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
            null,
            null,
            null,
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
            null,
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
            null,
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
        String chunkType,
        List<String> sectionPath,
        List<String> headingTrail,
        String tableId,
        String slideId,
        String parserConfidence,
        String workspaceKey,
        String knowledgeDocumentClass,
        String documentType,
        String documentStatus,
        String projectKey,
        String documentNumber,
        LocalDate documentDate,
        LocalDate periodStart,
        LocalDate periodEnd,
        String department,
        String project,
        String counterparty,
        String businessStatus,
        String language,
        String languageCode,
        List<String> tags,
        String sourceTrust,
        String sourceType,
        Instant createdAt,
        Instant updatedAt
    ) {
        this(
            documentId,
            chunkId,
            materialId,
            sourceKey,
            null,
            null,
            null,
            title,
            chunkText,
            page,
            extractor,
            ocrUsed,
            chunkType,
            sectionPath,
            headingTrail,
            tableId,
            slideId,
            parserConfidence,
            workspaceKey,
            knowledgeDocumentClass,
            documentType,
            documentStatus,
            projectKey,
            documentNumber,
            documentDate,
            periodStart,
            periodEnd,
            department,
            project,
            counterparty,
            businessStatus,
            language,
            languageCode,
            tags,
            sourceTrust,
            sourceType,
            createdAt,
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
                    metadata.versionLabel(),
                    snapshot.versionState() == null ? null : snapshot.versionState().name(),
                    snapshot.lineageVersion(),
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
                    metadata.workspaceKey(),
                    metadata.knowledgeDocumentClass().name(),
                    metadata.documentType().name(),
                    metadata.documentStatus().name(),
                    metadata.projectKey(),
                    metadata.documentNumber(),
                    metadata.documentDate(),
                    metadata.periodStart(),
                    metadata.periodEnd(),
                    metadata.department(),
                    metadata.project(),
                    metadata.counterparty(),
                    metadata.businessStatus(),
                    metadata.language(),
                    metadata.languageCode() == null ? null : metadata.languageCode().name(),
                    metadata.tags(),
                    metadata.sourceTrust().name(),
                    snapshot.sourceType(),
                    snapshot.createdAt(),
                    snapshot.updatedAt()
                );
            })
            .toList();
    }
}
