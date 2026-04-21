CREATE TABLE reference_projects (
    key TEXT PRIMARY KEY,
    workspace_key TEXT NOT NULL REFERENCES reference_workspaces (key),
    name_ru TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX reference_projects_workspace_key_idx ON reference_projects (workspace_key);
CREATE INDEX reference_projects_active_idx ON reference_projects (active);
CREATE INDEX reference_projects_sort_idx ON reference_projects (sort_order ASC, name_ru ASC);
CREATE INDEX reference_projects_workspace_sort_idx ON reference_projects (workspace_key, sort_order ASC, name_ru ASC);
