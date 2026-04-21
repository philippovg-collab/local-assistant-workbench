ALTER TABLE materials
    ADD COLUMN IF NOT EXISTS workspace_key TEXT,
    ADD COLUMN IF NOT EXISTS project_key TEXT,
    ADD COLUMN IF NOT EXISTS document_status TEXT;

INSERT INTO reference_workspaces (
    key,
    name_ru,
    active,
    sort_order,
    is_default,
    created_at,
    updated_at
) VALUES (
    'general',
    'Общая',
    true,
    0,
    true,
    NOW(),
    NOW()
) ON CONFLICT (key) DO NOTHING;

UPDATE materials
SET language_code = NULL
WHERE language_code IS NULL
   OR BTRIM(language_code) = '';

UPDATE materials
SET language_code = UPPER(BTRIM(language_code))
WHERE UPPER(BTRIM(language_code)) IN ('RU', 'KK', 'EN');

UPDATE materials
SET language_code = NULL
WHERE language_code IS NOT NULL
  AND language_code NOT IN ('RU', 'KK', 'EN');

UPDATE materials
SET workspace_key = NULL
WHERE workspace_key IS NOT NULL
  AND BTRIM(workspace_key) = '';

UPDATE materials m
SET workspace_key = 'general'
WHERE m.workspace_key IS NULL
   OR NOT EXISTS (
       SELECT 1
       FROM reference_workspaces rw
       WHERE rw.key = m.workspace_key
   );

UPDATE materials
SET project_key = NULL
WHERE project_key IS NOT NULL
  AND BTRIM(project_key) = '';

UPDATE materials m
SET project_key = rp.key
FROM reference_projects rp
WHERE m.project_key IS NULL
  AND m.project_name = rp.key;

UPDATE materials m
SET project_key = NULL
WHERE m.project_key IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM reference_projects rp
      WHERE rp.key = m.project_key
  );

UPDATE materials m
SET project_key = NULL
WHERE m.project_key IS NOT NULL
  AND EXISTS (
      SELECT 1
      FROM reference_projects rp
      WHERE rp.key = m.project_key
        AND rp.workspace_key <> m.workspace_key
  );

UPDATE materials
SET document_status = CASE
    WHEN UPPER(BTRIM(COALESCE(document_status, ''))) IN ('ACTIVE', 'DRAFT', 'ARCHIVED', 'REVOKED')
        THEN UPPER(BTRIM(document_status))
    WHEN UPPER(BTRIM(COALESCE(business_status, ''))) IN ('ACTIVE', 'DRAFT', 'ARCHIVED', 'REVOKED')
        THEN UPPER(BTRIM(business_status))
    ELSE 'ACTIVE'
END;

ALTER TABLE materials
    DROP CONSTRAINT IF EXISTS materials_language_code_check,
    DROP CONSTRAINT IF EXISTS materials_document_status_check,
    DROP CONSTRAINT IF EXISTS materials_workspace_key_fk,
    DROP CONSTRAINT IF EXISTS materials_project_key_fk;

ALTER TABLE materials
    ALTER COLUMN workspace_key SET DEFAULT 'general',
    ALTER COLUMN workspace_key SET NOT NULL,
    ALTER COLUMN document_status SET DEFAULT 'ACTIVE',
    ALTER COLUMN document_status SET NOT NULL;

ALTER TABLE materials
    ADD CONSTRAINT materials_language_code_check
        CHECK (language_code IN ('RU', 'KK', 'EN') OR language_code IS NULL),
    ADD CONSTRAINT materials_document_status_check
        CHECK (document_status IN ('ACTIVE', 'DRAFT', 'ARCHIVED', 'REVOKED')),
    ADD CONSTRAINT materials_workspace_key_fk
        FOREIGN KEY (workspace_key) REFERENCES reference_workspaces (key),
    ADD CONSTRAINT materials_project_key_fk
        FOREIGN KEY (project_key) REFERENCES reference_projects (key);

CREATE INDEX IF NOT EXISTS materials_workspace_key_idx ON materials (workspace_key);
CREATE INDEX IF NOT EXISTS materials_project_key_idx ON materials (project_key);
CREATE INDEX IF NOT EXISTS materials_document_status_idx ON materials (document_status);
