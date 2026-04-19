CREATE TABLE material_search_sync_events (
    id UUID PRIMARY KEY,
    material_id UUID NOT NULL REFERENCES materials (id) ON DELETE CASCADE,
    event_type TEXT NOT NULL,
    delivery_state TEXT NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ,
    claimed_at TIMESTAMPTZ,
    last_error_code TEXT,
    last_error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT material_search_sync_events_event_type_check
        CHECK (event_type IN ('UPSERT_SEARCHABLE', 'REMOVE_SEARCHABLE')),
    CONSTRAINT material_search_sync_events_delivery_state_check
        CHECK (delivery_state IN ('PENDING', 'IN_PROGRESS', 'FAILED', 'DELIVERED'))
);

CREATE INDEX material_search_sync_events_delivery_idx
    ON material_search_sync_events (delivery_state, COALESCE(next_attempt_at, created_at));

CREATE INDEX material_search_sync_events_material_created_idx
    ON material_search_sync_events (material_id, created_at);
