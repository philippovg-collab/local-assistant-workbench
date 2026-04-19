CREATE OR REPLACE FUNCTION material_normalize_lineage_label(raw_value TEXT)
RETURNS TEXT
LANGUAGE SQL
IMMUTABLE
AS $$
    SELECT CASE
        WHEN raw_value IS NULL OR btrim(raw_value) = '' THEN NULL
        ELSE NULLIF(
            regexp_replace(
                regexp_replace(lower(btrim(raw_value)), '[^[:alnum:]]+', '-', 'g'),
                '(^-+|-+$)',
                '',
                'g'
            ),
            ''
        )
    END
$$;

CREATE OR REPLACE FUNCTION material_normalize_file_stem(original_file_name TEXT)
RETURNS TEXT
LANGUAGE SQL
IMMUTABLE
AS $$
    SELECT material_normalize_lineage_label(
        CASE
            WHEN original_file_name IS NULL OR btrim(original_file_name) = '' THEN NULL
            ELSE regexp_replace(btrim(original_file_name), '\.[^.]+$', '')
        END
    )
$$;

CREATE OR REPLACE FUNCTION material_build_content_anchor(raw_content TEXT)
RETURNS TEXT
LANGUAGE SQL
IMMUTABLE
AS $$
    WITH normalized_content AS (
        SELECT lower(
            btrim(
                regexp_replace(
                    COALESCE(raw_content, ''),
                    E'\\s+',
                    ' ',
                    'g'
                )
            )
        ) AS content
    ),
    ordered_tokens AS (
        SELECT token, ordinality
        FROM normalized_content,
        regexp_split_to_table(
            regexp_replace(content, '[^[:alnum:]]+', ' ', 'g'),
            E'\\s+'
        ) WITH ORDINALITY AS token_row(token, ordinality)
        WHERE char_length(token) >= 2
    )
    SELECT COALESCE(
        (
            SELECT string_agg(token, '-' ORDER BY ordinality)
            FROM (
                SELECT token, ordinality
                FROM ordered_tokens
                ORDER BY ordinality
                LIMIT 12
            ) limited_tokens
        ),
        'empty'
    )
$$;

CREATE OR REPLACE FUNCTION material_resolve_explicit_lineage_title(
    raw_source_type TEXT,
    raw_title TEXT,
    raw_original_file_name TEXT
)
RETURNS TEXT
LANGUAGE SQL
IMMUTABLE
AS $$
    WITH normalized AS (
        SELECT
            COALESCE(NULLIF(lower(btrim(raw_source_type)), ''), 'text') AS source_type,
            material_normalize_lineage_label(raw_title) AS normalized_title,
            material_normalize_lineage_label(raw_original_file_name) AS normalized_file_name
    )
    SELECT CASE
        WHEN normalized_title IS NULL THEN NULL
        WHEN source_type = 'file' AND normalized_title = normalized_file_name THEN NULL
        WHEN source_type = 'text' AND normalized_title = 'text-material' THEN NULL
        ELSE normalized_title
    END
    FROM normalized
$$;

