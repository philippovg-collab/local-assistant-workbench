package com.example.demo.service;

import com.example.demo.model.DocumentStatus;
import com.example.demo.model.DocumentType;
import com.example.demo.model.MaterialLanguageCode;
import com.example.demo.model.RetrievalQueryHints;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RetrievalQueryHintExtractor {

    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("\\b(\\d{4}-\\d{2}-\\d{2})\\b");
    private static final Pattern DOTTED_DATE_PATTERN = Pattern.compile("\\b(\\d{2}[./]\\d{2}[./]\\d{4})\\b");
    private static final Pattern RANGE_PATTERN = Pattern.compile(
        "(?iu)(?:с|from)\\s+(\\d{4}-\\d{2}-\\d{2}|\\d{2}[./]\\d{2}[./]\\d{4})\\s+(?:по|to)\\s+(\\d{4}-\\d{2}-\\d{2}|\\d{2}[./]\\d{2}[./]\\d{4})"
    );
    private static final Pattern DOCUMENT_NUMBER_MARKER_PATTERN = Pattern.compile(
        "(?iu)№\\s*([\\p{L}\\p{N}][\\p{L}\\p{N}/._-]{2,})"
    );
    private static final Pattern DOCUMENT_NUMBER_CODE_PATTERN = Pattern.compile(
        "\\b([A-Z]{2,}(?:-[A-Z0-9]{2,}){1,})\\b"
    );
    private static final Pattern VERSION_PATTERN = Pattern.compile(
        "(?iu)(v\\d+(?:\\.\\d+)*)|(rev\\s*\\d+)|(revision\\s*\\d+)|(версия\\s*\\d+)"
    );

    private static final Pattern PROJECT_PATTERN = Pattern.compile(
        "(?iu)(?:по\\s+проекту|project)\\s+([\\p{L}\\p{N}][\\p{L}\\p{N} .&()/_-]{1,80})"
    );
    private static final Pattern COUNTERPARTY_PATTERN = Pattern.compile(
        "(?iu)(?:контрагент|counterparty)\\s*[: ]\\s*([\\p{L}\\p{N}][\\p{L}\\p{N} .&()/_-]{1,80})"
    );
    private static final Pattern STATUS_PATTERN = Pattern.compile(
        "(?iu)(?:status|статус)\\s*[: ]\\s*([\\p{L}\\p{N}][\\p{L}\\p{N} .&()/_-]{1,60})"
    );
    private static final Pattern DEPARTMENT_PATTERN = Pattern.compile(
        "(?iu)(?:подразделение|департамент|department)\\s*[: ]\\s*([\\p{L}\\p{N}][\\p{L}\\p{N} .&()/_-]{1,80})"
    );

    public RetrievalQueryHints extract(String query) {
        if (!StringUtils.hasText(query)) {
            return RetrievalQueryHints.empty();
        }

        String normalized = query.trim();
        LocalDate documentDateFrom = null;
        LocalDate documentDateTo = null;

        Matcher rangeMatcher = RANGE_PATTERN.matcher(normalized);
        if (rangeMatcher.find()) {
            documentDateFrom = parseDate(rangeMatcher.group(1));
            documentDateTo = parseDate(rangeMatcher.group(2));
        } else {
            java.util.List<LocalDate> dates = extractStandaloneDates(normalized);
            if (!dates.isEmpty()) {
                documentDateFrom = dates.getFirst();
                documentDateTo = dates.getFirst();
            }
        }

        List<MaterialLanguageCode> languageCodes = extractLanguageCodes(normalized);
        String project = extractFacet(normalized, PROJECT_PATTERN);

        return new RetrievalQueryHints(
            extractDocumentNumber(normalized),
            documentDateFrom,
            documentDateTo,
            extractVersionLabel(normalized),
            legacyLanguageValue(languageCodes),
            project,
            extractFacet(normalized, COUNTERPARTY_PATTERN),
            extractFacet(normalized, STATUS_PATTERN),
            extractFacet(normalized, DEPARTMENT_PATTERN),
            extractDocumentTypes(normalized),
            extractDocumentStatuses(normalized),
            List.of(),
            languageCodes,
            null,
            null,
            null,
            null
        );
    }

    private java.util.List<LocalDate> extractStandaloneDates(String query) {
        java.util.List<LocalDate> dates = new java.util.ArrayList<>();
        collectDates(ISO_DATE_PATTERN.matcher(query), dates);
        collectDates(DOTTED_DATE_PATTERN.matcher(query), dates);
        return dates;
    }

    private void collectDates(Matcher matcher, java.util.List<LocalDate> target) {
        while (matcher.find()) {
            LocalDate parsed = parseDate(matcher.group(1));
            if (parsed != null) {
                target.add(parsed);
            }
        }
    }

    private String extractDocumentNumber(String query) {
        Matcher markerMatcher = DOCUMENT_NUMBER_MARKER_PATTERN.matcher(query);
        if (markerMatcher.find()) {
            return markerMatcher.group(1);
        }

        Matcher codeMatcher = DOCUMENT_NUMBER_CODE_PATTERN.matcher(query.toUpperCase(Locale.ROOT));
        if (codeMatcher.find()) {
            return codeMatcher.group(1);
        }
        return null;
    }

    private String extractVersionLabel(String query) {
        Matcher matcher = VERSION_PATTERN.matcher(query);
        if (!matcher.find()) {
            return null;
        }
        for (int index = 1; index <= matcher.groupCount(); index++) {
            if (StringUtils.hasText(matcher.group(index))) {
                return matcher.group(index).trim();
            }
        }
        return null;
    }

    private List<MaterialLanguageCode> extractLanguageCodes(String query) {
        String normalized = query.toLowerCase(Locale.ROOT);
        if (normalized.contains("на русском")
            || normalized.contains("русский")
            || normalized.contains("русском")
            || Pattern.compile("(?iu)\\bru\\b").matcher(query).find()) {
            return List.of(MaterialLanguageCode.RU);
        }
        if (normalized.contains("на английском")
            || normalized.contains("английский")
            || normalized.contains("английском")
            || normalized.contains("english")
            || Pattern.compile("(?iu)\\ben\\b").matcher(query).find()) {
            return List.of(MaterialLanguageCode.EN);
        }
        if (normalized.contains("на казахском")
            || normalized.contains("казахский")
            || normalized.contains("казахском")
            || Pattern.compile("(?iu)\\bkk\\b").matcher(query).find()) {
            return List.of(MaterialLanguageCode.KK);
        }
        return List.of();
    }

    private String legacyLanguageValue(List<MaterialLanguageCode> languageCodes) {
        if (languageCodes == null || languageCodes.isEmpty()) {
            return null;
        }
        return languageCodes.getFirst().name().toLowerCase(Locale.ROOT);
    }

    private List<DocumentStatus> extractDocumentStatuses(String query) {
        String statusText = extractFacet(query, STATUS_PATTERN);
        if (!StringUtils.hasText(statusText)) {
            return List.of();
        }
        String normalized = statusText.toLowerCase(Locale.ROOT);
        if (normalized.contains("active") || normalized.contains("действ") || normalized.contains("актив")) {
            return List.of(DocumentStatus.ACTIVE);
        }
        if (normalized.contains("draft") || normalized.contains("чернов")) {
            return List.of(DocumentStatus.DRAFT);
        }
        if (normalized.contains("archive") || normalized.contains("архив")) {
            return List.of(DocumentStatus.ARCHIVED);
        }
        if (normalized.contains("revoked") || normalized.contains("отозв") || normalized.contains("отмен")) {
            return List.of(DocumentStatus.REVOKED);
        }
        return List.of();
    }

    private List<DocumentType> extractDocumentTypes(String query) {
        String normalized = query.toLowerCase(Locale.ROOT);
        if (normalized.contains("договор") || normalized.contains("contract")) {
            return List.of(DocumentType.CONTRACT);
        }
        if (normalized.contains("политик") || normalized.contains("policy")) {
            return List.of(DocumentType.POLICY);
        }
        if (normalized.contains("отчет") || normalized.contains("отчёт") || normalized.contains("report")) {
            return List.of(DocumentType.REPORT);
        }
        if (normalized.contains("регламент") || normalized.contains("procedure")) {
            return List.of(DocumentType.PROCEDURE);
        }
        if (normalized.contains("презентац") || normalized.contains("presentation")) {
            return List.of(DocumentType.PRESENTATION);
        }
        if (normalized.contains("таблиц") || normalized.contains("spreadsheet")) {
            return List.of(DocumentType.SPREADSHEET);
        }
        if (normalized.contains("письм") || normalized.contains("letter")) {
            return List.of(DocumentType.LETTER);
        }
        if (normalized.contains("инструкц") || normalized.contains("manual")) {
            return List.of(DocumentType.MANUAL);
        }
        if (normalized.contains("faq")) {
            return List.of(DocumentType.FAQ);
        }
        return List.of();
    }

    private String extractFacet(String query, Pattern pattern) {
        Matcher matcher = pattern.matcher(query);
        if (!matcher.find()) {
            return null;
        }
        return trimFacetValue(matcher.group(1));
    }

    private String trimFacetValue(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        String normalized = rawValue.trim()
            .replaceAll("[,.;:!?]+$", "")
            .replaceAll("(?iu)\\s+(договор|contract|контрагент|counterparty|status|статус|подразделение|department|version|версия|on|in)\\b.*$", "")
            .trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private LocalDate parseDate(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }

        String normalized = rawValue.trim();
        try {
            if (normalized.contains("-")) {
                return LocalDate.parse(normalized);
            }
            String dotted = normalized.replace('/', '.');
            return LocalDate.parse(dotted, DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
