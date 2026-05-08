package com.example.demo.service;

final class MaterialMetadataDefaults {

    static final String DOCUMENT_TYPE = "documentType";
    static final String DOCUMENT_DATE = "documentDate";
    static final String DOCUMENT_NUMBER = "documentNumber";
    static final String AUTHOR = "author";
    static final String DEPARTMENT = "department";
    static final String VERSION_LABEL = "versionLabel";
    static final String LANGUAGE = "language";
    static final String LANGUAGE_CODE = "languageCode";
    static final String TAGS = "tags";
    static final String MANUAL_TAGS = "manualTags";
    static final String AUTO_TAGS = "autoTags";
    static final String EFFECTIVE_TAGS = "effectiveTags";
    static final String SOURCE_TRUST = "sourceTrust";
    static final String PROJECT = "project";
    static final String PROJECT_KEY = "projectKey";
    static final String KNOWLEDGE_DOCUMENT_CLASS = "knowledgeDocumentClass";
    static final String WORKSPACE_KEY = "workspaceKey";
    static final String COUNTERPARTY = "counterparty";
    static final String BUSINESS_STATUS = "businessStatus";
    static final String DOCUMENT_STATUS = "documentStatus";
    static final String PERIOD_START = "periodStart";
    static final String PERIOD_END = "periodEnd";
    static final String DEFAULT_WORKSPACE_KEY = "general";

    static final double LABELLED_HEADER_CONFIDENCE = 0.9d;
    static final double REGEX_HINT_CONFIDENCE = 0.75d;
    static final double LANGUAGE_HEURISTIC_CONFIDENCE = 0.6d;
    static final double TAG_HINT_CONFIDENCE = 0.45d;

    private MaterialMetadataDefaults() {
    }
}
