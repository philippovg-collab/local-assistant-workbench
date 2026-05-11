CREATE TABLE eval_datasets (
    id UUID PRIMARY KEY,
    dataset_key TEXT NOT NULL,
    kind TEXT NOT NULL,
    version TEXT NOT NULL,
    status TEXT NOT NULL,
    name TEXT NOT NULL,
    description TEXT,
    tags_jsonb JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT eval_datasets_kind_check CHECK (kind IN ('SMOKE', 'GOLDEN', 'CANDIDATE')),
    CONSTRAINT eval_datasets_status_check CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    CONSTRAINT eval_datasets_key_version_unique UNIQUE (dataset_key, version)
);

CREATE INDEX eval_datasets_status_created_idx ON eval_datasets (status, created_at DESC);
CREATE INDEX eval_datasets_kind_created_idx ON eval_datasets (kind, created_at DESC);

CREATE TABLE eval_cases (
    id UUID PRIMARY KEY,
    dataset_id UUID NOT NULL REFERENCES eval_datasets (id) ON DELETE CASCADE,
    case_key TEXT NOT NULL,
    revision INT NOT NULL,
    case_type TEXT NOT NULL,
    expected_mode TEXT NOT NULL,
    severity TEXT NOT NULL,
    question TEXT NOT NULL,
    knowledge_scope_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb,
    retrieval_filters_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb,
    gold_facts_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb,
    gold_answers_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb,
    gold_evidence_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb,
    origin_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT eval_cases_revision_positive_check CHECK (revision > 0),
    CONSTRAINT eval_cases_case_type_check CHECK (case_type IN (
        'EXACT_FACT',
        'MULTI_DOCUMENT_COMPARISON',
        'DATE_VERSION_FILTER',
        'TABLE_QUESTION',
        'AMBIGUOUS_QUERY',
        'NO_ANSWER',
        'CONFLICTING_SOURCES'
    )),
    CONSTRAINT eval_cases_expected_mode_check CHECK (expected_mode IN ('ANSWER', 'ABSTAIN', 'CLARIFY')),
    CONSTRAINT eval_cases_severity_check CHECK (severity IN ('BLOCKER', 'CRITICAL', 'MAJOR', 'MINOR')),
    CONSTRAINT eval_cases_dataset_key_revision_unique UNIQUE (dataset_id, case_key, revision)
);

CREATE INDEX eval_cases_dataset_idx ON eval_cases (dataset_id, case_key ASC, revision ASC);
CREATE INDEX eval_cases_type_idx ON eval_cases (case_type);
CREATE INDEX eval_cases_severity_idx ON eval_cases (severity);

CREATE TABLE eval_case_reviews (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES eval_cases (id) ON DELETE CASCADE,
    status TEXT NOT NULL,
    reviewer TEXT,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT eval_case_reviews_status_check CHECK (status IN (
        'CANDIDATE',
        'APPROVED',
        'REJECTED',
        'NEEDS_REVISION'
    ))
);

CREATE INDEX eval_case_reviews_case_idx ON eval_case_reviews (case_id, created_at DESC);
CREATE INDEX eval_case_reviews_status_idx ON eval_case_reviews (status, created_at DESC);

CREATE TABLE corpus_snapshots (
    id UUID PRIMARY KEY,
    snapshot_key TEXT NOT NULL UNIQUE,
    status TEXT NOT NULL,
    reference_instant TIMESTAMPTZ NOT NULL,
    material_set_hash TEXT NOT NULL,
    search_state_hash TEXT NOT NULL,
    config_hash TEXT NOT NULL,
    metadata_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT corpus_snapshots_status_check CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED'))
);

CREATE INDEX corpus_snapshots_status_created_idx ON corpus_snapshots (status, created_at DESC);
CREATE INDEX corpus_snapshots_reference_idx ON corpus_snapshots (reference_instant DESC);
CREATE INDEX corpus_snapshots_config_hash_idx ON corpus_snapshots (config_hash);

