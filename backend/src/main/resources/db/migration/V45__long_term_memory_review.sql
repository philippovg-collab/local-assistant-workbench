CREATE TABLE memory_entries (
    id UUID PRIMARY KEY,
    status TEXT NOT NULL,
    entry_type TEXT NOT NULL,
    content_text TEXT,
    normalized_key TEXT NOT NULL,
    workspace_key TEXT,
    project_key TEXT,
    pinned BOOLEAN NOT NULL DEFAULT false,
    confidence NUMERIC(5, 4),
    provenance_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb,
    source_conversation_id UUID,
    source_run_id UUID,
    source_turn_no INT,
    source_text_preview TEXT,
    source_text_hash TEXT,
    approved_at TIMESTAMPTZ,
    rejected_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT memory_entries_status_check CHECK (status IN ('PENDING_REVIEW', 'APPROVED', 'REJECTED', 'DELETED')),
    CONSTRAINT memory_entries_type_check CHECK (entry_type IN ('USER_PREFERENCE', 'USER_ALIAS', 'WORKSPACE_NOTE', 'PROJECT_NOTE', 'PINNED_USER_FACT')),
    CONSTRAINT memory_entries_confidence_check CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1)),
    CONSTRAINT memory_entries_source_turn_check CHECK (source_turn_no IS NULL OR source_turn_no > 0),
    CONSTRAINT memory_entries_deleted_content_check CHECK (
        status <> 'DELETED'
        OR (content_text IS NULL AND source_text_preview IS NULL AND pinned = false)
    )
);

CREATE UNIQUE INDEX memory_entries_active_unique_key_idx
    ON memory_entries (
        entry_type,
        normalized_key,
        COALESCE(workspace_key, ''),
        COALESCE(project_key, '')
    )
    WHERE status IN ('APPROVED', 'PENDING_REVIEW');

CREATE INDEX memory_entries_status_pinned_updated_idx
    ON memory_entries (status, pinned DESC, updated_at DESC);

CREATE INDEX memory_entries_type_scope_idx
    ON memory_entries (entry_type, workspace_key, project_key);

CREATE INDEX memory_entries_source_run_idx
    ON memory_entries (source_run_id)
    WHERE source_run_id IS NOT NULL;

CREATE TABLE memory_review_actions (
    id UUID PRIMARY KEY,
    entry_id UUID NOT NULL REFERENCES memory_entries (id) ON DELETE CASCADE,
    action TEXT NOT NULL,
    actor TEXT NOT NULL,
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT memory_review_actions_action_check CHECK (action IN ('CREATE', 'EDIT', 'APPROVE', 'REJECT', 'PIN', 'UNPIN', 'DELETE'))
);

CREATE INDEX memory_review_actions_entry_created_idx
    ON memory_review_actions (entry_id, created_at DESC);

CREATE TABLE memory_extraction_jobs (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL,
    run_id UUID NOT NULL UNIQUE,
    turn_no INT NOT NULL,
    status TEXT NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMPTZ,
    last_error_code TEXT,
    last_error_message TEXT,
    lease_owner TEXT,
    lease_expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    CONSTRAINT memory_extraction_jobs_status_check CHECK (status IN ('PENDING', 'RUNNING', 'DONE', 'FAILED')),
    CONSTRAINT memory_extraction_jobs_turn_positive CHECK (turn_no > 0),
    CONSTRAINT memory_extraction_jobs_attempt_nonnegative CHECK (attempt_count >= 0),
    CONSTRAINT memory_extraction_jobs_run_fk
        FOREIGN KEY (conversation_id, run_id)
        REFERENCES chat_conversation_runs (conversation_id, run_id)
        ON DELETE CASCADE
);

CREATE INDEX memory_extraction_jobs_status_retry_idx
    ON memory_extraction_jobs (status, next_retry_at ASC, created_at ASC);

CREATE INDEX memory_extraction_jobs_lease_idx
    ON memory_extraction_jobs (status, lease_expires_at ASC)
    WHERE status = 'RUNNING';

ALTER TABLE context_assembly_snapshots
    ADD COLUMN selected_memory_jsonb JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN dropped_memory_jsonb JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN memory_status TEXT,
    ADD COLUMN memory_degraded_reason TEXT;
