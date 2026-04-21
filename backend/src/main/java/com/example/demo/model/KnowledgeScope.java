package com.example.demo.model;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;

public record KnowledgeScope(
    @Size(max = 32)
    List<String> presetIds,
    @Size(max = 32)
    List<String> facetIds,
    @Size(max = 32)
    List<KnowledgeDocumentClass> documentClasses,
    @Size(max = 32)
    List<DocumentType> documentTypes,
    @Size(max = 32)
    List<DocumentStatus> documentStatuses,
    @Size(max = 32)
    List<String> projectKeys,
    @Size(max = 128)
    String documentNumber,
    @Size(max = 32)
    List<MaterialLanguageCode> languageCodes,
    @Size(max = 32)
    List<String> tags,
    @Size(max = 128)
    String workspaceKey,
    LocalDate periodStartFrom,
    LocalDate periodStartTo,
    LocalDate periodEndFrom,
    LocalDate periodEndTo,
    boolean uploadedTodayOnly
) {

    public KnowledgeScope {
        presetIds = normalizeStringList(presetIds);
        facetIds = normalizeStringList(facetIds);
        documentClasses = documentClasses == null ? List.of() : List.copyOf(new LinkedHashSet<>(documentClasses));
        documentTypes = documentTypes == null ? List.of() : List.copyOf(new LinkedHashSet<>(documentTypes));
        documentStatuses = documentStatuses == null ? List.of() : List.copyOf(new LinkedHashSet<>(documentStatuses));
        projectKeys = normalizeStringList(projectKeys);
        documentNumber = normalizeText(documentNumber);
        languageCodes = languageCodes == null ? List.of() : List.copyOf(new LinkedHashSet<>(languageCodes));
        tags = normalizeStringList(tags);
        workspaceKey = normalizeText(workspaceKey);
        if (periodStartFrom != null && periodStartTo != null && periodStartFrom.isAfter(periodStartTo)) {
            throw new IllegalArgumentException("periodStartFrom must not be after periodStartTo");
        }
        if (periodEndFrom != null && periodEndTo != null && periodEndFrom.isAfter(periodEndTo)) {
            throw new IllegalArgumentException("periodEndFrom must not be after periodEndTo");
        }
    }

    public KnowledgeScope(
        List<String> presetIds,
        List<KnowledgeDocumentClass> documentClasses,
        List<String> tags,
        String workspaceKey,
        boolean uploadedTodayOnly
    ) {
        this(
            presetIds,
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

    public static KnowledgeScope empty() {
        return new KnowledgeScope(
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

    private static List<String> normalizeStringList(List<String> rawValues) {
        if (rawValues == null || rawValues.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String rawValue : rawValues) {
            String candidate = normalizeText(rawValue);
            if (candidate != null) {
                normalized.add(candidate);
            }
        }
        return List.copyOf(normalized);
    }

    private static String normalizeText(String rawValue) {
        if (rawValue == null) {
            return null;
        }

        String normalized = rawValue.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