CREATE TABLE corpus_snapshot_items (
    id UUID PRIMARY KEY,
    snapshot_id UUID NOT NULL REFERENCES corpus_snapshots (id) ON DELETE CASCADE,
    material_id UUID NOT NULL,
    material_version_id UUID,
    title TEXT,
    source_key TEXT,
    content_hash TEXT NOT NULL,
    metadata_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX corpus_snapshot_items_snapshot_idx ON corpus_snapshot_items (snapshot_id, material_id);
CREATE INDEX corpus_snapshot_items_material_idx ON corpus_snapshot_items (material_id);
CREATE INDEX corpus_snapshot_items_content_hash_idx ON corpus_snapshot_items (content_hash);

CREATE TABLE eval_runs (
    id UUID PRIMARY KEY,
    dataset_id UUID REFERENCES eval_datasets (id) ON DELETE SET NULL,
    snapshot_id UUID REFERENCES corpus_snapshots (id) ON DELETE SET NULL,
    status TEXT NOT NULL,
    execution_config_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb,
    config_hash TEXT NOT NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    summary_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT eval_runs_status_check CHECK (status IN ('QUEUED', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    CONSTRAINT eval_runs_timing_check CHECK (completed_at IS NULL OR started_at IS NULL OR completed_at >= started_at)
);

CREATE INDEX eval_runs_status_created_idx ON eval_runs (status, created_at DESC);
CREATE INDEX eval_runs_dataset_idx ON eval_runs (dataset_id, created_at DESC) WHERE dataset_id IS NOT NULL;
CREATE INDEX eval_runs_snapshot_idx ON eval_runs (snapshot_id, created_at DESC) WHERE snapshot_id IS NOT NULL;
CREATE INDEX eval_runs_config_hash_idx ON eval_runs (config_hash);

CREATE TABLE eval_run_items (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES eval_runs (id) ON DELETE CASCADE,
    case_id UUID REFERENCES eval_cases (id) ON DELETE SET NULL,
    chat_run_id UUID REFERENCES chat_run_headers (id) ON DELETE SET NULL,
    status TEXT NOT NULL,
    failure_code TEXT,
    artifact_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb,
    scorer_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT eval_run_items_status_check CHECK (status IN ('PENDING', 'RUNNING', 'PASSED', 'FAILED', 'ERROR', 'SKIPPED')),
    CONSTRAINT eval_run_items_failure_code_check CHECK (
        failure_code IS NULL OR failure_code IN (
            'RETRIEVAL_EMPTY',
            'RETRIEVAL_INSUFFICIENT',
            'CHAT_RUN_FAILED',
            'OUTPUT_FORMAT_ERROR',
            'SCORER_ERROR',
            'JUDGE_ERROR',
            'TIMEOUT',
            'UNKNOWN'
        )
    )
);

CREATE INDEX eval_run_items_run_idx ON eval_run_items (run_id, created_at ASC);
CREATE INDEX eval_run_items_status_idx ON eval_run_items (status, created_at DESC);
CREATE INDEX eval_run_items_case_idx ON eval_run_items (case_id) WHERE case_id IS NOT NULL;
CREATE INDEX eval_run_items_chat_run_idx ON eval_run_items (chat_run_id) WHERE chat_run_id IS NOT NULL;

CREATE TABLE eval_compares (
    id UUID PRIMARY KEY,
    baseline_run_id UUID NOT NULL REFERENCES eval_runs (id) ON DELETE CASCADE,
    candidate_run_id UUID NOT NULL REFERENCES eval_runs (id) ON DELETE CASCADE,
    status TEXT NOT NULL,
    compatibility_reason TEXT,
    summary_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT eval_compares_status_check CHECK (status IN ('UNKNOWN', 'COMPATIBLE', 'INCOMPATIBLE')),
    CONSTRAINT eval_compares_distinct_runs_check CHECK (baseline_run_id <> candidate_run_id),
    CONSTRAINT eval_compares_run_pair_unique UNIQUE (baseline_run_id, candidate_run_id)
);

CREATE INDEX eval_compares_status_created_idx ON eval_compares (status, created_at DESC);
CREATE INDEX eval_compares_candidate_idx ON eval_compares (candidate_run_id, created_at DESC);
