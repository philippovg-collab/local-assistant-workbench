package com.example.demo.model;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record MaterialMetadataSnapshot(
    DocumentType documentType,
    KnowledgeDocumentClass knowledgeDocumentClass,
    LocalDate documentDate,
    String documentNumber,
    String author,
    String department,
    String versionLabel,
    String language,
    MaterialLanguageCode languageCode,
    List<String> tags,
    List<String> manualTags,
    List<String> autoTags,
    List<String> effectiveTags,
    SourceTrustLevel sourceTrust,
    String project,
    String projectKey,
    String workspaceKey,
    String counterparty,
    String businessStatus,
    DocumentStatus documentStatus,
    LocalDate periodStart,
    LocalDate periodEnd,
    MaterialMetadataProvenance provenance
) {

    private static final String DOCUMENT_TYPE = "documentType";
    private static final String SOURCE_TRUST = "sourceTrust";
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
    private static final String PROJECT = "project";
    private static final String PROJECT_KEY = "projectKey";
    private static final String KNOWLEDGE_DOCUMENT_CLASS = "knowledgeDocumentClass";
    private static final String WORKSPACE_KEY = "workspaceKey";
    private static final String COUNTERPARTY = "counterparty";
    private static final String BUSINESS_STATUS = "businessStatus";
    private static final String DOCUMENT_STATUS = "documentStatus";
    private static final String PERIOD_START = "periodStart";
    private static final String PERIOD_END = "periodEnd";

    public MaterialMetadataSnapshot {
        DocumentType rawDocumentType = documentType;
        String rawWorkspaceKey = workspaceKey;

        provenance = provenance == null ? MaterialMetadataProvenance.empty() : provenance;
        documentType = documentType == null ? DocumentType.OTHER : documentType;
        knowledgeDocumentClass = deriveKnowledgeDocumentClass(documentType);
        documentNumber = normalizeText(documentNumber);
        author = normalizeText(author);
        department = normalizeText(department);
        versionLabel = normalizeText(versionLabel);
        languageCode = languageCode == null ? parseLegacyLanguageCode(language) : languageCode;
        language = languageCode == null ? normalizeText(language) : languageCode.name().toLowerCase(Locale.ROOT);
        manualTags = normalizeTags(manualTags);
        autoTags = normalizeAutoTags(autoTags, manualTags);
        effectiveTags = normalizeTags(effectiveTags);
        tags = normalizeTags(tags);
        if (manualTags.isEmpty() && !tags.isEmpty() && MetadataValueOrigin.MANUAL.equals(provenance.fieldOrigins().get(TAGS))) {
            manualTags = tags;
        }
        if (autoTags.isEmpty() && !tags.isEmpty() && MetadataValueOrigin.INFERRED.equals(provenance.fieldOrigins().get(TAGS))) {
            autoTags = normalizeAutoTags(tags, manualTags);
        }
        List<String> unionTags = unionTags(manualTags, autoTags);
        effectiveTags = unionTags.isEmpty() ? effectiveTags : unionTags;
        tags = effectiveTags;
        sourceTrust = sourceTrust == null ? SourceTrustLevel.UNKNOWN : sourceTrust;
        project = normalizeText(project);
        projectKey = normalizeText(projectKey);
        workspaceKey = normalizeText(rawWorkspaceKey);
        counterparty = normalizeText(counterparty);
        businessStatus = normalizeText(businessStatus);
        documentStatus = documentStatus == null ? parseLegacyDocumentStatus(businessStatus) : documentStatus;
        documentStatus = documentStatus == null ? DocumentStatus.ACTIVE : documentStatus;
        provenance = withDerivedOrigins(provenance, rawDocumentType, rawWorkspaceKey, workspaceKey, documentStatus);
    }

    public MaterialMetadataSnapshot(
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
        LocalDate periodEnd,
        MaterialMetadataProvenance provenance
    ) {
        this(
            documentType,
            knowledgeDocumentClass,
            documentDate,
            documentNumber,
            author,
            department,
            versionLabel,
            language,
            parseLegacyLanguageCode(language),
            tags,
            tags,
            List.of(),
            tags,
            sourceTrust,
            project,
            null,
            workspaceKey,
            counterparty,
            businessStatus,
            parseLegacyDocumentStatus(businessStatus),
            periodStart,
            periodEnd,
            provenance
        );
    }

    public static MaterialMetadataSnapshot empty() {
        return fromInput(null);
    }

    public static MaterialMetadataSnapshot fromInput(MaterialMetadataInput input) {
        if (input == null) {
            return new MaterialMetadataSnapshot(
                DocumentType.OTHER,
                KnowledgeDocumentClass.OTHER,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                SourceTrustLevel.UNKNOWN,
                null,
                null,
                null,
                null,
                null,
                DocumentStatus.ACTIVE,
                null,
                null,
                new MaterialMetadataProvenance(
                    Map.of(
                        DOCUMENT_TYPE, MetadataValueOrigin.DEFAULT,
                        KNOWLEDGE_DOCUMENT_CLASS, MetadataValueOrigin.DEFAULT,
                        DOCUMENT_STATUS, MetadataValueOrigin.DEFAULT,
                        SOURCE_TRUST, MetadataValueOrigin.DEFAULT
                    ),
                    Map.of()
                )
            );
        }

        Map<String, MetadataValueOrigin> origins = new LinkedHashMap<>();
        DocumentType resolvedDocumentType = input.documentType() == null ? DocumentType.OTHER : input.documentType();
        DocumentStatus resolvedDocumentStatus = input.effectiveDocumentStatus();
        SourceTrustLevel resolvedSourceTrust = SourceTrustLevel.UNKNOWN;

        origins.put(DOCUMENT_TYPE, input.documentType() == null ? MetadataValueOrigin.DEFAULT : MetadataValueOrigin.MANUAL);
        origins.put(
            KNOWLEDGE_DOCUMENT_CLASS,
            input.documentType() == null ? MetadataValueOrigin.DEFAULT : MetadataValueOrigin.INFERRED
        );
        origins.put(DOCUMENT_STATUS, input.documentStatus() == null ? MetadataValueOrigin.DEFAULT : MetadataValueOrigin.MANUAL);
        origins.put(SOURCE_TRUST, MetadataValueOrigin.DEFAULT);
        putIfPresent(origins, DOCUMENT_DATE, input.documentDate());
        putIfPresent(origins, DOCUMENT_NUMBER, input.documentNumber());
        putIfPresent(origins, AUTHOR, input.author());
        putIfPresent(origins, DEPARTMENT, input.department());
        putIfPresent(origins, VERSION_LABEL, input.versionLabel());
        putIfPresent(origins, LANGUAGE_CODE, input.languageCode());
        putIfPresent(origins, LANGUAGE, input.language());
        if (input.effectiveManualTags() != null && !input.effectiveManualTags().isEmpty()) {
            origins.put(TAGS, MetadataValueOrigin.MANUAL);
            origins.put(MANUAL_TAGS, MetadataValueOrigin.MANUAL);
            origins.put(EFFECTIVE_TAGS, MetadataValueOrigin.MANUAL);
        }
        putIfPresent(origins, PROJECT_KEY, input.projectKey());
        putIfPresent(origins, PROJECT, input.project());
        putIfPresent(origins, WORKSPACE_KEY, input.workspaceKey());
        putIfPresent(origins, COUNTERPARTY, input.counterparty());
        putIfPresent(origins, BUSINESS_STATUS, input.businessStatus());
        putIfPresent(origins, PERIOD_START, input.periodStart());
        putIfPresent(origins, PERIOD_END, input.periodEnd());

        return new MaterialMetadataSnapshot(
            resolvedDocumentType,
            deriveKnowledgeDocumentClass(resolvedDocumentType),
            input.documentDate(),
            input.documentNumber(),
            input.author(),
            input.department(),
            input.versionLabel(),
            input.language(),
            input.languageCode(),
            input.effectiveManualTags(),
            input.effectiveManualTags(),
            List.of(),
            input.effectiveManualTags(),
            resolvedSourceTrust,
            input.project(),
            input.projectKey(),
            input.workspaceKey(),
            input.counterparty(),
            input.businessStatus(),
            resolvedDocumentStatus,
            input.periodStart(),
            input.periodEnd(),
            new MaterialMetadataProvenance(origins, Map.of())
        );
    }

    public MaterialMetadataSnapshot withTags(List<String> newTags) {
        List<String> normalizedTags = normalizeTags(newTags);
        List<String> nextManualTags = !MetadataValueOrigin.INFERRED.equals(provenance.fieldOrigins().get(TAGS))
            ? normalizedTags
            : manualTags;
        List<String> nextAutoTags = MetadataValueOrigin.INFERRED.equals(provenance.fieldOrigins().get(TAGS))
            ? normalizedTags
            : autoTags;
        return withTagLayers(nextManualTags, nextAutoTags);
    }

    public MaterialMetadataSnapshot withManualTags(List<String> newManualTags) {
        return withTagLayers(newManualTags, autoTags);
    }

    public MaterialMetadataSnapshot withTagLayers(List<String> newManualTags, List<String> newAutoTags) {
        List<String> nextManualTags = normalizeTags(newManualTags);
        List<String> nextAutoTags = normalizeAutoTags(newAutoTags, nextManualTags);
        List<String> nextEffectiveTags = unionTags(nextManualTags, nextAutoTags);
        return new MaterialMetadataSnapshot(
            documentType,
            knowledgeDocumentClass,
            documentDate,
            documentNumber,
            author,
            department,
            versionLabel,
            language,
            languageCode,
            nextEffectiveTags,
            nextManualTags,
            nextAutoTags,
            nextEffectiveTags,
            sourceTrust,
            project,
            projectKey,
            workspaceKey,
            counterparty,
            businessStatus,
            documentStatus,
            periodStart,
            periodEnd,
            provenance
        );
    }

    public MaterialMetadataSnapshot withDerivedFields(
        KnowledgeDocumentClass updatedKnowledgeDocumentClass,
        String updatedWorkspaceKey
    ) {
        return new MaterialMetadataSnapshot(
            documentType,
            updatedKnowledgeDocumentClass,
            documentDate,
            documentNumber,
            author,
            department,
            versionLabel,
            language,
            languageCode,
            tags,
            manualTags,
            autoTags,
            effectiveTags,
            sourceTrust,
            project,
            projectKey,
            updatedWorkspaceKey,
            counterparty,
            businessStatus,
            documentStatus,
            periodStart,
            periodEnd,
            provenance
        );
    }

    public static KnowledgeDocumentClass deriveKnowledgeDocumentClass(DocumentType documentType) {
        DocumentType safeDocumentType = documentType == null ? DocumentType.OTHER : documentType;
        return switch (safeDocumentType) {
            case CONTRACT -> KnowledgeDocumentClass.CONTRACTS;
            case POLICY, PROCEDURE -> KnowledgeDocumentClass.REGULATIONS;
            case LETTER -> KnowledgeDocumentClass.CORRESPONDENCE;
            case MANUAL, FAQ, PRESENTATION, SPREADSHEET, REPORT -> KnowledgeDocumentClass.TECHDOCS;
            case OTHER -> KnowledgeDocumentClass.OTHER;
        };
    }

    public static String deriveWorkspaceKey(String project) {
        String normalizedProject = normalizeText(project);
        if (normalizedProject == null) {
            return null;
        }
        String slug = normalizedProject
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^\\p{L}\\p{N}]+", "-")
            .replaceAll("(^-+|-+$)", "");
        return slug.isBlank() ? null : slug;
    }

    private static MaterialMetadataProvenance withDerivedOrigins(
        MaterialMetadataProvenance provenance,
        DocumentType rawDocumentType,
        String rawWorkspaceKey,
        String resolvedWorkspaceKey,
        DocumentStatus resolvedDocumentStatus
    ) {
        Map<String, MetadataValueOrigin> fieldOrigins = new LinkedHashMap<>(provenance.fieldOrigins());
        if (!fieldOrigins.containsKey(DOCUMENT_TYPE) && rawDocumentType == null) {
            fieldOrigins.put(DOCUMENT_TYPE, MetadataValueOrigin.DEFAULT);
        }
        if (!fieldOrigins.containsKey(KNOWLEDGE_DOCUMENT_CLASS)) {
            fieldOrigins.put(
                KNOWLEDGE_DOCUMENT_CLASS,
                rawDocumentType == null ? MetadataValueOrigin.DEFAULT : MetadataValueOrigin.INFERRED
            );
        }
        if (resolvedWorkspaceKey != null && !fieldOrigins.containsKey(WORKSPACE_KEY)) {
            fieldOrigins.put(
                WORKSPACE_KEY,
                normalizeText(rawWorkspaceKey) == null ? MetadataValueOrigin.DEFAULT : MetadataValueOrigin.MANUAL
            );
        }
        if (resolvedDocumentStatus != null && !fieldOrigins.containsKey(DOCUMENT_STATUS)) {
            fieldOrigins.put(DOCUMENT_STATUS, MetadataValueOrigin.DEFAULT);
        }
        return new MaterialMetadataProvenance(fieldOrigins, provenance.fieldConfidence());
    }

    private static void putIfPresent(Map<String, MetadataValueOrigin> origins, String fieldName, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String stringValue && stringValue.isBlank()) {
            return;
        }
        origins.put(fieldName, MetadataValueOrigin.MANUAL);
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

    private static List<String> normalizeAutoTags(List<String> rawAutoTags, List<String> manualTags) {
        List<String> normalizedManualTags = normalizeTags(manualTags);
        if (rawAutoTags == null || rawAutoTags.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> manualSet = new LinkedHashSet<>(normalizedManualTags);
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String tag : rawAutoTags) {
            String candidate = normalizeText(tag);
            if (candidate != null && !manualSet.contains(candidate)) {
                normalized.add(candidate);
            }
        }
        return List.copyOf(normalized);
    }

    private static List<String> unionTags(List<String> manualTags, List<String> autoTags) {
        LinkedHashSet<String> union = new LinkedHashSet<>();
        union.addAll(normalizeTags(manualTags));
        union.addAll(normalizeAutoTags(autoTags, List.copyOf(union)));
        return List.copyOf(union);
    }
}
