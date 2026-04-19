package com.example.demo.model;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public record RetrievalFilters(
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

    public RetrievalFilters {
        documentNumber = normalizeText(documentNumber);
        department = normalizeText(department);
        project = normalizeText(project);
        counterparty = normalizeText(counterparty);
        businessStatus = normalizeText(businessStatus);
        language = normalizeText(language);
        tags = normalizeTags(tags);
        if (documentDateFrom != null && documentDateTo != null && documentDateFrom.isAfter(documentDateTo)) {
            throw new IllegalArgumentException("documentDateFrom must not be after documentDateTo");
        }
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
            sourceTrustMin != null ? sourceTrustMin : safeFallback.sourceTrustMin()
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
            && sourceTrustMin == null;
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

    private static String normalizeTag(String rawValue) {
        String normalized = normalizeText(rawValue);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }
}
