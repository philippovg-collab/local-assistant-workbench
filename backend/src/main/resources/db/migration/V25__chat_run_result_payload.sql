CREATE TABLE chat_run_results (
    run_id UUID PRIMARY KEY REFERENCES chat_run_headers (id) ON DELETE CASCADE,
    response_jsonb JSONB NOT NULL,
    response_schema_version TEXT NOT NULL DEFAULT 'chat_execution_response.v1',
    source TEXT NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chat_run_results_source_check
        CHECK (source IN ('LIVE_EXECUTION', 'TRACE_BACKFILL'))
);
