ALTER TABLE chat_run_queue
    ADD COLUMN lease_owner TEXT,
    ADD COLUMN lease_expires_at TIMESTAMPTZ,
    ADD COLUMN heartbeat_at TIMESTAMPTZ;

UPDATE chat_run_queue
SET lease_owner = 'legacy',
    heartbeat_at = claimed_at,
    lease_expires_at = claimed_at + interval '300 seconds'
WHERE delivery_state = 'IN_PROGRESS'
  AND claimed_at IS NOT NULL;

CREATE INDEX chat_run_queue_state_lease_created_at_idx
    ON chat_run_queue (delivery_state, lease_expires_at ASC, created_at ASC);
