ALTER TABLE materials
    ADD COLUMN version_state TEXT NOT NULL DEFAULT 'ACTIVE';

WITH ranked_materials AS (
    SELECT
        id,
        ROW_NUMBER() OVER (
            PARTITION BY source_key
            ORDER BY updated_at DESC, created_at DESC, id DESC
        ) AS version_rank
    FROM materials
)
UPDATE materials AS materials_to_update
SET version_state = CASE
    WHEN ranked_materials.version_rank = 1 THEN 'ACTIVE'
    ELSE 'SUPERSEDED'
END
FROM ranked_materials
WHERE ranked_materials.id = materials_to_update.id;

ALTER TABLE materials
    ADD CONSTRAINT materials_version_state_check
    CHECK (version_state IN ('ACTIVE', 'SUPERSEDED'));

CREATE INDEX materials_source_key_version_state_idx
    ON materials (source_key, version_state, updated_at DESC);