CREATE TABLE material_lineage_identities (
    source_key TEXT PRIMARY KEY,
    source_type TEXT NOT NULL,
    identity_kind TEXT NOT NULL,
    identity_key TEXT NOT NULL,
    explicit_title_norm TEXT,
    original_file_name_norm TEXT,
    file_stem_norm TEXT,
    content_anchor TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

DO $$
BEGIN
    IF EXISTS (
        WITH representative_rows AS (
            SELECT DISTINCT ON (source_key)
                source_key,
                COALESCE(NULLIF(lower(btrim(source_type)), ''), 'text') AS source_type,
                title,
                original_file_name
            FROM materials
            ORDER BY
                source_key,
                CASE WHEN version_state = 'ACTIVE' THEN 0 ELSE 1 END,
                lineage_version DESC,
                created_at DESC,
                id DESC
        ),
        normalized_identities AS (
            SELECT
                source_key,
                source_type,
                material_resolve_explicit_lineage_title(source_type, title, original_file_name) AS explicit_title_norm,
                material_normalize_file_stem(original_file_name) AS file_stem_norm
            FROM representative_rows
        )
        SELECT 1
        FROM normalized_identities
        WHERE source_type = 'file'
          AND explicit_title_norm IS NULL
          AND file_stem_norm IS NULL
    ) THEN
        RAISE EXCEPTION
            'V15 cannot backfill material_lineage_identities: found file lineage without explicit title and without a filename-derived stem';
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        WITH representative_rows AS (
            SELECT DISTINCT ON (source_key)
                source_key,
                COALESCE(NULLIF(lower(btrim(source_type)), ''), 'text') AS source_type,
                title,
                original_file_name,
                COALESCE(normalized_content, content, '') AS canonical_content,
                created_at
            FROM materials
            ORDER BY
                source_key,
                CASE WHEN version_state = 'ACTIVE' THEN 0 ELSE 1 END,
                lineage_version DESC,
                created_at DESC,
                id DESC
        ),
        canonical_identities AS (
            SELECT
                source_key,
                source_type,
                CASE
                    WHEN material_resolve_explicit_lineage_title(source_type, title, original_file_name) IS NOT NULL
                        THEN 'EXPLICIT_TITLE'
                    WHEN source_type = 'file'
                        THEN 'FILE_STEM_AND_CONTENT_ANCHOR'
                    ELSE 'CONTENT_ANCHOR'
                END AS identity_kind,
                CASE
                    WHEN material_resolve_explicit_lineage_title(source_type, title, original_file_name) IS NOT NULL
                        THEN material_resolve_explicit_lineage_title(source_type, title, original_file_name)
                    WHEN source_type = 'file'
                        THEN material_normalize_file_stem(original_file_name) || '|' || material_build_content_anchor(canonical_content)
                    ELSE material_build_content_anchor(canonical_content)
                END AS identity_key
            FROM representative_rows
        )
        SELECT 1
        FROM canonical_identities
        GROUP BY source_type, identity_kind, identity_key
        HAVING COUNT(DISTINCT source_key) > 1
    ) THEN
        RAISE EXCEPTION
            'V15 cannot backfill material_lineage_identities: canonical identity collision detected across existing source_key values';
    END IF;
END $$;

INSERT INTO material_lineage_identities (
    source_key,
    source_type,
    identity_kind,
    identity_key,
    explicit_title_norm,
    original_file_name_norm,
    file_stem_norm,
    content_anchor,
    created_at
)
WITH representative_rows AS (
    SELECT DISTINCT ON (source_key)
        source_key,
        COALESCE(NULLIF(lower(btrim(source_type)), ''), 'text') AS source_type,
        title,
        original_file_name,
        COALESCE(normalized_content, content, '') AS canonical_content,
        created_at
    FROM materials
    ORDER BY
        source_key,
        CASE WHEN version_state = 'ACTIVE' THEN 0 ELSE 1 END,
        lineage_version DESC,
        created_at DESC,
        id DESC
)
SELECT
    source_key,
    source_type,
    CASE
        WHEN material_resolve_explicit_lineage_title(source_type, title, original_file_name) IS NOT NULL
            THEN 'EXPLICIT_TITLE'
        WHEN source_type = 'file'
            THEN 'FILE_STEM_AND_CONTENT_ANCHOR'
        ELSE 'CONTENT_ANCHOR'
    END AS identity_kind,
    CASE
        WHEN material_resolve_explicit_lineage_title(source_type, title, original_file_name) IS NOT NULL
            THEN material_resolve_explicit_lineage_title(source_type, title, original_file_name)
        WHEN source_type = 'file'
            THEN material_normalize_file_stem(original_file_name) || '|' || material_build_content_anchor(canonical_content)
        ELSE material_build_content_anchor(canonical_content)
    END AS identity_key,
    material_resolve_explicit_lineage_title(source_type, title, original_file_name) AS explicit_title_norm,
    material_normalize_lineage_label(original_file_name) AS original_file_name_norm,
    material_normalize_file_stem(original_file_name) AS file_stem_norm,
    material_build_content_anchor(canonical_content) AS content_anchor,
    created_at
FROM representative_rows;

CREATE UNIQUE INDEX material_lineage_identities_lookup_uidx
    ON material_lineage_identities (source_type, identity_kind, identity_key);

CREATE INDEX material_lineage_identities_lookup_idx
    ON material_lineage_identities (source_type, identity_kind, identity_key, source_key);
