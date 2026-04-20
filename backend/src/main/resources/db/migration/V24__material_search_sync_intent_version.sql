ALTER TABLE material_search_sync_queue
    ADD COLUMN intent_version BIGINT NOT NULL DEFAULT 1;

ALTER TABLE material_search_sync_queue
    ADD COLUMN claimed_intent_version BIGINT;

UPDATE material_search_sync_queue
SET claimed_intent_version = intent_version
WHERE delivery_state = 'IN_PROGRESS'
  AND claimed_intent_version IS NULL;

ALTER TABLE material_search_sync_queue
    ADD CONSTRAINT material_search_sync_queue_intent_version_check
        CHECK (claimed_intent_version IS NULL OR claimed_intent_version <= intent_version);
