ALTER TABLE knowledge_presets
    ADD COLUMN IF NOT EXISTS filter_kind TEXT NOT NULL DEFAULT 'PRESET',
    ADD COLUMN IF NOT EXISTS criteria_jsonb JSONB;

ALTER TABLE knowledge_preset_revisions
    ADD COLUMN IF NOT EXISTS filter_kind TEXT NOT NULL DEFAULT 'PRESET',
    ADD COLUMN IF NOT EXISTS criteria_jsonb JSONB;

UPDATE knowledge_presets
SET criteria_jsonb = jsonb_build_object(
        'presetIds', COALESCE(scope_jsonb -> 'presetIds', '[]'::jsonb),
        'facetIds', '[]'::jsonb,
        'documentClasses', COALESCE(scope_jsonb -> 'documentClasses', '[]'::jsonb),
        'documentTypes',
            CASE
                WHEN scope_jsonb ? 'documentTypes' THEN scope_jsonb -> 'documentTypes'
                ELSE (
                    SELECT COALESCE(jsonb_agg(DISTINCT mapped_type), '[]'::jsonb)
                    FROM jsonb_array_elements_text(COALESCE(scope_jsonb -> 'documentClasses', '[]'::jsonb)) AS legacy_class(value)
                    CROSS JOIN LATERAL (
                        SELECT unnest(CASE lower(legacy_class.value)
                            WHEN 'contracts' THEN ARRAY['CONTRACT']
                            WHEN 'regulations' THEN ARRAY['POLICY', 'PROCEDURE']
                            WHEN 'correspondence' THEN ARRAY['LETTER']
                            WHEN 'techdocs' THEN ARRAY['REPORT', 'PRESENTATION', 'SPREADSHEET', 'MANUAL', 'FAQ']
                            WHEN 'other' THEN ARRAY['OTHER']
                            ELSE ARRAY[]::text[]
                        END) AS mapped_type
                    ) mapped
                )
            END,
        'documentStatuses', COALESCE(scope_jsonb -> 'documentStatuses', '[]'::jsonb),
        'projectKeys', COALESCE(scope_jsonb -> 'projectKeys', '[]'::jsonb),
        'documentNumber', scope_jsonb -> 'documentNumber',
        'languageCodes', COALESCE(scope_jsonb -> 'languageCodes', '[]'::jsonb),
        'tags', COALESCE(scope_jsonb -> 'tags', '[]'::jsonb),
        'workspaceKey', scope_jsonb -> 'workspaceKey',
        'periodStartFrom', scope_jsonb -> 'periodStartFrom',
        'periodStartTo', scope_jsonb -> 'periodStartTo',
        'periodEndFrom', scope_jsonb -> 'periodEndFrom',
        'periodEndTo', scope_jsonb -> 'periodEndTo',
        'uploadedTodayOnly', COALESCE(scope_jsonb -> 'uploadedTodayOnly', 'false'::jsonb)
    )
WHERE criteria_jsonb IS NULL;

UPDATE knowledge_preset_revisions
SET criteria_jsonb = jsonb_build_object(
        'presetIds', COALESCE(scope_jsonb -> 'presetIds', '[]'::jsonb),
        'facetIds', '[]'::jsonb,
        'documentClasses', COALESCE(scope_jsonb -> 'documentClasses', '[]'::jsonb),
        'documentTypes',
            CASE
                WHEN scope_jsonb ? 'documentTypes' THEN scope_jsonb -> 'documentTypes'
                ELSE (
                    SELECT COALESCE(jsonb_agg(DISTINCT mapped_type), '[]'::jsonb)
                    FROM jsonb_array_elements_text(COALESCE(scope_jsonb -> 'documentClasses', '[]'::jsonb)) AS legacy_class(value)
                    CROSS JOIN LATERAL (
                        SELECT unnest(CASE lower(legacy_class.value)
                            WHEN 'contracts' THEN ARRAY['CONTRACT']
                            WHEN 'regulations' THEN ARRAY['POLICY', 'PROCEDURE']
                            WHEN 'correspondence' THEN ARRAY['LETTER']
                            WHEN 'techdocs' THEN ARRAY['REPORT', 'PRESENTATION', 'SPREADSHEET', 'MANUAL', 'FAQ']
                            WHEN 'other' THEN ARRAY['OTHER']
                            ELSE ARRAY[]::text[]
                        END) AS mapped_type
                    ) mapped
                )
            END,
        'documentStatuses', COALESCE(scope_jsonb -> 'documentStatuses', '[]'::jsonb),
        'projectKeys', COALESCE(scope_jsonb -> 'projectKeys', '[]'::jsonb),
        'documentNumber', scope_jsonb -> 'documentNumber',
        'languageCodes', COALESCE(scope_jsonb -> 'languageCodes', '[]'::jsonb),
        'tags', COALESCE(scope_jsonb -> 'tags', '[]'::jsonb),
        'workspaceKey', scope_jsonb -> 'workspaceKey',
        'periodStartFrom', scope_jsonb -> 'periodStartFrom',
        'periodStartTo', scope_jsonb -> 'periodStartTo',
        'periodEndFrom', scope_jsonb -> 'periodEndFrom',
        'periodEndTo', scope_jsonb -> 'periodEndTo',
        'uploadedTodayOnly', COALESCE(scope_jsonb -> 'uploadedTodayOnly', 'false'::jsonb)
    )
WHERE criteria_jsonb IS NULL;

ALTER TABLE knowledge_presets
    ALTER COLUMN criteria_jsonb SET NOT NULL,
    ADD CONSTRAINT knowledge_presets_filter_kind_check
        CHECK (filter_kind IN ('PRESET', 'FACET'));

ALTER TABLE knowledge_preset_revisions
    ALTER COLUMN criteria_jsonb SET NOT NULL,
    ADD CONSTRAINT knowledge_preset_revisions_filter_kind_check
        CHECK (filter_kind IN ('PRESET', 'FACET'));

CREATE INDEX IF NOT EXISTS knowledge_presets_kind_active_idx
    ON knowledge_presets (filter_kind, is_active, updated_at DESC);
