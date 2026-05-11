ALTER TABLE eval_cases
    ADD COLUMN IF NOT EXISTS review_status TEXT NOT NULL DEFAULT 'DRAFT',
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS accepted_answers_jsonb JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS gold_evidence_locators_jsonb JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS required_doc_groups_jsonb JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS forbidden_document_refs_jsonb JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS tags_jsonb JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS archived_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS archived_by TEXT;

ALTER TABLE eval_cases
    ALTER COLUMN expected_mode DROP NOT NULL;

UPDATE eval_cases
SET severity = CASE severity
    WHEN 'CRITICAL' THEN 'HIGH'
    WHEN 'MAJOR' THEN 'MEDIUM'
    WHEN 'MINOR' THEN 'LOW'
    ELSE severity
END;

UPDATE eval_cases
SET accepted_answers_jsonb = CASE
        WHEN jsonb_typeof(gold_answers_jsonb) = 'array' THEN gold_answers_jsonb
        WHEN jsonb_typeof(gold_answers_jsonb -> 'acceptedAnswers') = 'array' THEN gold_answers_jsonb -> 'acceptedAnswers'
        WHEN jsonb_typeof(gold_answers_jsonb -> 'accepted') = 'array' THEN gold_answers_jsonb -> 'accepted'
        WHEN jsonb_typeof(gold_answers_jsonb -> 'answers') = 'array' THEN gold_answers_jsonb -> 'answers'
        ELSE accepted_answers_jsonb
    END,
    gold_evidence_locators_jsonb = CASE
        WHEN jsonb_typeof(gold_evidence_jsonb) = 'array' THEN gold_evidence_jsonb
        WHEN jsonb_typeof(gold_evidence_jsonb -> 'goldEvidenceLocators') = 'array' THEN gold_evidence_jsonb -> 'goldEvidenceLocators'
        WHEN jsonb_typeof(gold_evidence_jsonb -> 'evidenceLocators') = 'array' THEN gold_evidence_jsonb -> 'evidenceLocators'
        WHEN jsonb_typeof(gold_evidence_jsonb -> 'locators') = 'array' THEN gold_evidence_jsonb -> 'locators'
        WHEN jsonb_typeof(gold_evidence_jsonb -> 'evidence') = 'array' THEN gold_evidence_jsonb -> 'evidence'
        ELSE gold_evidence_locators_jsonb
    END,
    required_doc_groups_jsonb = CASE
        WHEN jsonb_typeof(gold_evidence_jsonb -> 'requiredDocGroups') = 'array' THEN gold_evidence_jsonb -> 'requiredDocGroups'
        ELSE required_doc_groups_jsonb
    END,
    forbidden_document_refs_jsonb = CASE
        WHEN jsonb_typeof(gold_evidence_jsonb -> 'forbiddenDocumentRefs') = 'array' THEN gold_evidence_jsonb -> 'forbiddenDocumentRefs'
        WHEN jsonb_typeof(gold_evidence_jsonb -> 'forbiddenDocuments') = 'array' THEN gold_evidence_jsonb -> 'forbiddenDocuments'
        WHEN jsonb_typeof(gold_evidence_jsonb -> 'forbiddenDocumentIds') = 'array' THEN gold_evidence_jsonb -> 'forbiddenDocumentIds'
        WHEN jsonb_typeof(gold_evidence_jsonb -> 'forbiddenDocIds') = 'array' THEN gold_evidence_jsonb -> 'forbiddenDocIds'
        ELSE forbidden_document_refs_jsonb
    END
WHERE accepted_answers_jsonb = '[]'::jsonb
   OR gold_evidence_locators_jsonb = '[]'::jsonb
   OR required_doc_groups_jsonb = '[]'::jsonb
   OR forbidden_document_refs_jsonb = '[]'::jsonb;

UPDATE eval_case_reviews
SET status = CASE status
    WHEN 'CANDIDATE' THEN 'DRAFT'
    WHEN 'NEEDS_REVISION' THEN 'REJECTED'
    ELSE status
