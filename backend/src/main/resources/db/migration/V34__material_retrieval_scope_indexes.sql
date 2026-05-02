CREATE INDEX IF NOT EXISTS materials_retrieval_ready_created_idx
    ON materials (created_at, id)
    WHERE version_state = 'ACTIVE'
      AND indexing_status IN ('READY', 'PARTIAL_READY');

CREATE INDEX IF NOT EXISTS materials_retrieval_ready_scope_idx
    ON materials (
        workspace_key,
        knowledge_document_class,
        document_status,
        project_key,
        created_at,
        id
    )
    WHERE version_state = 'ACTIVE'
      AND indexing_status IN ('READY', 'PARTIAL_READY');

CREATE INDEX IF NOT EXISTS materials_retrieval_ready_type_status_idx
    ON materials (
        document_type,
        document_status,
        language_code,
        created_at,
        id
    )
    WHERE version_state = 'ACTIVE'
      AND indexing_status IN ('READY', 'PARTIAL_READY');

CREATE INDEX IF NOT EXISTS materials_retrieval_ready_document_number_idx
    ON materials (LOWER(document_number), created_at, id)
    WHERE version_state = 'ACTIVE'
      AND indexing_status IN ('READY', 'PARTIAL_READY')
      AND document_number IS NOT NULL;

CREATE INDEX IF NOT EXISTS materials_retrieval_ready_period_idx
    ON materials (period_start, period_end, created_at, id)
    WHERE version_state = 'ACTIVE'
      AND indexing_status IN ('READY', 'PARTIAL_READY');

CREATE INDEX IF NOT EXISTS material_tags_retrieval_value_material_idx
    ON material_tags (LOWER(tag_value), material_id);
