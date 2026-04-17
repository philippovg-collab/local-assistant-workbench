UPDATE instructions
SET category = LOWER(TRIM(category));

UPDATE instructions
SET category = 'system'
WHERE category NOT IN ('system', 'user', 'context', 'safety');

ALTER TABLE instructions
    ADD CONSTRAINT instructions_category_check
    CHECK (category IN ('system', 'user', 'context', 'safety'));
