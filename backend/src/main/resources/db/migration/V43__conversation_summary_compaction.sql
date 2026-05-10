ALTER TABLE conversation_working_memory
    ADD COLUMN summary_text TEXT,
    ADD COLUMN summary_facts_jsonb JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN summary_active_entities_jsonb JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN summary_source_refs_jsonb JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN summary_through_turn_no BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN summary_updated_from_run_id UUID REFERENCES chat_run_headers (id) ON DELETE SET NULL,
    ADD COLUMN summary_updated_at TIMESTAMPTZ,
    ADD COLUMN summary_status TEXT NOT NULL DEFAULT 'EMPTY',
    ADD COLUMN summary_version INTEGER NOT NULL DEFAULT 1,
    ADD CONSTRAINT conversation_working_memory_summary_turn_nonnegative
        CHECK (summary_through_turn_no >= 0),
    ADD CONSTRAINT conversation_working_memory_summary_status_check
        CHECK (summary_status IN ('EMPTY', 'READY', 'STALE', 'FAILED'));

CREATE INDEX conversation_working_memory_summary_updated_run_idx
    ON conversation_working_memory (summary_updated_from_run_id)
    WHERE summary_updated_from_run_id IS NOT NULL;

CREATE TABLE conversation_summary_refresh_jobs (
    conversation_id UUID PRIMARY KEY REFERENCES chat_conversations (id) ON DELETE CASCADE,
    requested_through_turn_no BIGINT NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMPTZ,
    lease_owner TEXT,
    lease_expires_at TIMESTAMPTZ,
    last_error_code TEXT,
    last_error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT conversation_summary_refresh_turn_positive
        CHECK (requested_through_turn_no > 0),
    CONSTRAINT conversation_summary_refresh_attempt_nonnegative
        CHECK (attempt_count >= 0),
    CONSTRAINT conversation_summary_refresh_status_check
        CHECK (status IN ('PENDING', 'RUNNING', 'FAILED'))
);

CREATE INDEX conversation_summary_refresh_ready_idx
    ON conversation_summary_refresh_jobs (status, next_retry_at, updated_at);

ALTER TABLE context_assembly_snapshots
    ADD COLUMN summary_used BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN summary_through_turn_no BIGINT,
    ADD COLUMN summary_status TEXT,
    ADD COLUMN summary_token_estimate INTEGER,
    ADD COLUMN summary_degraded_reason TEXT;
