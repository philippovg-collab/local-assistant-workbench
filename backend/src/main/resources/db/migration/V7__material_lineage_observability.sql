ALTER TABLE materials
    ADD COLUMN superseded_by_material_id UUID REFERENCES materials (id) ON DELETE SET NULL,
    ADD COLUMN supersede_reason TEXT;
