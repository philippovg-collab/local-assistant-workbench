ALTER TABLE materials
    ADD COLUMN knowledge_document_class TEXT NOT NULL DEFAULT 'OTHER',
    ADD COLUMN workspace_key TEXT;

ALTER TABLE materials
    ADD CONSTRAINT materials_knowledge_document_class_check
        CHECK (knowledge_document_class IN ('CONTRACTS', 'REGULATIONS', 'CORRESPONDENCE', 'TECHDOCS', 'OTHER'));

CREATE INDEX materials_knowledge_document_class_idx ON materials (knowledge_document_class);
CREATE INDEX materials_workspace_key_idx ON materials (workspace_key);

CREATE TABLE knowledge_presets (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT,
    scope_jsonb JSONB NOT NULL,
    revision INTEGER NOT NULL DEFAULT 1,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE knowledge_preset_revisions (
    preset_id UUID NOT NULL REFERENCES knowledge_presets (id) ON DELETE CASCADE,
    revision INTEGER NOT NULL,
    name TEXT NOT NULL,
    description TEXT,
    scope_jsonb JSONB NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    restored_from_revision INTEGER,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (preset_id, revision)
);

CREATE INDEX knowledge_presets_active_idx ON knowledge_presets (is_active, updated_at DESC);

INSERT INTO knowledge_presets (id, name, description, scope_jsonb, revision, is_active, created_at, updated_at) VALUES
    ('00000000-0000-0000-0000-000000000101', 'Договоры', 'Поиск только по договорным материалам.', '{"presetIds":[],"documentClasses":["contracts"],"tags":[],"workspaceKey":null,"uploadedTodayOnly":false}'::jsonb, 1, true, NOW(), NOW()),
    ('00000000-0000-0000-0000-000000000102', 'Регламенты', 'Поиск по регламентам и внутренним правилам.', '{"presetIds":[],"documentClasses":["regulations"],"tags":[],"workspaceKey":null,"uploadedTodayOnly":false}'::jsonb, 1, true, NOW(), NOW()),
    ('00000000-0000-0000-0000-000000000103', 'Переписка', 'Поиск по письмам и рабочей переписке.', '{"presetIds":[],"documentClasses":["correspondence"],"tags":[],"workspaceKey":null,"uploadedTodayOnly":false}'::jsonb, 1, true, NOW(), NOW()),
    ('00000000-0000-0000-0000-000000000104', 'Техдоки', 'Поиск по технической документации и инструкциям.', '{"presetIds":[],"documentClasses":["techdocs"],"tags":[],"workspaceKey":null,"uploadedTodayOnly":false}'::jsonb, 1, true, NOW(), NOW()),
    ('00000000-0000-0000-0000-000000000105', 'Загруженные сегодня', 'Ограничение поиска только файлами, загруженными сегодня.', '{"presetIds":[],"documentClasses":[],"tags":[],"workspaceKey":null,"uploadedTodayOnly":true}'::jsonb, 1, true, NOW(), NOW());

INSERT INTO knowledge_preset_revisions (preset_id, revision, name, description, scope_jsonb, is_active, restored_from_revision, created_at, updated_at)
SELECT
    id,
    revision,
    name,
    description,
    scope_jsonb,
    is_active,
    NULL,
    created_at,
    updated_at
FROM knowledge_presets;

CREATE TABLE chat_audit_runs (
    id UUID PRIMARY KEY,
    mode TEXT NOT NULL,
    model TEXT NOT NULL,
    prompt TEXT NOT NULL,
    answer TEXT NOT NULL,
    context_status TEXT,
    answer_mode TEXT,
    audit_jsonb JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX chat_audit_runs_created_at_idx ON chat_audit_runs (created_at DESC);
