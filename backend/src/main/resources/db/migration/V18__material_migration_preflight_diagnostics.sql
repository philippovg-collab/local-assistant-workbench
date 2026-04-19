CREATE OR REPLACE VIEW material_migration_preflight_diagnostics AS
WITH metadata_conflicts AS (
    SELECT
        id::text AS material_id,
        'metadata_scope_conflict' AS check_name,
        'metadata_jsonb scope values differ from material scope columns before backfill' AS message
    FROM materials
    WHERE metadata_jsonb IS NOT NULL
      AND (
          (
              NULLIF(metadata_jsonb ->> 'knowledgeDocumentClass', '') IS NOT NULL
              AND knowledge_document_class IS NOT NULL
              AND UPPER(metadata_jsonb ->> 'knowledgeDocumentClass') <> knowledge_document_class
          )
          OR (
              NULLIF(metadata_jsonb ->> 'workspaceKey', '') IS NOT NULL
              AND workspace_key IS NOT NULL
              AND workspace_key IS DISTINCT FROM metadata_jsonb ->> 'workspaceKey'
          )
      )
),
lineage_identity_collisions AS (
    SELECT
        NULL::text AS material_id,
        'lineage_identity_collision' AS check_name,
        'multiple source keys share one canonical lineage identity before scope backfill' AS message
    FROM material_lineage_identities
    GROUP BY source_type, identity_kind, identity_key
    HAVING COUNT(DISTINCT source_key) > 1
),
missing_file_lineage_identity AS (
    SELECT
        source_key AS material_id,
        'missing_file_lineage_identity' AS check_name,
        'file lineage identity has neither explicit title nor filename-derived stem' AS message
    FROM material_lineage_identities
    WHERE source_type = 'file'
      AND explicit_title_norm IS NULL
      AND file_stem_norm IS NULL
)
SELECT * FROM metadata_conflicts
UNION ALL
SELECT * FROM lineage_identity_collisions
UNION ALL
SELECT * FROM missing_file_lineage_identity;

DO $$
DECLARE
    conflict_count integer;
BEGIN
    SELECT COUNT(*)
    INTO conflict_count
    FROM material_migration_preflight_diagnostics
    WHERE check_name = 'metadata_scope_conflict';

    IF conflict_count > 0 THEN
        RAISE EXCEPTION
            'Material migration preflight failed: % metadata scope conflict(s) detected before scope-column backfill. Inspect material_migration_preflight_diagnostics before rerunning.',
            conflict_count;
    END IF;
END $$;
