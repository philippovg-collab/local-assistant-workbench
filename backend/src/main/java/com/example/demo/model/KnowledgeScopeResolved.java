package com.example.demo.model;

import java.time.LocalDate;
import java.util.List;

public record KnowledgeScopeResolved(
    List<KnowledgePresetReference> presets,
    List<KnowledgePresetReference> facets,
    List<KnowledgeDocumentClass> documentClasses,
    List<DocumentType> documentTypes,
    List<DocumentStatus> documentStatuses,
    List<String> projectKeys,
    String documentNumber,
    List<MaterialLanguageCode> languageCodes,
    List<String> tags,
    String workspaceKey,
    LocalDate periodStartFrom,
    LocalDate periodStartTo,
    LocalDate periodEndFrom,
    LocalDate periodEndTo,
    boolean uploadedTodayOnly
) {
    public KnowledgeScopeResolved {
        presets = presets == null ? List.of() : List.copyOf(presets);
        facets = facets == null ? List.of() : List.copyOf(facets);
        documentClasses = documentClasses == null ? List.of() : List.copyOf(documentClasses);
        documentTypes = documentTypes == null ? List.of() : List.copyOf(documentTypes);
        documentStatuses = documentStatuses == null ? List.of() : List.copyOf(documentStatuses);
        projectKeys = projectKeys == null ? List.of() : List.copyOf(projectKeys);
        languageCodes = languageCodes == null ? List.of() : List.copyOf(languageCodes);
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    public KnowledgeScopeResolved(
        List<KnowledgePresetReference> presets,
        List<KnowledgeDocumentClass> documentClasses,
        List<String> tags,
        String workspaceKey,
        boolean uploadedTodayOnly
    ) {
        this(
            presets,
            List.of(),
            documentClasses,
            List.of(),
            List.of(),
            List.of(),
            null,
            List.of(),
            tags,
            workspaceKey,
            null,
            null,
            null,
            null,
            uploadedTodayOnly
        );
    }

    public static KnowledgeScopeResolved empty() {
        return new KnowledgeScopeResolved(
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            List.of(),
            List.of(),
            null,
            null,
            null,
            null,
            null,
            false
        );
    }
}
