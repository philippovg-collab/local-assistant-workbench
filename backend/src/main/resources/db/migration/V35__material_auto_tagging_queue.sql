CREATE TABLE material_auto_tagging_tasks (
    id UUID PRIMARY KEY,
    material_id UUID NOT NULL REFERENCES materials (id) ON DELETE CASCADE,
    content_hash TEXT NOT NULL,
    status TEXT NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMPTZ,
    claimed_at TIMESTAMPTZ,
    failure_code TEXT,
    failure_message TEXT,
    result_code TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    CONSTRAINT material_auto_tagging_tasks_status_check
        CHECK (status IN ('PENDING', 'RUNNING', 'FAILED', 'DONE')),
    CONSTRAINT material_auto_tagging_tasks_material_content_unique
        UNIQUE (material_id, content_hash)
);

CREATE INDEX material_auto_tagging_tasks_delivery_idx
    ON material_auto_tagging_tasks (status, COALESCE(next_retry_at, created_at), created_at);

CREATE INDEX material_auto_tagging_tasks_material_latest_idx
    ON material_auto_tagging_tasks (material_id, created_at DESC, updated_at DESC);

CREATE INDEX material_auto_tagging_tasks_claimed_idx
    ON material_auto_tagging_tasks (claimed_at);
