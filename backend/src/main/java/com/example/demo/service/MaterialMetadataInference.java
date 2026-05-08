package com.example.demo.service;

import static com.example.demo.service.MaterialMetadataDefaults.AUTHOR;
import static com.example.demo.service.MaterialMetadataDefaults.BUSINESS_STATUS;
import static com.example.demo.service.MaterialMetadataDefaults.COUNTERPARTY;
import static com.example.demo.service.MaterialMetadataDefaults.DEPARTMENT;
import static com.example.demo.service.MaterialMetadataDefaults.DOCUMENT_DATE;
import static com.example.demo.service.MaterialMetadataDefaults.DOCUMENT_NUMBER;
import static com.example.demo.service.MaterialMetadataDefaults.DOCUMENT_TYPE;
import static com.example.demo.service.MaterialMetadataDefaults.LABELLED_HEADER_CONFIDENCE;
import static com.example.demo.service.MaterialMetadataDefaults.LANGUAGE;
import static com.example.demo.service.MaterialMetadataDefaults.LANGUAGE_HEURISTIC_CONFIDENCE;
import static com.example.demo.service.MaterialMetadataDefaults.PERIOD_END;
import static com.example.demo.service.MaterialMetadataDefaults.PERIOD_START;
import static com.example.demo.service.MaterialMetadataDefaults.PROJECT;
import static com.example.demo.service.MaterialMetadataDefaults.REGEX_HINT_CONFIDENCE;
import static com.example.demo.service.MaterialMetadataDefaults.TAG_HINT_CONFIDENCE;
import static com.example.demo.service.MaterialMetadataDefaults.TAGS;
import static com.example.demo.service.MaterialMetadataDefaults.VERSION_LABEL;

import com.example.demo.model.DocumentType;
import com.example.demo.service.material.MaterialMetadataHints;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

final class MaterialMetadataInference {

