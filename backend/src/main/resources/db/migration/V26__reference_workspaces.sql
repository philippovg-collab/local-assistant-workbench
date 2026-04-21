CREATE TABLE reference_workspaces (
    key TEXT PRIMARY KEY,
    name_ru TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    sort_order INTEGER NOT NULL DEFAULT 0,
    is_default BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX reference_workspaces_one_default_uidx
    ON reference_workspaces (is_default)
    WHERE is_default;

CREATE INDEX reference_workspaces_active_idx ON reference_workspaces (active);
CREATE INDEX reference_workspaces_default_idx ON reference_workspaces (is_default);
CREATE INDEX reference_workspaces_sort_idx ON reference_workspaces (sort_order ASC, name_ru ASC);

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
);
