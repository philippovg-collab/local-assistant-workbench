package com.example.demo.infrastructure.material;

final class PostgresMaterialSql {

    static final String DEFAULT_WORKSPACE_KEY = "general";

    static final String MATERIAL_COLUMNS = """
        id,
        title,
        source_type,
        original_file_name,
        media_type,
        content,
        normalized_content,
        content_hash,
        source_key,
        extractor,
        ocr_used,
        page_count,
        document_type,
        document_date,
        document_number,
        author_name,
        department,
        version_label,
        language_code,
        source_trust,
        project_name,
        project_key,
        counterparty,
        business_status,
        document_status,
        period_start,
        period_end,
        knowledge_document_class,
        workspace_key,
        metadata_jsonb,
        lineage_version,
        indexing_status,
        version_state,
        status_reason_code,
        status_reason_message,
        indexing_attempts,
        next_retry_at,
        superseded_by_material_id,
        supersede_reason,
        created_at,
        updated_at
        """;

    static final String MATERIAL_SUMMARY_COLUMNS = """
        id,
        title,
        source_type,
        original_file_name,
        document_type,
        document_date,
        document_number,
        author_name,
        department,
        version_label,
        language_code,
        source_trust,
        project_name,
        project_key,
        counterparty,
        business_status,
        document_status,
        period_start,
        period_end,
        knowledge_document_class,
        workspace_key,
        metadata_jsonb,
        indexing_status,
        version_state,
        status_reason_code,
        status_reason_message,
        indexing_attempts,
        next_retry_at,
        created_at,
        updated_at,
        CHAR_LENGTH(COALESCE(content, '')) AS content_length,
        CASE
            WHEN CHAR_LENGTH(COALESCE(content, '')) > 180
                THEN LEFT(COALESCE(content, ''), 180) || '...'
            ELSE COALESCE(content, '')
        END AS preview
        """;

    private PostgresMaterialSql() {
    }
}
