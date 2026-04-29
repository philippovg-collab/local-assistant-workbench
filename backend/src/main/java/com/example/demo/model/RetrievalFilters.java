package com.example.demo.model;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public record RetrievalFilters(
    @Size(max = 128)
    String documentNumber,
    LocalDate documentDateFrom,
    LocalDate documentDateTo,
    @Size(max = 128)
    String department,
    @Size(max = 128)
    String project,
    @Size(max = 128)
    String counterparty,
    @Size(max = 128)
    String businessStatus,
    @Size(max = 128)
    String language,
    @Size(max = 32)
    List<String> tags,
    SourceTrustLevel sourceTrustMin,
    @Size(max = 32)
    List<DocumentType> documentTypes,
    @Size(max = 32)
    List<DocumentStatus> documentStatuses,
    @Size(max = 32)
    List<String> projectKeys,
    @Size(max = 32)
    List<MaterialLanguageCode> languageCodes,
    LocalDate periodStartFrom,
    LocalDate periodStartTo,
    LocalDate periodEndFrom,
    LocalDate periodEndTo
) {

    public RetrievalFilters {
        documentNumber = normalizeText(documentNumber);
        department = normalizeText(department);
        project = normalizeText(project);
        counterparty = normalizeText(counterparty);
        businessStatus = normalizeText(businessStatus);
        language = normalizeText(language);
        tags = normalizeTags(tags);
        documentTypes = documentTypes == null ? List.of() : List.copyOf(new LinkedHashSet<>(documentTypes));
        documentStatuses = documentStatuses == null ? List.of() : List.copyOf(new LinkedHashSet<>(documentStatuses));
        projectKeys = normalizeStringList(projectKeys);
        languageCodes = languageCodes == null ? List.of() : List.copyOf(new LinkedHashSet<>(languageCodes));
        if (documentDateFrom != null && documentDateTo != null && documentDateFrom.isAfter(documentDateTo)) {
            throw new IllegalArgumentException("documentDateFrom must not be after documentDateTo");
        }
        if (periodStartFrom != null && periodStartTo != null && periodStartFrom.isAfter(periodStartTo)) {
            throw new IllegalArgumentException("periodStartFrom must not be after periodStartTo");
        }
        if (periodEndFrom != null && periodEndTo != null && periodEndFrom.isAfter(periodEndTo)) {
            throw new IllegalArgumentException("periodEndFrom must not be after periodEndTo");
        }
    }

    public RetrievalFilters(
        String documentNumber,
        LocalDate documentDateFrom,
        LocalDate documentDateTo,
        String department,
        String project,
        String counterparty,
        String businessStatus,
        String language,
        List<String> tags,
        SourceTrustLevel sourceTrustMin
    ) {
        this(
            documentNumber,
            documentDateFrom,
            documentDateTo,
            department,
            project,
            counterparty,
            businessStatus,
            language,
            tags,
            sourceTrustMin,
            List.of(),
            parseLegacyDocumentStatuses(businessStatus),
            List.of(),
            parseLegacyLanguageCodes(language),
            null,
            null,
            null,
            null
        );
    }

    public static RetrievalFilters empty() {
        return new RetrievalFilters(null, null, null, null, null, null, null, null, List.of(), null);
    }

    public RetrievalFilters mergeMissing(RetrievalFilters fallback) {
        RetrievalFilters safeFallback = fallback == null ? empty() : fallback;
        return new RetrievalFilters(
            documentNumber != null ? documentNumber : safeFallback.documentNumber(),
            documentDateFrom != null ? documentDateFrom : safeFallback.documentDateFrom(),
            documentDateTo != null ? documentDateTo : safeFallback.documentDateTo(),
            department != null ? department : safeFallback.department(),
            project != null ? project : safeFallback.project(),
            counterparty != null ? counterparty : safeFallback.counterparty(),
            businessStatus != null ? businessStatus : safeFallback.businessStatus(),
            language != null ? language : safeFallback.language(),
            !tags.isEmpty() ? tags : safeFallback.tags(),
            sourceTrustMin != null ? sourceTrustMin : safeFallback.sourceTrustMin(),
            !documentTypes.isEmpty() ? documentTypes : safeFallback.documentTypes(),
            !documentStatuses.isEmpty() ? documentStatuses : safeFallback.documentStatuses(),
            !projectKeys.isEmpty() ? projectKeys : safeFallback.projectKeys(),
            !languageCodes.isEmpty() ? languageCodes : safeFallback.languageCodes(),
            periodStartFrom != null ? periodStartFrom : safeFallback.periodStartFrom(),
            periodStartTo != null ? periodStartTo : safeFallback.periodStartTo(),
            periodEndFrom != null ? periodEndFrom : safeFallback.periodEndFrom(),
            periodEndTo != null ? periodEndTo : safeFallback.periodEndTo()
        );
    }

    public boolean isEmpty() {
        return documentNumber == null
            && documentDateFrom == null
            && documentDateTo == null
            && department == null
            && project == null
            && counterparty == null
            && businessStatus == null
            && language == null
            && tags.isEmpty()
            && sourceTrustMin == null
            && documentTypes.isEmpty()
            && documentStatuses.isEmpty()
            && projectKeys.isEmpty()
            && languageCodes.isEmpty()
            && periodStartFrom == null
            && periodStartTo == null
            && periodEndFrom == null
            && periodEndTo == null;
    }

    public boolean matches(MaterialMetadataSnapshot metadata) {
        MaterialMetadataSnapshot safeMetadata = metadata == null ? MaterialMetadataSnapshot.empty() : metadata;
        if (documentNumber != null && !equalsIgnoreCase(documentNumber, safeMetadata.documentNumber())) {
            return false;
        }
        if (documentDateFrom != null && (safeMetadata.documentDate() == null || safeMetadata.documentDate().isBefore(documentDateFrom))) {
            return false;
        }
        if (documentDateTo != null && (safeMetadata.documentDate() == null || safeMetadata.documentDate().isAfter(documentDateTo))) {
            return false;
        }
        if (department != null && !equalsIgnoreCase(department, safeMetadata.department())) {
            return false;
        }
        if (project != null && !equalsIgnoreCase(project, safeMetadata.project())) {
            return false;
        }
        if (counterparty != null && !equalsIgnoreCase(counterparty, safeMetadata.counterparty())) {
            return false;
        }
        if (businessStatus != null && !equalsIgnoreCase(businessStatus, safeMetadata.businessStatus())) {
            return false;
        }
        if (language != null && !equalsIgnoreCase(language, safeMetadata.language())) {
            return false;
        }
        if (!documentTypes.isEmpty() && !documentTypes.contains(safeMetadata.documentType())) {
            return false;
        }
        if (!documentStatuses.isEmpty() && !documentStatuses.contains(safeMetadata.documentStatus())) {
            return false;
        }
        if (!projectKeys.isEmpty()) {
            String normalizedProjectKey = normalizeText(safeMetadata.projectKey());
            if (normalizedProjectKey == null || !lowerCaseProjectKeys().contains(normalizedProjectKey.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        if (!languageCodes.isEmpty() && !languageCodes.contains(safeMetadata.languageCode())) {
            return false;
        }
        if (periodStartFrom != null && (safeMetadata.periodStart() == null || safeMetadata.periodStart().isBefore(periodStartFrom))) {
            return false;
        }
        if (periodStartTo != null && (safeMetadata.periodStart() == null || safeMetadata.periodStart().isAfter(periodStartTo))) {
            return false;
        }
        if (periodEndFrom != null && (safeMetadata.periodEnd() == null || safeMetadata.periodEnd().isBefore(periodEndFrom))) {
            return false;
        }
        if (periodEndTo != null && (safeMetadata.periodEnd() == null || safeMetadata.periodEnd().isAfter(periodEndTo))) {
            return false;
        }
        if (!tags.isEmpty()) {
            Set<String> recordTags = safeMetadata.tags().stream()
                .map(RetrievalFilters::normalizeTag)
                .filter(candidate -> candidate != null)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            boolean matchesAnyTag = tags.stream().anyMatch(recordTags::contains);
            if (!matchesAnyTag) {
                return false;
            }
        }
        if (sourceTrustMin != null && trustRank(safeMetadata.sourceTrust()) < trustRank(sourceTrustMin)) {
            return false;
        }
        return true;
    }

    public List<String> lowerCaseTags() {
        return tags.stream()
            .map(RetrievalFilters::normalizeTag)
            .filter(candidate -> candidate != null)
            .toList();
    }

    public List<String> documentTypeNames() {
        return documentTypes.stream().map(Enum::name).toList();
    }

    public List<String> documentStatusNames() {
        return documentStatuses.stream().map(Enum::name).toList();
    }

    public List<String> lowerCaseProjectKeys() {
        return projectKeys.stream()
            .map(RetrievalFilters::normalizeText)
            .filter(candidate -> candidate != null)
            .map(candidate -> candidate.toLowerCase(Locale.ROOT))
            .toList();
    }

    public List<String> languageCodeNames() {
        return languageCodes.stream().map(Enum::name).toList();
    }

    public boolean hasExplicitDocumentStatuses() {
        return !documentStatuses.isEmpty() || businessStatus != null;
    }

    public boolean hasExplicitPeriods() {
        return periodStartFrom != null
            || periodStartTo != null
            || periodEndFrom != null
            || periodEndTo != null
            || documentDateFrom != null
            || documentDateTo != null;
    }

    public static int trustRank(SourceTrustLevel level) {
        SourceTrustLevel safeLevel = level == null ? SourceTrustLevel.UNKNOWN : level;
        return switch (safeLevel) {
            case UNKNOWN -> 0;
            case LOW -> 1;
            case MEDIUM -> 2;
            case HIGH -> 3;
        };
    }

    private static boolean equalsIgnoreCase(String expected, String actual) {
        return expected != null && actual != null && expected.equalsIgnoreCase(actual);
    }

    private static String normalizeText(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String normalized = rawValue.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static List<String> normalizeTags(List<String> rawTags) {
        if (rawTags == null || rawTags.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String tag : rawTags) {
            String candidate = normalizeTag(tag);
            if (candidate != null) {
                normalized.add(candidate);
            }
        }
        return List.copyOf(normalized);
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

    private static String normalizeTag(String rawValue) {
        String normalized = normalizeText(rawValue);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private static List<DocumentStatus> parseLegacyDocumentStatuses(String rawValue) {
        String normalized = normalizeText(rawValue);
        if (normalized == null) {
            return List.of();
        }
        try {
            return List.of(DocumentStatus.valueOf(normalized.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            return List.of();
        }
    }

    private static List<MaterialLanguageCode> parseLegacyLanguageCodes(String rawValue) {
        String normalized = normalizeText(rawValue);
        if (normalized == null) {
            return List.of();
        }
        try {
            return List.of(MaterialLanguageCode.fromValue(normalized));
        } catch (IllegalArgumentException ignored) {
            return List.of();
        }
    }
}
