UPDATE materials
SET knowledge_document_class = COALESCE(
        UPPER(NULLIF(metadata_jsonb ->> 'knowledgeDocumentClass', '')),
        knowledge_document_class,
        'OTHER'
    ),
    workspace_key = COALESCE(
        NULLIF(metadata_jsonb ->> 'workspaceKey', ''),
        workspace_key
    )
WHERE metadata_jsonb IS NOT NULL
  AND (
      NULLIF(metadata_jsonb ->> 'knowledgeDocumentClass', '') IS NOT NULL
      OR NULLIF(metadata_jsonb ->> 'workspaceKey', '') IS NOT NULL
  );

CREATE INDEX IF NOT EXISTS materials_workspace_key_lower_idx ON materials (LOWER(workspace_key));
