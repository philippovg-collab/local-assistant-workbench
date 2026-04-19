ALTER TABLE materials
    ADD COLUMN lineage_version INTEGER;

WITH ranked_versions AS (
    SELECT
        id,
        source_key,
        created_at,
        ROW_NUMBER() OVER (
            PARTITION BY source_key
            ORDER BY created_at ASC, id ASC
        ) AS lineage_version,
        COUNT(*) FILTER (WHERE version_state = 'ACTIVE') OVER (PARTITION BY source_key) AS active_count,
        MAX(created_at) FILTER (WHERE version_state = 'ACTIVE') OVER (PARTITION BY source_key) AS latest_active_created_at
    FROM materials
),
lineage_survivors AS (
    SELECT
        ranked_versions.source_key,
        COALESCE(
            (
                SELECT rv_active.id
                FROM ranked_versions rv_active
                WHERE rv_active.source_key = ranked_versions.source_key
                  AND rv_active.active_count = 1
                  AND rv_active.latest_active_created_at = rv_active.created_at
                  AND EXISTS (
                      SELECT 1
                      FROM materials active_material
                      WHERE active_material.id = rv_active.id
                        AND active_material.version_state = 'ACTIVE'
                  )
                ORDER BY rv_active.lineage_version DESC
                LIMIT 1
            ),
            (
                SELECT rv_latest.id
                FROM ranked_versions rv_latest
                WHERE rv_latest.source_key = ranked_versions.source_key
                ORDER BY rv_latest.lineage_version DESC
                LIMIT 1
            )
        ) AS survivor_id
    FROM ranked_versions
    GROUP BY ranked_versions.source_key
)
UPDATE materials AS materials_to_update
SET
    lineage_version = ranked_versions.lineage_version,
    version_state = CASE
        WHEN materials_to_update.id = lineage_survivors.survivor_id THEN 'ACTIVE'
        ELSE 'SUPERSEDED'
    END,
    superseded_by_material_id = CASE
        WHEN materials_to_update.id = lineage_survivors.survivor_id THEN NULL
        WHEN materials_to_update.version_state = 'ACTIVE'
            THEN lineage_survivors.survivor_id
        ELSE materials_to_update.superseded_by_material_id
    END,
    supersede_reason = CASE
        WHEN materials_to_update.id = lineage_survivors.survivor_id THEN materials_to_update.supersede_reason
        WHEN materials_to_update.version_state = 'ACTIVE'
             AND COALESCE(materials_to_update.supersede_reason, '') = ''
            THEN 'material.migration_resolved_multiple_active_versions'
        ELSE materials_to_update.supersede_reason
    END
FROM ranked_versions
JOIN lineage_survivors ON lineage_survivors.source_key = ranked_versions.source_key
WHERE ranked_versions.id = materials_to_update.id;

ALTER TABLE materials
    ALTER COLUMN lineage_version SET NOT NULL;

DROP INDEX IF EXISTS materials_content_hash_uidx;
DROP INDEX IF EXISTS materials_source_key_updated_at_idx;
DROP INDEX IF EXISTS materials_source_key_version_state_idx;

CREATE UNIQUE INDEX materials_source_key_content_hash_uidx
    ON materials (source_key, content_hash);

CREATE UNIQUE INDEX materials_source_key_lineage_version_uidx
    ON materials (source_key, lineage_version);

CREATE UNIQUE INDEX materials_one_active_per_source_key_uidx
    ON materials (source_key)
    WHERE version_state = 'ACTIVE';

CREATE INDEX materials_source_key_lineage_version_idx
    ON materials (source_key, lineage_version DESC);

CREATE INDEX materials_source_key_version_state_lineage_version_idx
    ON materials (source_key, version_state, lineage_version DESC);
