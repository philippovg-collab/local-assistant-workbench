ALTER TABLE context_assembly_snapshots
    ADD COLUMN retrieval_query_resolution_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb;
