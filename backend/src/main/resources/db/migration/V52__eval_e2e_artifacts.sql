ALTER TABLE eval_run_items
    ADD COLUMN IF NOT EXISTS context_assembly_id UUID,
    ADD COLUMN IF NOT EXISTS failure_message TEXT,
    ADD COLUMN IF NOT EXISTS score_summary_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'eval_run_items_failure_code_check'
    ) THEN
        ALTER TABLE eval_run_items
            DROP CONSTRAINT eval_run_items_failure_code_check;
    END IF;

    ALTER TABLE eval_run_items
        ADD CONSTRAINT eval_run_items_failure_code_check CHECK (
            failure_code IS NULL OR failure_code IN (
                'RETRIEVAL_EMPTY',
                'RETRIEVAL_INSUFFICIENT',
                'SNAPSHOT_STALE',
                'CONFIG_MISMATCH',
                'CASE_NOT_SCORABLE',
                'RETRIEVAL_NO_RESULTS',
                'FILTER_VIOLATION',
                'RETRIEVAL_EXECUTION_ERROR',
                'CHAT_RUN_FAILED',
                'CHAT_RUN_CANCELLED',
                'CHAT_RUN_TIMEOUT',
                'OUTPUT_FORMAT_ERROR',
                'CITATION_RESOLUTION_ERROR',
                'SCORER_ERROR',
                'JUDGE_ERROR',
                'TIMEOUT',
                'UNKNOWN'
            )
        );
END $$;

CREATE INDEX IF NOT EXISTS eval_run_items_context_assembly_idx
    ON eval_run_items (context_assembly_id)
    WHERE context_assembly_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS eval_run_item_artifacts (
    run_item_id UUID NOT NULL REFERENCES eval_run_items (id) ON DELETE CASCADE,
    artifact_type TEXT NOT NULL,
    payload_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (run_item_id, artifact_type),
    CONSTRAINT eval_run_item_artifacts_type_check CHECK (artifact_type IN (
        'CHAT_RUN_RESULT',
        'CHAT_RUN_TRACE',
        'CONTEXT_ASSEMBLY',
        'PROMPT_SNAPSHOT',
        'RETRIEVAL_SUMMARY',
        'CASE_SNAPSHOT',
        'RAW_OUTPUT',
        'STRUCTURED_OUTPUT',
        'ANSWER_CLAIMS',
        'JUDGE_OUTPUT',
        'SCORER_OUTPUT'
    ))
);

CREATE INDEX IF NOT EXISTS eval_run_item_artifacts_type_idx
    ON eval_run_item_artifacts (artifact_type, updated_at DESC);
