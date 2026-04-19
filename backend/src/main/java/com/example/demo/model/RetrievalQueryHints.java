package com.example.demo.model;

import java.time.LocalDate;

public record RetrievalQueryHints(
    String documentNumber,
    LocalDate documentDateFrom,
    LocalDate documentDateTo,
    String versionLabel,
    String language,
    String project,
    String counterparty,
    String businessStatus,
    String department
) {
    public RetrievalQueryHints {
        documentNumber = normalizeText(documentNumber);
        versionLabel = normalizeText(versionLabel);
        language = normalizeText(language);
        project = normalizeText(project);
        counterparty = normalizeText(counterparty);
        businessStatus = normalizeText(businessStatus);
        department = normalizeText(department);
        if (documentDateFrom != null && documentDateTo != null && documentDateFrom.isAfter(documentDateTo)) {
            LocalDate originalFrom = documentDateFrom;
            documentDateFrom = documentDateTo;
            documentDateTo = originalFrom;
        }
    }

    public static RetrievalQueryHints empty() {
        return new RetrievalQueryHints(null, null, null, null, null, null, null, null, null);
    }

    public boolean isEmpty() {
        return documentNumber == null
            && documentDateFrom == null
            && documentDateTo == null
            && versionLabel == null
            && language == null
            && project == null
            && counterparty == null
            && businessStatus == null
            && department == null;
    }

    public RetrievalFilters toRetrievalFilters() {
        return new RetrievalFilters(
            documentNumber,
            documentDateFrom,
            documentDateTo,
            department,
            project,
            counterparty,
            businessStatus,
            language,
            java.util.List.of(),
            null
        );
    }

    private static String normalizeText(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String normalized = rawValue.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
