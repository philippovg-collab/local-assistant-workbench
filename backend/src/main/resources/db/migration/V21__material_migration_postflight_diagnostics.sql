CREATE OR REPLACE VIEW material_migration_postflight_diagnostics AS
WITH metadata_conflicts AS (
    SELECT
        id::text AS material_id,
        'metadata_scope_conflict' AS check_name,
        'metadata_jsonb scope values still differ from material scope columns after backfill' AS message
    FROM materials
    WHERE metadata_jsonb IS NOT NULL
      AND (
          (
              NULLIF(metadata_jsonb ->> 'knowledgeDocumentClass', '') IS NOT NULL
              AND UPPER(metadata_jsonb ->> 'knowledgeDocumentClass') <> knowledge_document_class
          )
          OR (
              NULLIF(metadata_jsonb ->> 'workspaceKey', '') IS NOT NULL
              AND workspace_key IS DISTINCT FROM metadata_jsonb ->> 'workspaceKey'
          )
      )
),
missing_scope_defaults AS (
    SELECT
        id::text AS material_id,
        'missing_scope_default' AS check_name,
        'material still has no knowledge_document_class after scope backfill' AS message
    FROM materials
    WHERE knowledge_document_class IS NULL
)
SELECT * FROM metadata_conflicts
UNION ALL
SELECT * FROM missing_scope_defaults;
