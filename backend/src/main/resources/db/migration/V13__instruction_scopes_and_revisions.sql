ALTER TABLE instructions
    ADD COLUMN scope_level TEXT NOT NULL DEFAULT 'chat_scenario',
    ADD COLUMN scope_target_id TEXT,
    ADD COLUMN revision INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT true;

ALTER TABLE instructions
    ADD CONSTRAINT instructions_scope_level_check
        CHECK (scope_level IN ('assistant_system', 'workspace_project', 'chat_scenario', 'request_temporary')),
    ADD CONSTRAINT instructions_revision_positive_check
        CHECK (revision > 0);

CREATE INDEX instructions_scope_idx ON instructions (scope_level, scope_target_id, is_active);

CREATE TABLE instruction_revisions (
    instruction_id UUID NOT NULL REFERENCES instructions (id) ON DELETE CASCADE,
    revision INTEGER NOT NULL,
    title TEXT NOT NULL,
    category TEXT NOT NULL,
    content TEXT NOT NULL,
    normalized_content TEXT NOT NULL,
    scope_level TEXT NOT NULL,
    scope_target_id TEXT,
    is_active BOOLEAN NOT NULL DEFAULT true,
    restored_from_revision INTEGER,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (instruction_id, revision)
);

ALTER TABLE instruction_revisions
    ADD CONSTRAINT instruction_revisions_scope_level_check
        CHECK (scope_level IN ('assistant_system', 'workspace_project', 'chat_scenario', 'request_temporary'));

INSERT INTO instruction_revisions (
    instruction_id,
    revision,
    title,
    category,
    content,
    normalized_content,
    scope_level,
    scope_target_id,
    is_active,
    restored_from_revision,
    created_at,
    updated_at
)
SELECT
    id,
    revision,
    title,
    category,
    content,
    normalized_content,
    scope_level,
    scope_target_id,
    is_active,
    NULL,
    created_at,
    updated_at
FROM instructions;
