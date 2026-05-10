CREATE INDEX IF NOT EXISTS materials_retrieval_ready_department_lower_idx
    ON materials (LOWER(department), created_at, id)
    WHERE version_state = 'ACTIVE'
      AND indexing_status IN ('READY', 'PARTIAL_READY')
      AND department IS NOT NULL;

CREATE INDEX IF NOT EXISTS materials_retrieval_ready_project_name_lower_idx
    ON materials (LOWER(project_name), created_at, id)
    WHERE version_state = 'ACTIVE'
      AND indexing_status IN ('READY', 'PARTIAL_READY')
      AND project_name IS NOT NULL;

CREATE INDEX IF NOT EXISTS materials_retrieval_ready_counterparty_lower_idx
    ON materials (LOWER(counterparty), created_at, id)
    WHERE version_state = 'ACTIVE'
      AND indexing_status IN ('READY', 'PARTIAL_READY')
      AND counterparty IS NOT NULL;

CREATE INDEX IF NOT EXISTS materials_retrieval_ready_business_status_lower_idx
    ON materials (LOWER(business_status), created_at, id)
    WHERE version_state = 'ACTIVE'
      AND indexing_status IN ('READY', 'PARTIAL_READY')
      AND business_status IS NOT NULL;

CREATE INDEX IF NOT EXISTS materials_retrieval_ready_source_trust_idx
    ON materials (source_trust, created_at, id)
    WHERE version_state = 'ACTIVE'
      AND indexing_status IN ('READY', 'PARTIAL_READY');
