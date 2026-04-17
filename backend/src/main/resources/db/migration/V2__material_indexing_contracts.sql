ALTER TABLE materials
    ADD COLUMN indexing_status TEXT NOT NULL DEFAULT 'READY',
    ADD COLUMN status_reason_code TEXT,
    ADD COLUMN status_reason_message TEXT;

UPDATE materials
SET indexing_status = 'READY'
WHERE indexing_status IS NULL;
