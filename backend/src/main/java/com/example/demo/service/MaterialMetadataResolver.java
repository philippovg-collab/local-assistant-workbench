package com.example.demo.service;

import com.example.demo.service.material.MaterialMetadataHints;

import com.example.demo.api.ApiException;
import com.example.demo.service.reference.port.ReferenceDataRepository;
import com.example.demo.service.reference.StoredReferenceProjectRecord;
import com.example.demo.model.DocumentStatus;
import com.example.demo.model.DocumentType;
import com.example.demo.model.KnowledgeDocumentClass;
import com.example.demo.model.MaterialLanguageCode;
import com.example.demo.model.MaterialMetadataInput;
import com.example.demo.model.MaterialMetadataProvenance;
import com.example.demo.model.MaterialMetadataSnapshot;
import com.example.demo.model.MetadataValueOrigin;
import com.example.demo.model.SourceTrustLevel;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class MaterialMetadataResolver {

    private static final String DOCUMENT_TYPE = "documentType";
    private static final String DOCUMENT_DATE = "documentDate";
    private static final String DOCUMENT_NUMBER = "documentNumber";
    private static final String AUTHOR = "author";
    private static final String DEPARTMENT = "department";
    private static final String VERSION_LABEL = "versionLabel";
    private static final String LANGUAGE = "language";
    private static final String LANGUAGE_CODE = "languageCode";
    private static final String TAGS = "tags";
    private static final String MANUAL_TAGS = "manualTags";
    private static final String AUTO_TAGS = "autoTags";
    private static final String EFFECTIVE_TAGS = "effectiveTags";
    private static final String SOURCE_TRUST = "sourceTrust";
    private static final String PROJECT = "project";
    private static final String PROJECT_KEY = "projectKey";
    private static final String KNOWLEDGE_DOCUMENT_CLASS = "knowledgeDocumentClass";
    private static final String WORKSPACE_KEY = "workspaceKey";
    private static final String COUNTERPARTY = "counterparty";
    private static final String BUSINESS_STATUS = "businessStatus";
    private static final String DOCUMENT_STATUS = "documentStatus";
    private static final String PERIOD_START = "periodStart";
    private static final String PERIOD_END = "periodEnd";
    private static final String DEFAULT_WORKSPACE_KEY = "general";

    private static final double LABELLED_HEADER_CONFIDENCE = 0.9d;
    private static final double REGEX_HINT_CONFIDENCE = 0.75d;
    private static final double LANGUAGE_HEURISTIC_CONFIDENCE = 0.6d;
    private static final double TAG_HINT_CONFIDENCE = 0.45d;
    private static final int HEADER_LINE_LIMIT = 12;
    private static final int HEADER_CHAR_LIMIT = 1_200;
    private static final int MAX_INFERRED_TAGS = 3;

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
        "and",
        "brief",
        "contract",
        "document",
        "draft",
        "faq",
        "file",
        "final",
        "guide",
        "letter",
        "manual",
        "material",
        "note",
        "other",
        "pdf",
        "policy",
        "presentation",
        "procedure",
        "report",
        "scan",
        "sheet",
        "spreadsheet",
        "text",
        "txt",
        "updated",
        "version",
        "договор",
        "документ",
        "заметка",
        "инструкция",
        "материал",
        "отчет",
        "политика",
        "письмо",
        "проект",
        "презентация",
        "процедура",
        "редакция",
        "скан",
        "справка",
        "таблица",
        "текст",
        "файл"
    );

    private final ReferenceDataRepository referenceDataRepository;

    @Autowired
    public MaterialMetadataResolver(ReferenceDataRepository referenceDataRepository) {
        this.referenceDataRepository = referenceDataRepository;
    }

    public MaterialMetadataSnapshot resolve(
        MaterialMetadataInput manualInput,
        String title,
        String sourceType,
        String originalFileName,
        String mediaType,
        String contentHeader
    ) {
        return resolve(
            manualInput,
            title,
            sourceType,
            originalFileName,
            mediaType,
            contentHeader,
            MaterialMetadataHints.empty()
        );
    }

    public MaterialMetadataSnapshot resolve(
        MaterialMetadataInput manualInput,
        String title,
        String sourceType,
        String originalFileName,
        String mediaType,
        String contentHeader,
        MaterialMetadataHints parserHints
    ) {
        MaterialMetadataHints regexHints = inferHints(title, sourceType, originalFileName, mediaType, contentHeader);
        return merge(manualInput, mergeHints(parserHints, regexHints));
    }

    private MaterialMetadataHints inferHints(
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

        DocumentType documentType = inferDocumentType(searchableTitle, normalizedFileName, normalizedMediaType);
        if (documentType != null) {
            confidence.put(DOCUMENT_TYPE, REGEX_HINT_CONFIDENCE);
        }

        String language = inferLanguage(searchableTitle, headerText);
        if (StringUtils.hasText(language)) {
            confidence.put(LANGUAGE, languageHintConfidence(searchableTitle));
        }

        LocalDate documentDate = inferDocumentDate(searchableTitle, headerLines, headerText);
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

        String author = firstMatchingValue(headerLines, AUTHOR_PATTERN);
        if (StringUtils.hasText(author)) {
            confidence.put(AUTHOR, LABELLED_HEADER_CONFIDENCE);
        }

        String department = firstMatchingValue(headerLines, DEPARTMENT_PATTERN);
        if (StringUtils.hasText(department)) {
            confidence.put(DEPARTMENT, LABELLED_HEADER_CONFIDENCE);
        }

        String project = firstMatchingValue(headerLines, PROJECT_PATTERN);
        if (StringUtils.hasText(project)) {
            confidence.put(PROJECT, LABELLED_HEADER_CONFIDENCE);
        }

        String counterparty = firstMatchingValue(headerLines, COUNTERPARTY_PATTERN);
        if (StringUtils.hasText(counterparty)) {
            confidence.put(COUNTERPARTY, LABELLED_HEADER_CONFIDENCE);
        }

        String businessStatus = firstMatchingValue(headerLines, STATUS_PATTERN);
        if (StringUtils.hasText(businessStatus)) {
            confidence.put(BUSINESS_STATUS, LABELLED_HEADER_CONFIDENCE);
        }

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
            String explicitPeriodStart = firstMatchingValue(headerLines, PERIOD_START_PATTERN);
            LocalDate inferredPeriodStart = firstDate(explicitPeriodStart);
            if (inferredPeriodStart != null) {
                periodStart = inferredPeriodStart;
                confidence.put(PERIOD_START, LABELLED_HEADER_CONFIDENCE);
            }
        }

        if (periodEnd == null) {
            String explicitPeriodEnd = firstMatchingValue(headerLines, PERIOD_END_PATTERN);
            LocalDate inferredPeriodEnd = firstDate(explicitPeriodEnd);
            if (inferredPeriodEnd != null) {
                periodEnd = inferredPeriodEnd;
                confidence.put(PERIOD_END, LABELLED_HEADER_CONFIDENCE);
            }
        }

        if (periodStart != null && periodEnd != null && periodStart.isAfter(periodEnd)) {
            periodStart = null;
            periodEnd = null;
            confidence.remove(PERIOD_START);
            confidence.remove(PERIOD_END);
        }

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
            periodStart,
            periodEnd,
            confidence
        );
    }

    private MaterialMetadataHints mergeHints(MaterialMetadataHints primaryHints, MaterialMetadataHints fallbackHints) {
        MaterialMetadataHints primary = primaryHints == null ? MaterialMetadataHints.empty() : primaryHints;
        MaterialMetadataHints fallback = fallbackHints == null ? MaterialMetadataHints.empty() : fallbackHints;
        Map<String, Double> mergedConfidence = new LinkedHashMap<>(fallback.fieldConfidence());
        mergedConfidence.putAll(primary.fieldConfidence());

        return new MaterialMetadataHints(
            chooseHint(primary.documentType(), fallback.documentType()),
            chooseHint(primary.documentDate(), fallback.documentDate()),
            chooseHint(primary.documentNumber(), fallback.documentNumber()),
            chooseHint(primary.author(), fallback.author()),
            chooseHint(primary.department(), fallback.department()),
            chooseHint(primary.versionLabel(), fallback.versionLabel()),
            chooseHint(primary.language(), fallback.language()),
            !primary.tags().isEmpty() ? primary.tags() : fallback.tags(),
            chooseHint(primary.project(), fallback.project()),
            chooseHint(primary.counterparty(), fallback.counterparty()),
            chooseHint(primary.businessStatus(), fallback.businessStatus()),
            chooseHint(primary.periodStart(), fallback.periodStart()),
            chooseHint(primary.periodEnd(), fallback.periodEnd()),
            mergedConfidence
        );
    }

    private MaterialMetadataSnapshot merge(MaterialMetadataInput manualInput, MaterialMetadataHints hints) {
        DocumentType documentType = chooseDocumentType(manualInput, hints);
        KnowledgeDocumentClass knowledgeDocumentClass = MaterialMetadataSnapshot.deriveKnowledgeDocumentClass(documentType);
        SourceTrustLevel sourceTrust = SourceTrustLevel.UNKNOWN;
        String workspaceKey = resolveWorkspaceKey(manualInput);
        String projectKey = resolveProjectKey(manualInput, workspaceKey);
        String project = chooseString(manualInput != null ? manualInput.project() : null, hints.project());
        String businessStatus = chooseString(
            manualInput != null ? manualInput.businessStatus() : null,
            hints.businessStatus()
        );
        DocumentStatus documentStatus = manualInput != null && manualInput.documentStatus() != null
            ? manualInput.documentStatus()
            : DocumentStatus.ACTIVE;
        MaterialLanguageCode languageCode = manualInput != null && manualInput.languageCode() != null
            ? manualInput.languageCode()
            : languageCodeFromHint(hints.language());
        String language = languageCode == null ? null : languageCode.name().toLowerCase(Locale.ROOT);

        Map<String, MetadataValueOrigin> origins = new LinkedHashMap<>();
        Map<String, Double> confidence = new LinkedHashMap<>();

        recordFieldOrigin(origins, confidence, DOCUMENT_TYPE, manualInput != null ? manualInput.documentType() : null, hints.documentType(), hints);
        recordFieldOrigin(origins, confidence, DOCUMENT_DATE, null, hints.documentDate(), hints);
        recordFieldOrigin(origins, confidence, DOCUMENT_NUMBER, manualInput != null ? manualInput.documentNumber() : null, hints.documentNumber(), hints);
        recordFieldOrigin(origins, confidence, AUTHOR, null, hints.author(), hints);
        recordFieldOrigin(origins, confidence, DEPARTMENT, null, hints.department(), hints);
        recordFieldOrigin(origins, confidence, VERSION_LABEL, null, hints.versionLabel(), hints);
        recordFieldOrigin(origins, confidence, LANGUAGE_CODE, manualInput != null ? manualInput.languageCode() : null, languageCodeFromHint(hints.language()), hints);
        if (languageCode != null) {
            origins.putIfAbsent(
                LANGUAGE,
                manualInput != null && manualInput.languageCode() != null
                    ? MetadataValueOrigin.MANUAL
                    : MetadataValueOrigin.INFERRED
            );
            if ((manualInput == null || manualInput.languageCode() == null) && hints.fieldConfidence().containsKey(LANGUAGE)) {
                confidence.put(LANGUAGE, hints.fieldConfidence().get(LANGUAGE));
                confidence.put(LANGUAGE_CODE, hints.fieldConfidence().get(LANGUAGE));
            }
        }

        List<String> manualTags = manualInput == null ? List.of() : manualInput.effectiveManualTags();
        List<String> autoTags = autoOnlyTags(hints.tags(), manualTags);
        List<String> tags = unionTags(manualTags, autoTags);
        if (!manualTags.isEmpty()) {
            origins.put(TAGS, MetadataValueOrigin.MANUAL);
            origins.put(MANUAL_TAGS, MetadataValueOrigin.MANUAL);
            origins.put(EFFECTIVE_TAGS, MetadataValueOrigin.MANUAL);
        } else if (!autoTags.isEmpty()) {
            origins.put(TAGS, MetadataValueOrigin.INFERRED);
            origins.put(EFFECTIVE_TAGS, MetadataValueOrigin.INFERRED);
        }
        if (!autoTags.isEmpty()) {
            origins.put(AUTO_TAGS, MetadataValueOrigin.INFERRED);
            confidence.put(AUTO_TAGS, hints.fieldConfidence().getOrDefault(TAGS, TAG_HINT_CONFIDENCE));
            confidence.put(TAGS, hints.fieldConfidence().getOrDefault(TAGS, TAG_HINT_CONFIDENCE));
        }

        if ((manualInput != null && manualInput.documentType() != null) || hints.documentType() != null) {
            origins.put(KNOWLEDGE_DOCUMENT_CLASS, MetadataValueOrigin.INFERRED);
        } else {
            origins.put(KNOWLEDGE_DOCUMENT_CLASS, MetadataValueOrigin.DEFAULT);
        }

        origins.put(SOURCE_TRUST, MetadataValueOrigin.DEFAULT);
        if (documentStatus == DocumentStatus.ACTIVE && (manualInput == null || manualInput.documentStatus() == null)) {
            origins.put(DOCUMENT_STATUS, MetadataValueOrigin.DEFAULT);
        } else {
            origins.put(DOCUMENT_STATUS, MetadataValueOrigin.MANUAL);
        }
        if (projectKey != null) {
            origins.put(
                PROJECT_KEY,
                manualInput != null && manualInput.projectKey() != null
                    ? MetadataValueOrigin.MANUAL
                    : MetadataValueOrigin.MANUAL
            );
        }
        recordFieldOrigin(origins, confidence, PROJECT, manualInput != null ? manualInput.project() : null, hints.project(), hints);
        if (manualInput != null && manualInput.workspaceKey() != null) {
            origins.put(WORKSPACE_KEY, MetadataValueOrigin.MANUAL);
        } else {
            origins.put(WORKSPACE_KEY, MetadataValueOrigin.DEFAULT);
        }
        recordFieldOrigin(origins, confidence, COUNTERPARTY, null, hints.counterparty(), hints);
        recordFieldOrigin(
            origins,
            confidence,
            BUSINESS_STATUS,
            manualInput != null ? manualInput.businessStatus() : null,
            hints.businessStatus(),
            hints
        );
        recordFieldOrigin(origins, confidence, PERIOD_START, manualInput != null ? manualInput.periodStart() : null, hints.periodStart(), hints);
        recordFieldOrigin(origins, confidence, PERIOD_END, manualInput != null ? manualInput.periodEnd() : null, hints.periodEnd(), hints);

        Object documentTypeSource = manualInput != null ? manualInput.documentType() : null;
        if (documentTypeSource == null && hints.documentType() == null) {
            origins.put(DOCUMENT_TYPE, MetadataValueOrigin.DEFAULT);
        }

        return new MaterialMetadataSnapshot(
            documentType,
            knowledgeDocumentClass,
            hints.documentDate(),
            chooseString(manualInput != null ? manualInput.documentNumber() : null, hints.documentNumber()),
            hints.author(),
            hints.department(),
            hints.versionLabel(),
            language,
            languageCode,
            tags,
            manualTags,
            autoTags,
            tags,
            sourceTrust,
            project,
            projectKey,
            workspaceKey,
            hints.counterparty(),
            businessStatus,
            documentStatus,
            manualInput != null && manualInput.periodStart() != null ? manualInput.periodStart() : hints.periodStart(),
            manualInput != null && manualInput.periodEnd() != null ? manualInput.periodEnd() : hints.periodEnd(),
            new MaterialMetadataProvenance(origins, confidence)
        );
    }

    private String resolveWorkspaceKey(MaterialMetadataInput manualInput) {
        String workspaceKey = manualInput != null && manualInput.workspaceKey() != null
            ? manualInput.workspaceKey()
            : DEFAULT_WORKSPACE_KEY;
        if (referenceDataRepository != null && !referenceDataRepository.workspaceExists(workspaceKey)) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "material.metadata.workspace_not_found",
                "Reference workspace '" + workspaceKey + "' does not exist"
            );
        }
        return workspaceKey;
    }

    private String resolveProjectKey(MaterialMetadataInput manualInput, String workspaceKey) {
        if (manualInput == null) {
            return null;
        }
        if (manualInput.projectKey() != null) {
            return requireProjectInWorkspace(manualInput.projectKey(), workspaceKey);
        }
        if (manualInput.project() == null || referenceDataRepository == null) {
            return null;
        }
        return referenceDataRepository.findProjectByKey(manualInput.project())
            .filter(project -> project.workspaceKey().equals(workspaceKey))
            .map(StoredReferenceProjectRecord::key)
            .orElse(null);
    }

    private String requireProjectInWorkspace(String projectKey, String workspaceKey) {
        if (referenceDataRepository == null) {
            return projectKey;
        }
        StoredReferenceProjectRecord project = referenceDataRepository.findProjectByKey(projectKey)
            .orElseThrow(() -> new ApiException(
                HttpStatus.BAD_REQUEST,
                "material.metadata.project_not_found",
                "Reference project '" + projectKey + "' does not exist"
            ));
        if (!project.workspaceKey().equals(workspaceKey)) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "material.metadata.project_workspace_mismatch",
                "Reference project '" + projectKey + "' does not belong to workspace '" + workspaceKey + "'"
            );
        }
        return project.key();
    }

    private MaterialLanguageCode languageCodeFromHint(String language) {
        String normalizedLanguage = normalizeText(language);
        if (normalizedLanguage == null) {
            return null;
        }
        return switch (normalizedLanguage.toLowerCase(Locale.ROOT)) {
            case "ru", "rus", "russian" -> MaterialLanguageCode.RU;
            case "kk", "kz", "kaz", "kazakh" -> MaterialLanguageCode.KK;
            case "en", "eng", "english" -> MaterialLanguageCode.EN;
            default -> null;
        };
    }

    private static Pattern labelledPattern(String... labels) {
        String joined = String.join("|", labels);
        return Pattern.compile("^(?:" + joined + ")\\s*[:\\-]\\s*(.+)$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private DocumentType chooseDocumentType(MaterialMetadataInput manualInput, MaterialMetadataHints hints) {
        if (manualInput != null && manualInput.documentType() != null) {
            return manualInput.documentType();
        }
        if (hints.documentType() != null) {
            return hints.documentType();
        }
        return DocumentType.OTHER;
    }

    private void recordFieldOrigin(
        Map<String, MetadataValueOrigin> origins,
        Map<String, Double> confidence,
        String field,
        Object manualValue,
        Object inferredValue,
        MaterialMetadataHints hints
    ) {
        if (isPresent(manualValue)) {
            origins.put(field, MetadataValueOrigin.MANUAL);
            return;
        }
        if (isPresent(inferredValue)) {
            origins.put(field, MetadataValueOrigin.INFERRED);
            Double hintedConfidence = hints.fieldConfidence().get(field);
            if (hintedConfidence != null) {
                confidence.put(field, hintedConfidence);
            }
        }
    }

    private DocumentType inferDocumentType(String searchableTitle, String originalFileName, String mediaType) {
        String combined = joinSearchText(searchableTitle, normalizeFileStem(originalFileName), mediaType);
        if (!StringUtils.hasText(combined)) {
            return null;
        }
        String normalized = combined.toLowerCase(Locale.ROOT);
        if (normalized.contains("faq") || normalized.contains("вопрос") || normalized.contains("ответ")) {
            return DocumentType.FAQ;
        }
        if (normalized.contains("policy") || normalized.contains("политик") || normalized.contains("регламент")) {
            return DocumentType.POLICY;
        }
        if (normalized.contains("contract") || normalized.contains("agreement") || normalized.contains("договор")) {
            return DocumentType.CONTRACT;
        }
        if (normalized.contains("report") || normalized.contains("отчет") || normalized.contains("отчёт")) {
            return DocumentType.REPORT;
        }
        if (normalized.contains("procedure") || normalized.contains("процедур") || normalized.contains("порядок")) {
            return DocumentType.PROCEDURE;
        }
        if (normalized.contains("manual") || normalized.contains("guide") || normalized.contains("руководств")) {
            return DocumentType.MANUAL;
        }
        if (normalized.contains("letter") || normalized.contains("letterhead") || normalized.contains("письмо")) {
            return DocumentType.LETTER;
        }
        if (normalized.contains(".ppt") || normalized.contains(".pptx")
            || normalized.contains("presentation") || normalized.contains("презентац")) {
            return DocumentType.PRESENTATION;
        }
        if (normalized.contains(".xls") || normalized.contains(".xlsx") || normalized.contains(".csv")
            || normalized.contains("spreadsheet") || normalized.contains("table") || normalized.contains("таблиц")) {
            return DocumentType.SPREADSHEET;
        }
        return null;
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

    private LocalDate inferDocumentDate(String searchableTitle, List<String> headerLines, String headerText) {
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
            return LocalDate.of(
                Integer.parseInt(year),
                Integer.parseInt(month),
                Integer.parseInt(day)
            );
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
        if (normalized.isBlank()) {
            return null;
        }
        return normalized;
    }

    private String firstVersion(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        Matcher matcher = VERSION_PATTERN.matcher(text);
        if (matcher.find()) {
            return normalizeVersionLabel(matcher.group(1));
        }
        return null;
    }

    private String normalizeVersionLabel(String rawValue) {
        String normalized = normalizeText(rawValue);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        return normalized.startsWith("v") ? normalized : "v" + normalized;
    }

    private String chooseString(String manualValue, String inferredValue) {
        return StringUtils.hasText(manualValue) ? manualValue : inferredValue;
    }

    private <T> T chooseHint(T preferredValue, T fallbackValue) {
        return isPresent(preferredValue) ? preferredValue : fallbackValue;
    }

    private static List<String> autoOnlyTags(List<String> rawAutoTags, List<String> manualTags) {
        if (rawAutoTags == null || rawAutoTags.isEmpty()) {
            return List.of();
        }

        Set<String> manualSet = new LinkedHashSet<>();
        if (manualTags != null) {
            for (String tag : manualTags) {
                String normalized = normalizeText(tag);
                if (normalized != null) {
                    manualSet.add(normalized);
                }
            }
        }

        Set<String> normalizedAutoTags = new LinkedHashSet<>();
        for (String tag : rawAutoTags) {
            String normalized = normalizeText(tag);
            if (normalized != null && !manualSet.contains(normalized)) {
                normalizedAutoTags.add(normalized);
            }
        }
        return List.copyOf(normalizedAutoTags);
    }

    private static List<String> unionTags(List<String> manualTags, List<String> autoTags) {
        Set<String> union = new LinkedHashSet<>();
        if (manualTags != null) {
            for (String tag : manualTags) {
                String normalized = normalizeText(tag);
                if (normalized != null) {
                    union.add(normalized);
                }
            }
        }
        union.addAll(autoOnlyTags(autoTags, List.copyOf(union)));
        return List.copyOf(union);
    }

    private static boolean isPresent(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String stringValue) {
            return StringUtils.hasText(stringValue);
        }
        if (value instanceof List<?> listValue) {
            return !listValue.isEmpty();
        }
        return true;
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
