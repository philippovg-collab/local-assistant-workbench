DROP INDEX IF EXISTS materials_source_key_content_hash_uidx;

CREATE INDEX IF NOT EXISTS materials_source_key_content_hash_idx
    ON materials (source_key, content_hash);
