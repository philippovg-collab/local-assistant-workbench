CREATE TABLE IF NOT EXISTS material_lineage_operator_overrides (
    id UUID PRIMARY KEY,
    lineage_key TEXT NOT NULL,
    source_key TEXT NOT NULL,
    reason TEXT NOT NULL,
    created_by TEXT,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deactivated_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX IF NOT EXISTS material_lineage_operator_overrides_lineage_key_active_uq
    ON material_lineage_operator_overrides (lineage_key)
    WHERE active = true;

CREATE UNIQUE INDEX IF NOT EXISTS material_lineage_operator_overrides_source_key_active_uq
    ON material_lineage_operator_overrides (source_key)
    WHERE active = true;

CREATE INDEX IF NOT EXISTS material_lineage_operator_overrides_active_idx
    ON material_lineage_operator_overrides (active, created_at DESC);
