ALTER TABLE materials
    ADD COLUMN chunk_profile TEXT NOT NULL DEFAULT 'fixed-v1';

CREATE TABLE material_segments (
    material_id UUID NOT NULL REFERENCES materials (id) ON DELETE CASCADE,
    segment_index INTEGER NOT NULL,
    segment_text TEXT NOT NULL,
    page INTEGER,
    extractor TEXT NOT NULL,
    ocr_used BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (material_id, segment_index)
);

CREATE INDEX material_segments_material_page_idx
    ON material_segments (material_id, page, segment_index);
