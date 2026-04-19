package com.example.demo.model;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

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

    public RetrievalQueryHints withoutDismissedFilterKeys(Collection<String> dismissedFilterKeys) {
        if (dismissedFilterKeys == null || dismissedFilterKeys.isEmpty()) {
            return this;
        }
        Set<String> keys = new HashSet<>();
        for (String key : dismissedFilterKeys) {
            if (key != null) {
                keys.add(key.trim().toLowerCase(Locale.ROOT));
            }
        }
        boolean dismissDocumentDate = keys.contains("documentdatefrom") || keys.contains("documentdateto");
        return new RetrievalQueryHints(
            keys.contains("documentnumber") ? null : documentNumber,
            dismissDocumentDate ? null : documentDateFrom,
            dismissDocumentDate ? null : documentDateTo,
            versionLabel,
            keys.contains("language") ? null : language,
            keys.contains("project") ? null : project,
            keys.contains("counterparty") ? null : counterparty,
            keys.contains("businessstatus") ? null : businessStatus,
            keys.contains("department") ? null : department
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
