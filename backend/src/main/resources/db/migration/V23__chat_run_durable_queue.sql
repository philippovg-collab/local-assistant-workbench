CREATE TABLE chat_run_queue (
    run_id UUID PRIMARY KEY REFERENCES chat_run_headers (id) ON DELETE CASCADE,
    delivery_state TEXT NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    claimed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX chat_run_queue_state_created_at_idx ON chat_run_queue (delivery_state, created_at ASC);

INSERT INTO chat_run_queue (
    run_id,
    delivery_state,
    attempt_count,
    claimed_at,
    created_at,
    updated_at
)
SELECT
    h.id,
    'PENDING',
    0,
    NULL,
    h.created_at,
    NOW()
FROM chat_run_headers h
JOIN chat_run_request_snapshots r ON r.run_id = h.id
WHERE h.status <> 'FAILED'
  AND h.status <> 'COMPLETED'
  AND h.status <> 'CANCELLED'
ON CONFLICT (run_id) DO NOTHING;

UPDATE chat_run_headers h
SET status = 'FAILED',
    failed_at = NOW(),
    failure_stage = 'QUEUE',
    failure_code = 'chat_run.request_unavailable',
    failure_message = 'Chat run request snapshot is unavailable; run cannot be resumed.',
    latency_ms_total = GREATEST(0, (EXTRACT(EPOCH FROM (NOW() - h.created_at)) * 1000)::BIGINT)
WHERE h.status <> 'FAILED'
  AND h.status <> 'COMPLETED'
  AND h.status <> 'CANCELLED'
  AND NOT EXISTS (
      SELECT 1
      FROM chat_run_request_snapshots r
      WHERE r.run_id = h.id
  );

INSERT INTO chat_run_events (
    id,
    run_id,
    event_type,
    event_payload_jsonb,
    created_at
)
SELECT
    h.id,
    h.id,
    'FAILED',
    jsonb_build_object(
        'stage', 'QUEUE',
        'code', 'chat_run.request_unavailable',
        'message', 'Chat run request snapshot is unavailable; run cannot be resumed.'
    ),
    h.failed_at
FROM chat_run_headers h
WHERE h.failure_code = 'chat_run.request_unavailable'
  AND h.failed_at IS NOT NULL
ON CONFLICT (id) DO NOTHING;
