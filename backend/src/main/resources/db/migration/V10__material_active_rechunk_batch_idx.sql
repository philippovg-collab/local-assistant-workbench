CREATE INDEX materials_active_created_at_id_idx
    ON materials (created_at, id)
    WHERE version_state = 'ACTIVE';
