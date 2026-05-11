package com.example.demo.model;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
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
    String department,
    List<DocumentType> documentTypes,
    List<DocumentStatus> documentStatuses,
    List<String> projectKeys,
    List<MaterialLanguageCode> languageCodes,
    LocalDate periodStartFrom,
    LocalDate periodStartTo,
    LocalDate periodEndFrom,
    LocalDate periodEndTo
) {
    public RetrievalQueryHints {
        documentNumber = normalizeText(documentNumber);
        versionLabel = normalizeText(versionLabel);
        language = normalizeText(language);
        project = normalizeText(project);
        counterparty = normalizeText(counterparty);
        businessStatus = normalizeText(businessStatus);
        department = normalizeText(department);
        documentTypes = documentTypes == null ? List.of() : List.copyOf(documentTypes);
        documentStatuses = documentStatuses == null ? List.of() : List.copyOf(documentStatuses);
        projectKeys = projectKeys == null ? List.of() : List.copyOf(projectKeys);
        languageCodes = languageCodes == null ? List.of() : List.copyOf(languageCodes);
        if (documentDateFrom != null && documentDateTo != null && documentDateFrom.isAfter(documentDateTo)) {
            LocalDate originalFrom = documentDateFrom;
            documentDateFrom = documentDateTo;
            documentDateTo = originalFrom;
        }
    }

    public RetrievalQueryHints(
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
        this(
            documentNumber,
            documentDateFrom,
            documentDateTo,
            versionLabel,
            language,
            project,
            counterparty,
            businessStatus,
            department,
            List.of(),
            List.of(),
            List.of(),
            parseLegacyLanguageCodes(language),
            null,
            null,
            null,
            null
        );
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
            && department == null
            && documentTypes.isEmpty()
            && documentStatuses.isEmpty()
            && projectKeys.isEmpty()
            && languageCodes.isEmpty()
            && periodStartFrom == null
            && periodStartTo == null
            && periodEndFrom == null
            && periodEndTo == null;
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
            null,
            documentTypes,
            documentStatuses,
            projectKeys,
            languageCodes,
            periodStartFrom,
            periodStartTo,
            periodEndFrom,
            periodEndTo,
            versionLabel,
            null,
            versionLabel == null ? null : VersionSelectionMode.VERSION_LABEL,
            null,
            null,
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
            keys.contains("versionlabel") ? null : versionLabel,
            keys.contains("language") ? null : language,
            keys.contains("project") ? null : project,
            keys.contains("counterparty") ? null : counterparty,
            keys.contains("businessstatus") ? null : businessStatus,
            keys.contains("department") ? null : department,
            keys.contains("documenttypes") ? List.of() : documentTypes,
            keys.contains("documentstatuses") ? List.of() : documentStatuses,
            keys.contains("projectkeys") || keys.contains("project") ? List.of() : projectKeys,
            keys.contains("languagecodes") || keys.contains("language") ? List.of() : languageCodes,
            keys.contains("periodstartfrom") ? null : periodStartFrom,
            keys.contains("periodstartto") ? null : periodStartTo,
            keys.contains("periodendfrom") ? null : periodEndFrom,
            keys.contains("periodendto") ? null : periodEndTo
        );
    }

    private static String normalizeText(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String normalized = rawValue.trim();
        return normalized.isEmpty() ? null : normalized;
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
