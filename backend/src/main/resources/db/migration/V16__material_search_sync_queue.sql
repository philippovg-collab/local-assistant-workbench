CREATE TABLE material_search_sync_queue (
    material_id UUID PRIMARY KEY,
    delivery_state TEXT NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ,
    claimed_at TIMESTAMPTZ,
    last_error_code TEXT,
    last_error_message TEXT,
    requested_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT material_search_sync_queue_delivery_state_check
        CHECK (delivery_state IN ('PENDING', 'IN_PROGRESS', 'FAILED'))
);

WITH retained_events AS (
    SELECT
        material_id,
        delivery_state,
        attempt_count,
        next_attempt_at,
        last_error_code,
        last_error_message,
        created_at,
        updated_at
    FROM material_search_sync_events
    WHERE delivery_state <> 'DELIVERED'
),
latest_errors AS (
    SELECT DISTINCT ON (material_id)
        material_id,
        last_error_code,
        last_error_message
    FROM retained_events
    WHERE last_error_code IS NOT NULL OR last_error_message IS NOT NULL
    ORDER BY material_id, updated_at DESC, created_at DESC
),
collapsed_entries AS (
    SELECT
        material_id,
        CASE
            WHEN COUNT(*) FILTER (WHERE delivery_state IN ('PENDING', 'IN_PROGRESS')) > 0 THEN 'PENDING'
            ELSE 'FAILED'
        END AS delivery_state,
        CASE
            WHEN COUNT(*) FILTER (WHERE delivery_state IN ('PENDING', 'IN_PROGRESS')) > 0
                THEN COALESCE(MAX(attempt_count) FILTER (WHERE delivery_state IN ('PENDING', 'IN_PROGRESS')), 0)
            ELSE COALESCE(MAX(attempt_count) FILTER (WHERE delivery_state = 'FAILED'), 0)
        END AS attempt_count,
        MIN(next_attempt_at) FILTER (
            WHERE delivery_state = 'PENDING'
              AND next_attempt_at IS NOT NULL
        ) AS next_attempt_at,
        MAX(created_at) AS requested_at,
        MIN(created_at) AS created_at,
        MAX(updated_at) AS updated_at
    FROM retained_events
    GROUP BY material_id
)
INSERT INTO material_search_sync_queue (
    material_id,
    delivery_state,
    attempt_count,
    next_attempt_at,
    claimed_at,
    last_error_code,
    last_error_message,
    requested_at,
    created_at,
    updated_at
)
SELECT
    collapsed_entries.material_id,
    collapsed_entries.delivery_state,
    collapsed_entries.attempt_count,
    collapsed_entries.next_attempt_at,
    NULL,
    latest_errors.last_error_code,
    latest_errors.last_error_message,
    collapsed_entries.requested_at,
    collapsed_entries.created_at,
    collapsed_entries.updated_at
FROM collapsed_entries
LEFT JOIN latest_errors USING (material_id);

DROP TABLE material_search_sync_events;

CREATE INDEX material_search_sync_queue_delivery_idx
    ON material_search_sync_queue (delivery_state, COALESCE(next_attempt_at, requested_at));

CREATE INDEX material_search_sync_queue_claimed_idx
    ON material_search_sync_queue (claimed_at);
