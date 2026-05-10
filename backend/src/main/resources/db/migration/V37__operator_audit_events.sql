CREATE TABLE operator_audit_events (
    id UUID PRIMARY KEY,
    occurred_at TIMESTAMPTZ NOT NULL,
    actor TEXT,
    event_type TEXT NOT NULL,
    outcome TEXT NOT NULL,
    request_id TEXT,
    method TEXT,
    path TEXT,
    entity_type TEXT,
    entity_id TEXT,
    workspace_key TEXT,
    status_code INT,
    failure_code TEXT,
    failure_message TEXT,
    remote_addr TEXT,
    user_agent TEXT,
    metadata_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX operator_audit_events_occurred_at_idx
    ON operator_audit_events (occurred_at DESC);

CREATE INDEX operator_audit_events_event_occurred_at_idx
    ON operator_audit_events (event_type, occurred_at DESC);

CREATE INDEX operator_audit_events_entity_idx
    ON operator_audit_events (entity_type, entity_id, occurred_at DESC);

CREATE INDEX operator_audit_events_request_id_idx
    ON operator_audit_events (request_id);
