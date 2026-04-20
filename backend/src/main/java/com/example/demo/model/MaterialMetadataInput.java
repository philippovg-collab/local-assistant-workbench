package com.example.demo.model;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;

public record MaterialMetadataInput(
    DocumentType documentType,
    KnowledgeDocumentClass knowledgeDocumentClass,
    LocalDate documentDate,
    @Size(max = 128)
    String documentNumber,
    @Size(max = 128)
    String author,
    @Size(max = 128)
    String department,
    @Size(max = 128)
    String versionLabel,
    @Size(max = 128)
    String language,
    @Size(max = 32)
    List<String> tags,
    SourceTrustLevel sourceTrust,
    @Size(max = 128)
    String project,
    @Size(max = 128)
    String workspaceKey,
    @Size(max = 128)
    String counterparty,
    @Size(max = 128)
    String businessStatus,
    LocalDate periodStart,
    LocalDate periodEnd
) {

    public MaterialMetadataInput {
        workspaceKey = normalizeText(workspaceKey);
        documentNumber = normalizeText(documentNumber);
        author = normalizeText(author);
        department = normalizeText(department);
        versionLabel = normalizeText(versionLabel);
        language = normalizeText(language);
        tags = normalizeTags(tags);
        project = normalizeText(project);
        counterparty = normalizeText(counterparty);
        businessStatus = normalizeText(businessStatus);
        if (periodStart != null && periodEnd != null && periodStart.isAfter(periodEnd)) {
            throw new IllegalArgumentException("periodStart must not be after periodEnd");
        }
    }

    public MaterialMetadataInput(
        DocumentType documentType,
        LocalDate documentDate,
        String documentNumber,
        String author,
        String department,
        String versionLabel,
        String language,
        List<String> tags,
        SourceTrustLevel sourceTrust,
        String project,
        String counterparty,
        String businessStatus,
        LocalDate periodStart,
        LocalDate periodEnd
    ) {
        this(
            documentType,
            null,
            documentDate,
            documentNumber,
            author,
            department,
            versionLabel,
            language,
            tags,
            sourceTrust,
            project,
            null,
            counterparty,
            businessStatus,
            periodStart,
            periodEnd
        );
    }

    public KnowledgeDocumentClass effectiveKnowledgeDocumentClass() {
        return knowledgeDocumentClass != null
            ? knowledgeDocumentClass
            : MaterialMetadataSnapshot.deriveKnowledgeDocumentClass(documentType);
    }

    public String effectiveWorkspaceKey() {
        return workspaceKey != null
            ? workspaceKey
            : MaterialMetadataSnapshot.deriveWorkspaceKey(project);
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static List<String> normalizeTags(List<String> rawTags) {
        if (rawTags == null || rawTags.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String tag : rawTags) {
            String candidate = normalizeText(tag);
            if (candidate != null) {
                normalized.add(candidate);
            }
        }
        return List.copyOf(normalized);
    }
}
