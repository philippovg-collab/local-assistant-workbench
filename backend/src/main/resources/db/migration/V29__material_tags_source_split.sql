ALTER TABLE material_tags
    ADD COLUMN IF NOT EXISTS tag_source TEXT;

UPDATE material_tags
SET tag_source = 'MANUAL'
WHERE tag_source IS NULL OR BTRIM(tag_source) = '';

ALTER TABLE material_tags
    ALTER COLUMN tag_source SET DEFAULT 'MANUAL',
    ALTER COLUMN tag_source SET NOT NULL;

ALTER TABLE material_tags
    DROP CONSTRAINT IF EXISTS material_tags_source_check,
    ADD CONSTRAINT material_tags_source_check
        CHECK (tag_source IN ('AUTO', 'MANUAL'));

CREATE INDEX IF NOT EXISTS material_tags_source_idx
    ON material_tags (material_id, tag_source, tag_order);
