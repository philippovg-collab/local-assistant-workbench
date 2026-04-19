ALTER TABLE material_chunks
    ADD COLUMN chunk_type TEXT,
    ADD COLUMN section_path TEXT[],
    ADD COLUMN heading_trail TEXT[],
    ADD COLUMN table_id TEXT,
    ADD COLUMN slide_id TEXT,
    ADD COLUMN parser_confidence TEXT;