    private static final int HEADER_LINE_LIMIT = 12;
    private static final int HEADER_CHAR_LIMIT = 1_200;
    private static final int MAX_INFERRED_TAGS = 3;
    private final MaterialMetadataTypeInference typeInference = new MaterialMetadataTypeInference();

    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("\\b(20\\d{2})-(\\d{2})-(\\d{2})\\b");
    private static final Pattern DMY_DATE_PATTERN = Pattern.compile("\\b(\\d{2})[./](\\d{2})[./](20\\d{2})\\b");
    private static final Pattern VERSION_PATTERN = Pattern.compile(
        "\\b(?:v(?:ersion)?|rev(?:ision)?|версия)\\s*[:#-]?\\s*([0-9]+(?:\\.[0-9]+)*)\\b",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern DOCUMENT_NUMBER_PATTERN = Pattern.compile(
        "\\b([A-ZА-ЯЁ]{1,8}(?:[-/][A-ZА-ЯЁ0-9]{1,16}){1,5}|[A-ZА-ЯЁ]{2,8}-\\d{2,}(?:[-/][A-ZА-ЯЁ0-9]{1,16})*)\\b",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern LABELLED_DOCUMENT_NUMBER_PATTERN = Pattern.compile(
        "^(?:document\\s*(?:no|number)?|doc\\s*(?:no|number)?|номер\\s*документа|документ|номер)\\s*(?:[:#№-]\\s*|№\\s*)(.+)$",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern LABELLED_DATE_PATTERN = Pattern.compile(
        "^(?:date|document\\s*date|дата|дата\\s*документа)\\s*[:\\-]\\s*(.+)$",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern AUTHOR_PATTERN = labelledPattern("author", "автор");
    private static final Pattern DEPARTMENT_PATTERN = labelledPattern("department", "division", "департамент", "подразделение", "отдел");
    private static final Pattern PROJECT_PATTERN = labelledPattern("project", "проект");
    private static final Pattern COUNTERPARTY_PATTERN = labelledPattern("counterparty", "контрагент");
    private static final Pattern STATUS_PATTERN = labelledPattern("status", "статус");
    private static final Pattern PERIOD_PATTERN = labelledPattern("period", "период");
    private static final Pattern PERIOD_START_PATTERN = labelledPattern(
        "period\\s*start",
        "start\\s*date",
        "дата\\s*начала",
        "начало\\s*периода"
    );
    private static final Pattern PERIOD_END_PATTERN = labelledPattern(
        "period\\s*end",
        "end\\s*date",
        "дата\\s*окончания",
        "конец\\s*периода"
    );
    private static final Pattern VERSION_LABEL_PATTERN = labelledPattern("version", "revision", "версия", "редакция");

    private static final Set<String> TAG_STOPWORDS = Set.of(
        "and", "brief", "contract", "document", "draft", "faq", "file", "final", "guide", "letter",
        "manual", "material", "note", "other", "pdf", "policy", "presentation", "procedure", "report",
        "scan", "sheet", "spreadsheet", "text", "txt", "updated", "version", "договор", "документ",
        "заметка", "инструкция", "материал", "отчет", "политика", "письмо", "проект", "презентация",
        "процедура", "редакция", "скан", "справка", "таблица", "текст", "файл"
    );

    MaterialMetadataHints inferHints(
        String title,
        String sourceType,
        String originalFileName,
        String mediaType,
        String contentHeader
    ) {
        String normalizedTitle = normalizeText(title);
        String normalizedFileName = normalizeText(originalFileName);
        String normalizedMediaType = normalizeText(mediaType);
        String searchableTitle = joinSearchText(normalizedTitle, normalizedFileName);
        List<String> headerLines = headerLines(contentHeader);
        String headerText = String.join("\n", headerLines);

        Map<String, Double> confidence = new LinkedHashMap<>();
        DocumentType documentType = typeInference.inferDocumentType(searchableTitle, normalizedFileName, normalizedMediaType);
        if (documentType != null) {
            confidence.put(DOCUMENT_TYPE, REGEX_HINT_CONFIDENCE);
        }
        String language = inferLanguage(searchableTitle, headerText);
        if (StringUtils.hasText(language)) {
            confidence.put(LANGUAGE, languageHintConfidence(searchableTitle));
        }
        LocalDate documentDate = inferDocumentDate(searchableTitle, headerLines);
        if (documentDate != null) {
            confidence.put(DOCUMENT_DATE, documentDateHintConfidence(headerLines));
        }
        String documentNumber = inferDocumentNumber(searchableTitle, headerLines, headerText);
        if (StringUtils.hasText(documentNumber)) {
            confidence.put(DOCUMENT_NUMBER, documentNumberHintConfidence(headerLines));
        }
        String versionLabel = inferVersionLabel(searchableTitle, headerLines, headerText);
        if (StringUtils.hasText(versionLabel)) {
            confidence.put(VERSION_LABEL, versionLabelHintConfidence(headerLines));
        }

        String author = putLabelledConfidence(headerLines, AUTHOR_PATTERN, confidence, AUTHOR);
        String department = putLabelledConfidence(headerLines, DEPARTMENT_PATTERN, confidence, DEPARTMENT);
        String project = putLabelledConfidence(headerLines, PROJECT_PATTERN, confidence, PROJECT);
        String counterparty = putLabelledConfidence(headerLines, COUNTERPARTY_PATTERN, confidence, COUNTERPARTY);
        String businessStatus = putLabelledConfidence(headerLines, STATUS_PATTERN, confidence, BUSINESS_STATUS);
        LocalDate[] period = inferPeriod(headerLines, confidence);
        List<String> tags = inferTags(normalizedTitle, normalizedFileName, sourceType);
        if (!tags.isEmpty()) {
            confidence.put(TAGS, TAG_HINT_CONFIDENCE);
        }

        return new MaterialMetadataHints(
            documentType,
            documentDate,
            documentNumber,
            author,
            department,
            versionLabel,
            language,
            tags,
            project,
            counterparty,
            businessStatus,
            period[0],
            period[1],
            confidence
        );
    }

    private String putLabelledConfidence(
        List<String> headerLines,
        Pattern pattern,
        Map<String, Double> confidence,
        String field
    ) {
        String value = firstMatchingValue(headerLines, pattern);
        if (StringUtils.hasText(value)) {
            confidence.put(field, LABELLED_HEADER_CONFIDENCE);
        }
        return value;
    }

    private LocalDate[] inferPeriod(List<String> headerLines, Map<String, Double> confidence) {
        LocalDate periodStart = null;
        LocalDate periodEnd = null;
        String explicitPeriod = firstMatchingValue(headerLines, PERIOD_PATTERN);
        if (StringUtils.hasText(explicitPeriod)) {
            List<LocalDate> periodDates = extractDates(explicitPeriod);
            if (periodDates.size() >= 2 && !periodDates.get(0).isAfter(periodDates.get(1))) {
                periodStart = periodDates.get(0);
                periodEnd = periodDates.get(1);
                confidence.put(PERIOD_START, LABELLED_HEADER_CONFIDENCE);
                confidence.put(PERIOD_END, LABELLED_HEADER_CONFIDENCE);
            }
        }
        if (periodStart == null) {
            periodStart = firstDate(firstMatchingValue(headerLines, PERIOD_START_PATTERN));
            if (periodStart != null) {
                confidence.put(PERIOD_START, LABELLED_HEADER_CONFIDENCE);
            }
        }
        if (periodEnd == null) {
            periodEnd = firstDate(firstMatchingValue(headerLines, PERIOD_END_PATTERN));
            if (periodEnd != null) {
                confidence.put(PERIOD_END, LABELLED_HEADER_CONFIDENCE);
            }
        }
        if (periodStart != null && periodEnd != null && periodStart.isAfter(periodEnd)) {
            confidence.remove(PERIOD_START);
            confidence.remove(PERIOD_END);
            return new LocalDate[] { null, null };
        }
        return new LocalDate[] { periodStart, periodEnd };
    }

    private static Pattern labelledPattern(String... labels) {
        String joined = String.join("|", labels);
        return Pattern.compile("^(?:" + joined + ")\\s*[:\\-]\\s*(.+)$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private String inferLanguage(String searchableTitle, String headerText) {
        String combined = joinSearchText(searchableTitle, headerText).toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(combined)) {
            return null;
        }
        if (containsAny(combined, " kk ", " kz ", " kazakh ", " қазақ ", " қазақстан ")) {
            return "kk";
        }
        if (containsAny(combined, " ru ", " rus ", " russian ", " русский ", " рус ")) {
            return "ru";
        }
        if (containsAny(combined, " en ", " eng ", " english ")) {
            return "en";
        }
        return inferLanguageFromScript(headerText);
    }

    private double languageHintConfidence(String searchableTitle) {
        String combined = searchableTitle == null ? "" : searchableTitle.toLowerCase(Locale.ROOT);
        return containsAny(combined, " kk ", " kz ", " ru ", " rus ", " en ", " eng ")
            ? REGEX_HINT_CONFIDENCE
            : LANGUAGE_HEURISTIC_CONFIDENCE;
    }

    private LocalDate inferDocumentDate(String searchableTitle, List<String> headerLines) {
        for (String line : headerLines) {
            Matcher matcher = LABELLED_DATE_PATTERN.matcher(line);
            if (matcher.matches()) {
                LocalDate labelledDate = firstDate(matcher.group(1));
                if (labelledDate != null) {
                    return labelledDate;
                }
            }
        }
        return firstDate(searchableTitle);
    }

    private double documentDateHintConfidence(List<String> headerLines) {
        return headerLines.stream().anyMatch(line -> LABELLED_DATE_PATTERN.matcher(line).matches())
            ? LABELLED_HEADER_CONFIDENCE
            : REGEX_HINT_CONFIDENCE;
    }

    private String inferDocumentNumber(String searchableTitle, List<String> headerLines, String headerText) {
        for (String line : headerLines) {
            Matcher matcher = LABELLED_DOCUMENT_NUMBER_PATTERN.matcher(line);
            if (matcher.matches()) {
                String labelledNumber = normalizeDocumentNumberCandidate(matcher.group(1));
                if (StringUtils.hasText(labelledNumber)) {
                    return labelledNumber;
                }
            }
        }
        return firstDocumentNumber(joinSearchText(searchableTitle, headerText));
    }

    private double documentNumberHintConfidence(List<String> headerLines) {
        return headerLines.stream().anyMatch(line -> LABELLED_DOCUMENT_NUMBER_PATTERN.matcher(line).matches())
            ? LABELLED_HEADER_CONFIDENCE
            : REGEX_HINT_CONFIDENCE;
    }

    private String inferVersionLabel(String searchableTitle, List<String> headerLines, String headerText) {
        for (String line : headerLines) {
            String labelledVersion = firstMatchingValue(List.of(line), VERSION_LABEL_PATTERN);
            if (StringUtils.hasText(labelledVersion)) {
                return normalizeVersionLabel(labelledVersion);
            }
        }
        return firstVersion(joinSearchText(searchableTitle, headerText));
    }

    private double versionLabelHintConfidence(List<String> headerLines) {
        return headerLines.stream().anyMatch(line -> VERSION_LABEL_PATTERN.matcher(line).matches())
            ? LABELLED_HEADER_CONFIDENCE
            : REGEX_HINT_CONFIDENCE;
    }

    private List<String> inferTags(String title, String originalFileName, String sourceType) {
        String tagSource = "file".equalsIgnoreCase(normalizeText(sourceType))
            ? joinSearchText(title, normalizeFileStem(originalFileName))
            : title;
        if (!StringUtils.hasText(tagSource)) {
            return List.of();
        }
        Set<String> candidates = new LinkedHashSet<>();
        for (String token : tagSource.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (token.isBlank() || token.length() < 3 || token.chars().allMatch(Character::isDigit)) {
                continue;
            }
            if (TAG_STOPWORDS.contains(token) || VERSION_PATTERN.matcher(token).find()) {
                continue;
            }
            candidates.add(token);
            if (candidates.size() == MAX_INFERRED_TAGS) {
                break;
            }
        }
        return candidates.size() >= 2 || "file".equalsIgnoreCase(normalizeText(sourceType))
            ? List.copyOf(candidates)
            : List.of();
    }

    private String inferLanguageFromScript(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        int latinLetters = 0;
        int cyrillicLetters = 0;
        int kazakhSpecificLetters = 0;
        for (char character : text.toCharArray()) {
            Character.UnicodeScript script = Character.UnicodeScript.of(character);
            if (script == Character.UnicodeScript.LATIN && Character.isLetter(character)) {
                latinLetters++;
            } else if (script == Character.UnicodeScript.CYRILLIC && Character.isLetter(character)) {
                cyrillicLetters++;
                if ("ӘәҒғҚқҢңӨөҰұҮүІі".indexOf(character) >= 0) {
                    kazakhSpecificLetters++;
                }
            }
        }
        if (kazakhSpecificLetters > 0) {
            return "kk";
        }
        if (cyrillicLetters >= latinLetters && cyrillicLetters >= 6) {
            return "ru";
        }
        if (latinLetters > cyrillicLetters && latinLetters >= 6) {
            return "en";
        }
        return null;
    }

    private List<String> headerLines(String contentHeader) {
        if (!StringUtils.hasText(contentHeader)) {
            return List.of();
        }
        String clipped = contentHeader.length() > HEADER_CHAR_LIMIT
            ? contentHeader.substring(0, HEADER_CHAR_LIMIT)
            : contentHeader;
        List<String> lines = new ArrayList<>();
        for (String rawLine : clipped.replace("\r\n", "\n").split("\n")) {
            String normalizedLine = normalizeText(rawLine);
            if (!StringUtils.hasText(normalizedLine)) {
                continue;
            }
            lines.add(normalizedLine);
            if (lines.size() == HEADER_LINE_LIMIT) {
                break;
            }
        }
        return List.copyOf(lines);
    }

    private String firstMatchingValue(List<String> headerLines, Pattern pattern) {
        for (String line : headerLines) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.matches()) {
                return normalizeText(matcher.group(1));
            }
        }
        return null;
    }

    private LocalDate firstDate(String text) {
        List<LocalDate> dates = extractDates(text);
        return dates.isEmpty() ? null : dates.getFirst();
    }

    private List<LocalDate> extractDates(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        List<LocalDate> dates = new ArrayList<>();
        appendDates(dates, ISO_DATE_PATTERN.matcher(text), true);
        appendDates(dates, DMY_DATE_PATTERN.matcher(text), false);
        return dates.stream().distinct().toList();
    }

    private void appendDates(List<LocalDate> dates, Matcher matcher, boolean iso) {
        while (matcher.find()) {
            LocalDate parsedDate = iso
                ? safeDate(matcher.group(1), matcher.group(2), matcher.group(3))
                : safeDate(matcher.group(3), matcher.group(2), matcher.group(1));
            if (parsedDate != null) {
                dates.add(parsedDate);
            }
        }
    }

    private LocalDate safeDate(String year, String month, String day) {
        try {
            return LocalDate.of(Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day));
        } catch (DateTimeException | NumberFormatException exception) {
            return null;
        }
    }

    private String firstDocumentNumber(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        Matcher matcher = DOCUMENT_NUMBER_PATTERN.matcher(text);
        while (matcher.find()) {
            String candidate = normalizeDocumentNumberCandidate(matcher.group(1));
            if (StringUtils.hasText(candidate) && containsLetter(candidate) && containsDigit(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private String normalizeDocumentNumberCandidate(String candidate) {
        if (!StringUtils.hasText(candidate)) {
            return null;
        }
        String normalized = candidate.trim()
            .replaceAll("^[№#:\\-\\s]+", "")
            .replaceAll("[,.;]+$", "");
        return normalized.isBlank() ? null : normalized;
    }

    private String firstVersion(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        Matcher matcher = VERSION_PATTERN.matcher(text);
        return matcher.find() ? normalizeVersionLabel(matcher.group(1)) : null;
    }

    private String normalizeVersionLabel(String rawValue) {
        String normalized = normalizeText(rawValue);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        return normalized.startsWith("v") ? normalized : "v" + normalized;
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeFileStem(String originalFileName) {
        if (!StringUtils.hasText(originalFileName)) {
            return null;
        }
        String trimmed = originalFileName.trim();
        int extensionSeparator = trimmed.lastIndexOf('.');
        return extensionSeparator > 0 ? trimmed.substring(0, extensionSeparator) : trimmed;
    }

    private String joinSearchText(String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                parts.add(value);
            }
        }
        return String.join(" ", parts);
    }

    private boolean containsAny(String text, String... tokens) {
        String padded = " " + text + " ";
        for (String token : tokens) {
            if (padded.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsLetter(String value) {
        return value.codePoints().anyMatch(Character::isLetter);
    }

    private boolean containsDigit(String value) {
        return value.codePoints().anyMatch(Character::isDigit);
    }
}
