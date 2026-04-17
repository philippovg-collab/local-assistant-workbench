ALTER TABLE materials
    ADD COLUMN indexing_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN next_retry_at TIMESTAMPTZ,
    ADD COLUMN claimed_at TIMESTAMPTZ;

UPDATE materials
SET next_retry_at = updated_at
WHERE next_retry_at IS NULL;

ALTER TABLE material_chunks
    ALTER COLUMN embedding DROP NOT NULL;

CREATE INDEX materials_indexing_queue_idx
    ON materials (indexing_status, next_retry_at, created_at);
