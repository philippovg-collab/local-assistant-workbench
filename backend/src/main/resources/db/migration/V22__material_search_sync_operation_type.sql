ALTER TABLE material_search_sync_queue
    ADD COLUMN operation_type TEXT NOT NULL DEFAULT 'UPSERT';

ALTER TABLE material_search_sync_queue
    ADD CONSTRAINT material_search_sync_queue_operation_type_check
        CHECK (operation_type IN ('UPSERT', 'DELETE'));
