ALTER TABLE reference_workspaces
    ADD COLUMN IF NOT EXISTS description TEXT;
