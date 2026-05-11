ALTER TABLE corpus_snapshots
    ADD COLUMN IF NOT EXISTS manifest_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS total_item_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS active_item_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS superseded_item_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS ready_item_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE corpus_snapshot_items
    ADD COLUMN IF NOT EXISTS version_state TEXT,
    ADD COLUMN IF NOT EXISTS lineage_version INTEGER,
    ADD COLUMN IF NOT EXISTS version_label TEXT,
    ADD COLUMN IF NOT EXISTS indexing_status TEXT,
    ADD COLUMN IF NOT EXISTS document_number TEXT,
    ADD COLUMN IF NOT EXISTS document_date DATE,
    ADD COLUMN IF NOT EXISTS document_type TEXT,
    ADD COLUMN IF NOT EXISTS document_status TEXT,
    ADD COLUMN IF NOT EXISTS workspace_key TEXT,
    ADD COLUMN IF NOT EXISTS project_key TEXT,
    ADD COLUMN IF NOT EXISTS language_code TEXT,
    ADD COLUMN IF NOT EXISTS metadata_hash TEXT,
    ADD COLUMN IF NOT EXISTS chunk_profile TEXT,
    ADD COLUMN IF NOT EXISTS chunk_count INTEGER,
    ADD COLUMN IF NOT EXISTS chunk_set_hash TEXT,
    ADD COLUMN IF NOT EXISTS material_created_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS material_updated_at TIMESTAMPTZ;

ALTER TABLE eval_runs
    ADD COLUMN IF NOT EXISTS dataset_version TEXT,
    ADD COLUMN IF NOT EXISTS corpus_snapshot_id UUID REFERENCES corpus_snapshots (id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS execution_config_hash TEXT;

UPDATE eval_runs
SET corpus_snapshot_id = COALESCE(corpus_snapshot_id, snapshot_id),
    execution_config_hash = COALESCE(execution_config_hash, config_hash)
WHERE corpus_snapshot_id IS NULL
   OR execution_config_hash IS NULL;

ALTER TABLE eval_compares
    ADD COLUMN IF NOT EXISTS compatibility_status TEXT NOT NULL DEFAULT 'BLOCKED',
    ADD COLUMN IF NOT EXISTS compatibility_reasons_jsonb JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS baseline_dataset_version TEXT,
    ADD COLUMN IF NOT EXISTS candidate_dataset_version TEXT,
    ADD COLUMN IF NOT EXISTS baseline_snapshot_id UUID REFERENCES corpus_snapshots (id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS candidate_snapshot_id UUID REFERENCES corpus_snapshots (id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS baseline_material_set_hash TEXT,
    ADD COLUMN IF NOT EXISTS candidate_material_set_hash TEXT,
    ADD COLUMN IF NOT EXISTS baseline_search_state_hash TEXT,
    ADD COLUMN IF NOT EXISTS candidate_search_state_hash TEXT,
    ADD COLUMN IF NOT EXISTS baseline_config_hash TEXT,
    ADD COLUMN IF NOT EXISTS candidate_config_hash TEXT;

UPDATE eval_compares
SET compatibility_status = CASE
    WHEN status = 'COMPATIBLE' THEN 'COMPATIBLE'
    WHEN status = 'UNKNOWN' THEN 'BLOCKED'
    ELSE 'BLOCKED'
END;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'corpus_snapshot_items_version_state_check'
    ) THEN
        ALTER TABLE corpus_snapshot_items
            ADD CONSTRAINT corpus_snapshot_items_version_state_check
            CHECK (version_state IS NULL OR version_state IN ('ACTIVE', 'SUPERSEDED'));
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'corpus_snapshot_items_indexing_status_check'
    ) THEN
        ALTER TABLE corpus_snapshot_items
            ADD CONSTRAINT corpus_snapshot_items_indexing_status_check
            CHECK (indexing_status IS NULL OR indexing_status IN ('READY', 'PARTIAL_READY', 'PENDING', 'IN_PROGRESS', 'FAILED'));
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'eval_compares_compatibility_status_check'
    ) THEN
        ALTER TABLE eval_compares
            ADD CONSTRAINT eval_compares_compatibility_status_check
            CHECK (compatibility_status IN ('COMPATIBLE', 'BLOCKED', 'WARNING_ONLY'));
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS corpus_snapshots_material_set_hash_idx ON corpus_snapshots (material_set_hash);
CREATE INDEX IF NOT EXISTS corpus_snapshots_search_state_hash_idx ON corpus_snapshots (search_state_hash);
CREATE INDEX IF NOT EXISTS corpus_snapshot_items_source_key_idx ON corpus_snapshot_items (source_key);
CREATE INDEX IF NOT EXISTS corpus_snapshot_items_metadata_hash_idx ON corpus_snapshot_items (metadata_hash);
CREATE INDEX IF NOT EXISTS corpus_snapshot_items_chunk_set_hash_idx ON corpus_snapshot_items (chunk_set_hash);
CREATE UNIQUE INDEX IF NOT EXISTS corpus_snapshot_items_material_version_uidx
    ON corpus_snapshot_items (snapshot_id, material_id, COALESCE(material_version_id, material_id));

CREATE INDEX IF NOT EXISTS eval_runs_corpus_snapshot_idx
    ON eval_runs (corpus_snapshot_id, created_at DESC)
    WHERE corpus_snapshot_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS eval_runs_execution_config_hash_idx ON eval_runs (execution_config_hash);

CREATE INDEX IF NOT EXISTS eval_compares_baseline_candidate_idx ON eval_compares (baseline_run_id, candidate_run_id);
CREATE INDEX IF NOT EXISTS eval_compares_compatibility_status_idx ON eval_compares (compatibility_status, created_at DESC);
CREATE INDEX IF NOT EXISTS eval_compares_hashes_idx
    ON eval_compares (baseline_material_set_hash, candidate_material_set_hash, baseline_search_state_hash, candidate_search_state_hash);
