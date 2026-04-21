package com.example.demo.model;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public record MaterialMetadataInput(
    DocumentType documentType,
    @Size(max = 128)
    String workspaceKey,
    DocumentStatus documentStatus,
    @Size(max = 128)
    String projectKey,
    @Size(max = 128)
    String documentNumber,
    MaterialLanguageCode languageCode,
    @Size(max = 32)
    List<String> manualTags,
    LocalDate periodStart,
    LocalDate periodEnd,

    // Legacy write-only compatibility fields. Resolver decides which ones are safe to map.
    KnowledgeDocumentClass knowledgeDocumentClass,
    LocalDate documentDate,
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
    String counterparty,
    @Size(max = 128)
    String businessStatus
) {

    public MaterialMetadataInput {
        workspaceKey = normalizeText(workspaceKey);
        projectKey = normalizeText(projectKey);
        documentNumber = normalizeText(documentNumber);
        author = normalizeText(author);
        department = normalizeText(department);
        versionLabel = normalizeText(versionLabel);
        language = normalizeText(language);
        tags = normalizeTags(tags);
        manualTags = normalizeTags(!isEmpty(manualTags) ? manualTags : tags);
        project = normalizeText(project);
        counterparty = normalizeText(counterparty);
        businessStatus = normalizeText(businessStatus);
        if (languageCode == null) {
            languageCode = parseLegacyLanguageCode(language);
        }
        if (documentStatus == null) {
            documentStatus = parseLegacyDocumentStatus(businessStatus);
        }
        if (periodStart != null && periodEnd != null && periodStart.isAfter(periodEnd)) {
            throw new IllegalArgumentException("periodStart must not be after periodEnd");
        }
    }

    public MaterialMetadataInput(
        DocumentType documentType,
        KnowledgeDocumentClass knowledgeDocumentClass,
        LocalDate documentDate,
        String documentNumber,
        String author,
        String department,
        String versionLabel,
        String language,
        List<String> tags,
        SourceTrustLevel sourceTrust,
        String project,
        String workspaceKey,
        String counterparty,
        String businessStatus,
        LocalDate periodStart,
        LocalDate periodEnd
    ) {
        this(
            documentType,
            workspaceKey,
            parseLegacyDocumentStatus(businessStatus),
            null,
            documentNumber,
            parseLegacyLanguageCode(language),
            tags,
            periodStart,
            periodEnd,
            knowledgeDocumentClass,
            documentDate,
            author,
            department,
            versionLabel,
            language,
            tags,
            sourceTrust,
            project,
            counterparty,
            businessStatus
        );
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
        return MaterialMetadataSnapshot.deriveKnowledgeDocumentClass(documentType);
    }

    public DocumentStatus effectiveDocumentStatus() {
        return documentStatus == null ? DocumentStatus.ACTIVE : documentStatus;
    }

    public List<String> effectiveManualTags() {
        return manualTags == null ? List.of() : manualTags;
    }

    public String effectiveWorkspaceKey() {
        return workspaceKey;
    }

    private static MaterialLanguageCode parseLegacyLanguageCode(String rawValue) {
        String normalized = normalizeText(rawValue);
        if (normalized == null) {
            return null;
        }
        return switch (normalized.toLowerCase(Locale.ROOT)) {
            case "ru", "rus", "russian" -> MaterialLanguageCode.RU;
            case "kk", "kz", "kaz", "kazakh" -> MaterialLanguageCode.KK;
            case "en", "eng", "english" -> MaterialLanguageCode.EN;
            default -> null;
        };
    }

    private static DocumentStatus parseLegacyDocumentStatus(String rawValue) {
        String normalized = normalizeText(rawValue);
        if (normalized == null) {
            return null;
        }
        try {
            return DocumentStatus.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static boolean isEmpty(List<String> values) {
        return values == null || values.isEmpty();
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