END;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'eval_cases_severity_check'
    ) THEN
        ALTER TABLE eval_cases
            DROP CONSTRAINT eval_cases_severity_check;
    END IF;

    ALTER TABLE eval_cases
        ADD CONSTRAINT eval_cases_severity_check
        CHECK (severity IN ('BLOCKER', 'HIGH', 'MEDIUM', 'LOW'));

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'eval_cases_review_status_check'
    ) THEN
        ALTER TABLE eval_cases
            ADD CONSTRAINT eval_cases_review_status_check
            CHECK (review_status IN ('DRAFT', 'READY_FOR_REVIEW', 'APPROVED', 'REJECTED', 'PROMOTED', 'ARCHIVED'));
    END IF;

    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'eval_case_reviews_status_check'
    ) THEN
        ALTER TABLE eval_case_reviews
            DROP CONSTRAINT eval_case_reviews_status_check;
    END IF;

    ALTER TABLE eval_case_reviews
        ADD CONSTRAINT eval_case_reviews_status_check
        CHECK (status IN ('DRAFT', 'READY_FOR_REVIEW', 'APPROVED', 'REJECTED', 'PROMOTED', 'ARCHIVED'));
END $$;

ALTER TABLE eval_case_reviews
    ADD COLUMN IF NOT EXISTS case_revision INTEGER,
    ADD COLUMN IF NOT EXISTS metadata_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb;

UPDATE eval_case_reviews r
SET case_revision = c.revision
FROM eval_cases c
WHERE r.case_id = c.id
  AND r.case_revision IS NULL;

CREATE TABLE IF NOT EXISTS eval_case_revisions (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES eval_cases (id) ON DELETE CASCADE,
    dataset_id UUID NOT NULL REFERENCES eval_datasets (id) ON DELETE CASCADE,
    case_key TEXT NOT NULL,
    revision INTEGER NOT NULL,
    case_type TEXT NOT NULL,
    expected_mode TEXT,
    severity TEXT NOT NULL,
    question TEXT NOT NULL,
    knowledge_scope_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb,
    retrieval_filters_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb,
    gold_facts_jsonb JSONB NOT NULL DEFAULT '[]'::jsonb,
    accepted_answers_jsonb JSONB NOT NULL DEFAULT '[]'::jsonb,
    gold_evidence_locators_jsonb JSONB NOT NULL DEFAULT '[]'::jsonb,
    required_doc_groups_jsonb JSONB NOT NULL DEFAULT '[]'::jsonb,
    forbidden_document_refs_jsonb JSONB NOT NULL DEFAULT '[]'::jsonb,
    tags_jsonb JSONB NOT NULL DEFAULT '[]'::jsonb,
    origin_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb,
    review_status TEXT NOT NULL,
    active BOOLEAN NOT NULL,
    content_hash TEXT NOT NULL,
    created_by TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT eval_case_revisions_revision_positive_check CHECK (revision > 0),
    CONSTRAINT eval_case_revisions_case_type_check CHECK (case_type IN (
        'EXACT_FACT',
        'MULTI_DOCUMENT_COMPARISON',
        'DATE_VERSION_FILTER',
        'TABLE_QUESTION',
        'AMBIGUOUS_QUERY',
        'NO_ANSWER',
        'CONFLICTING_SOURCES'
    )),
    CONSTRAINT eval_case_revisions_expected_mode_check CHECK (expected_mode IS NULL OR expected_mode IN ('ANSWER', 'ABSTAIN', 'CLARIFY')),
    CONSTRAINT eval_case_revisions_severity_check CHECK (severity IN ('BLOCKER', 'HIGH', 'MEDIUM', 'LOW')),
    CONSTRAINT eval_case_revisions_review_status_check CHECK (review_status IN ('DRAFT', 'READY_FOR_REVIEW', 'APPROVED', 'REJECTED', 'PROMOTED', 'ARCHIVED')),
    CONSTRAINT eval_case_revisions_case_revision_unique UNIQUE (case_id, revision)
);

