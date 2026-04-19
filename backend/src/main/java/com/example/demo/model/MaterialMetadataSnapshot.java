package com.example.demo.model;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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

    private static final String DOCUMENT_TYPE = "documentType";
    private static final String SOURCE_TRUST = "sourceTrust";
    private static final String DOCUMENT_DATE = "documentDate";
    private static final String DOCUMENT_NUMBER = "documentNumber";
    private static final String AUTHOR = "author";
    private static final String DEPARTMENT = "department";
    private static final String VERSION_LABEL = "versionLabel";
    private static final String LANGUAGE = "language";
    private static final String TAGS = "tags";
    private static final String PROJECT = "project";
    private static final String KNOWLEDGE_DOCUMENT_CLASS = "knowledgeDocumentClass";
    private static final String WORKSPACE_KEY = "workspaceKey";
    private static final String COUNTERPARTY = "counterparty";
    private static final String BUSINESS_STATUS = "businessStatus";
    private static final String PERIOD_START = "periodStart";
    private static final String PERIOD_END = "periodEnd";

    public MaterialMetadataSnapshot {
        DocumentType rawDocumentType = documentType;
        KnowledgeDocumentClass rawKnowledgeDocumentClass = knowledgeDocumentClass;
        String rawWorkspaceKey = workspaceKey;

        documentType = documentType == null ? DocumentType.OTHER : documentType;
        documentNumber = normalizeText(documentNumber);
        author = normalizeText(author);
        department = normalizeText(department);
        versionLabel = normalizeText(versionLabel);
        language = normalizeText(language);
        tags = normalizeTags(tags);
        sourceTrust = sourceTrust == null ? SourceTrustLevel.UNKNOWN : sourceTrust;
        project = normalizeText(project);
        knowledgeDocumentClass = rawKnowledgeDocumentClass == null
            ? deriveKnowledgeDocumentClass(documentType)
            : rawKnowledgeDocumentClass;
        workspaceKey = normalizeText(rawWorkspaceKey);
        if (workspaceKey == null) {
            workspaceKey = deriveWorkspaceKey(project);
        }
        counterparty = normalizeText(counterparty);
        businessStatus = normalizeText(businessStatus);
        provenance = provenance == null ? MaterialMetadataProvenance.empty() : provenance;
        provenance = withDerivedOrigins(
            provenance,
            rawDocumentType,
            rawKnowledgeDocumentClass,
            rawWorkspaceKey,
            workspaceKey
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
                List.of(),
                SourceTrustLevel.UNKNOWN,
                null,
                null,
                null,
                null,
                null,
                null,
                new MaterialMetadataProvenance(
                    Map.of(
                        DOCUMENT_TYPE, MetadataValueOrigin.DEFAULT,
                        KNOWLEDGE_DOCUMENT_CLASS, MetadataValueOrigin.DEFAULT,
                        SOURCE_TRUST, MetadataValueOrigin.DEFAULT
                    ),
                    Map.of()
                )
            );
        }

        Map<String, MetadataValueOrigin> origins = new LinkedHashMap<>();
        DocumentType resolvedDocumentType = input.documentType() == null ? DocumentType.OTHER : input.documentType();
        KnowledgeDocumentClass resolvedKnowledgeDocumentClass = input.effectiveKnowledgeDocumentClass();
        SourceTrustLevel resolvedSourceTrust = input.sourceTrust() == null ? SourceTrustLevel.UNKNOWN : input.sourceTrust();

        origins.put(DOCUMENT_TYPE, input.documentType() == null ? MetadataValueOrigin.DEFAULT : MetadataValueOrigin.MANUAL);
        origins.put(
            KNOWLEDGE_DOCUMENT_CLASS,
            input.knowledgeDocumentClass() == null ? MetadataValueOrigin.INFERRED : MetadataValueOrigin.MANUAL
        );
        origins.put(SOURCE_TRUST, input.sourceTrust() == null ? MetadataValueOrigin.DEFAULT : MetadataValueOrigin.MANUAL);
        putIfPresent(origins, DOCUMENT_DATE, input.documentDate());
        putIfPresent(origins, DOCUMENT_NUMBER, input.documentNumber());
        putIfPresent(origins, AUTHOR, input.author());
        putIfPresent(origins, DEPARTMENT, input.department());
        putIfPresent(origins, VERSION_LABEL, input.versionLabel());
        putIfPresent(origins, LANGUAGE, input.language());
        if (input.tags() != null && !input.tags().isEmpty()) {
            origins.put(TAGS, MetadataValueOrigin.MANUAL);
        }
        putIfPresent(origins, PROJECT, input.project());
        putIfPresent(origins, WORKSPACE_KEY, input.workspaceKey());
        putIfPresent(origins, COUNTERPARTY, input.counterparty());
        putIfPresent(origins, BUSINESS_STATUS, input.businessStatus());
        putIfPresent(origins, PERIOD_START, input.periodStart());
        putIfPresent(origins, PERIOD_END, input.periodEnd());

        return new MaterialMetadataSnapshot(
            resolvedDocumentType,
            resolvedKnowledgeDocumentClass,
            input.documentDate(),
            input.documentNumber(),
            input.author(),
            input.department(),
            input.versionLabel(),
            input.language(),
            input.tags(),
            resolvedSourceTrust,
            input.project(),
            input.effectiveWorkspaceKey(),
            input.counterparty(),
            input.businessStatus(),
            input.periodStart(),
            input.periodEnd(),
            new MaterialMetadataProvenance(origins, Map.of())
        );
    }

    public MaterialMetadataSnapshot withTags(List<String> newTags) {
        return new MaterialMetadataSnapshot(
            documentType,
            knowledgeDocumentClass,
            documentDate,
            documentNumber,
            author,
            department,
            versionLabel,
            language,
            newTags,
            sourceTrust,
            project,
            workspaceKey,
            counterparty,
            businessStatus,
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
            tags,
            sourceTrust,
            project,
            updatedWorkspaceKey,
            counterparty,
            businessStatus,
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
            .toLowerCase(java.util.Locale.ROOT)
            .replaceAll("[^\\p{L}\\p{N}]+", "-")
            .replaceAll("(^-+|-+$)", "");
        return slug.isBlank() ? null : slug;
    }

    private static MaterialMetadataProvenance withDerivedOrigins(
        MaterialMetadataProvenance provenance,
        DocumentType rawDocumentType,
        KnowledgeDocumentClass rawKnowledgeDocumentClass,
        String rawWorkspaceKey,
        String resolvedWorkspaceKey
    ) {
        Map<String, MetadataValueOrigin> fieldOrigins = new LinkedHashMap<>(provenance.fieldOrigins());
        if (!fieldOrigins.containsKey(DOCUMENT_TYPE) && rawDocumentType == null) {
            fieldOrigins.put(DOCUMENT_TYPE, MetadataValueOrigin.DEFAULT);
        }
        if (!fieldOrigins.containsKey(KNOWLEDGE_DOCUMENT_CLASS)) {
            fieldOrigins.put(
                KNOWLEDGE_DOCUMENT_CLASS,
                rawKnowledgeDocumentClass == null ? MetadataValueOrigin.INFERRED : MetadataValueOrigin.MANUAL
            );
        }
        if (resolvedWorkspaceKey != null && !fieldOrigins.containsKey(WORKSPACE_KEY)) {
            fieldOrigins.put(
                WORKSPACE_KEY,
                normalizeText(rawWorkspaceKey) == null ? MetadataValueOrigin.INFERRED : MetadataValueOrigin.MANUAL
            );
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
