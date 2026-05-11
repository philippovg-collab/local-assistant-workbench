ALTER TABLE eval_runs
    ADD COLUMN IF NOT EXISTS run_kind TEXT NOT NULL DEFAULT 'E2E';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'eval_runs_run_kind_check'
    ) THEN
        ALTER TABLE eval_runs
            ADD CONSTRAINT eval_runs_run_kind_check
            CHECK (run_kind IN ('E2E', 'RETRIEVAL_ONLY'));
    END IF;

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
                'OUTPUT_FORMAT_ERROR',
                'SCORER_ERROR',
                'JUDGE_ERROR',
                'TIMEOUT',
                'UNKNOWN'
            )
        );
END $$;

CREATE INDEX IF NOT EXISTS eval_runs_run_kind_created_idx ON eval_runs (run_kind, created_at DESC);