INSERT INTO eval_case_revisions (
    id,
    case_id,
    dataset_id,
    case_key,
    revision,
    case_type,
    expected_mode,
    severity,
    question,
    knowledge_scope_jsonb,
    retrieval_filters_jsonb,
    gold_facts_jsonb,
    accepted_answers_jsonb,
    gold_evidence_locators_jsonb,
    required_doc_groups_jsonb,
    forbidden_document_refs_jsonb,
    tags_jsonb,
    origin_jsonb,
    review_status,
    active,
    content_hash,
    created_at
)
SELECT md5(random()::text || clock_timestamp()::text || c.id::text)::uuid,
       c.id,
       c.dataset_id,
       c.case_key,
       c.revision,
       c.case_type,
       c.expected_mode,
       c.severity,
       c.question,
       c.knowledge_scope_jsonb,
       c.retrieval_filters_jsonb,
       CASE
           WHEN jsonb_typeof(c.gold_facts_jsonb) = 'array' THEN c.gold_facts_jsonb
           WHEN jsonb_typeof(c.gold_facts_jsonb -> 'facts') = 'array' THEN c.gold_facts_jsonb -> 'facts'
           ELSE '[]'::jsonb
       END,
       c.accepted_answers_jsonb,
       c.gold_evidence_locators_jsonb,
       c.required_doc_groups_jsonb,
       c.forbidden_document_refs_jsonb,
       c.tags_jsonb,
       c.origin_jsonb,
       c.review_status,
       c.active,
       md5(
           c.case_key
           || ':' || c.revision
           || ':' || c.case_type
           || ':' || COALESCE(c.expected_mode, '')
           || ':' || c.severity
           || ':' || c.question
           || ':' || c.knowledge_scope_jsonb::text
           || ':' || c.retrieval_filters_jsonb::text
           || ':' || c.gold_facts_jsonb::text
           || ':' || c.accepted_answers_jsonb::text
           || ':' || c.gold_evidence_locators_jsonb::text
           || ':' || c.required_doc_groups_jsonb::text
           || ':' || c.forbidden_document_refs_jsonb::text
           || ':' || c.tags_jsonb::text
       ),
       c.created_at
FROM eval_cases c
WHERE NOT EXISTS (
    SELECT 1
    FROM eval_case_revisions r
    WHERE r.case_id = c.id
      AND r.revision = c.revision
);

CREATE INDEX IF NOT EXISTS eval_case_revisions_case_idx ON eval_case_revisions (case_id, revision DESC);
CREATE INDEX IF NOT EXISTS eval_case_revisions_dataset_idx ON eval_case_revisions (dataset_id, case_key ASC, revision DESC);
CREATE INDEX IF NOT EXISTS eval_cases_dataset_active_idx ON eval_cases (dataset_id, active, case_key ASC);
CREATE INDEX IF NOT EXISTS eval_cases_review_status_idx ON eval_cases (review_status, updated_at DESC);

CREATE TABLE IF NOT EXISTS eval_dataset_versions (
    id UUID PRIMARY KEY,
    dataset_id UUID NOT NULL REFERENCES eval_datasets (id) ON DELETE CASCADE,
    version TEXT NOT NULL,
    dataset_hash TEXT NOT NULL,
    case_count INTEGER NOT NULL DEFAULT 0,
    case_revision_refs_jsonb JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_by TEXT,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT eval_dataset_versions_case_count_check CHECK (case_count >= 0),
    CONSTRAINT eval_dataset_versions_dataset_version_unique UNIQUE (dataset_id, version)
);

CREATE INDEX IF NOT EXISTS eval_dataset_versions_dataset_idx ON eval_dataset_versions (dataset_id, created_at DESC);
CREATE INDEX IF NOT EXISTS eval_dataset_versions_hash_idx ON eval_dataset_versions (dataset_hash);

CREATE TABLE IF NOT EXISTS eval_case_promotions (
    id UUID PRIMARY KEY,
    source_case_id UUID NOT NULL REFERENCES eval_cases (id) ON DELETE RESTRICT,
    source_case_revision INTEGER NOT NULL,
    target_dataset_id UUID NOT NULL REFERENCES eval_datasets (id) ON DELETE RESTRICT,
    target_case_id UUID NOT NULL REFERENCES eval_cases (id) ON DELETE RESTRICT,
    target_case_revision INTEGER NOT NULL,
    target_dataset_version_id UUID REFERENCES eval_dataset_versions (id) ON DELETE SET NULL,
    promoted_by TEXT,
    note TEXT,
    metadata_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS eval_case_promotions_source_idx ON eval_case_promotions (source_case_id, source_case_revision);
CREATE INDEX IF NOT EXISTS eval_case_promotions_target_idx ON eval_case_promotions (target_dataset_id, created_at DESC);

ALTER TABLE eval_run_items
    ADD COLUMN IF NOT EXISTS case_revision INTEGER;

UPDATE eval_run_items i
SET case_revision = c.revision
FROM eval_cases c
WHERE i.case_id = c.id
  AND i.case_revision IS NULL;

CREATE INDEX IF NOT EXISTS eval_run_items_case_revision_idx
    ON eval_run_items (case_id, case_revision)
    WHERE case_id IS NOT NULL;
