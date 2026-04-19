ALTER TABLE materials
    ADD COLUMN document_type TEXT NOT NULL DEFAULT 'OTHER',
    ADD COLUMN document_date DATE,
    ADD COLUMN document_number TEXT,
    ADD COLUMN author_name TEXT,
    ADD COLUMN department TEXT,
    ADD COLUMN version_label TEXT,
    ADD COLUMN language_code TEXT,
    ADD COLUMN source_trust TEXT NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN project_name TEXT,
    ADD COLUMN counterparty TEXT,
    ADD COLUMN business_status TEXT,
    ADD COLUMN period_start DATE,
    ADD COLUMN period_end DATE,
    ADD COLUMN metadata_jsonb JSONB NOT NULL DEFAULT '{"fieldOrigins":{"documentType":"DEFAULT","sourceTrust":"DEFAULT"},"fieldConfidence":{}}'::jsonb;

ALTER TABLE materials
    ADD CONSTRAINT materials_document_type_check
        CHECK (document_type IN ('POLICY', 'CONTRACT', 'REPORT', 'PROCEDURE', 'PRESENTATION', 'SPREADSHEET', 'LETTER', 'MANUAL', 'FAQ', 'OTHER')),
    ADD CONSTRAINT materials_source_trust_check
        CHECK (source_trust IN ('HIGH', 'MEDIUM', 'LOW', 'UNKNOWN'));

CREATE TABLE material_tags (
    material_id UUID NOT NULL REFERENCES materials (id) ON DELETE CASCADE,
    tag_order INTEGER NOT NULL,
    tag_value TEXT NOT NULL,
    PRIMARY KEY (material_id, tag_order)
);

CREATE INDEX materials_document_number_idx ON materials (document_number);
CREATE INDEX materials_document_date_idx ON materials (document_date);
CREATE INDEX materials_department_idx ON materials (department);
CREATE INDEX materials_project_name_idx ON materials (project_name);
CREATE INDEX materials_counterparty_idx ON materials (counterparty);
CREATE INDEX materials_business_status_idx ON materials (business_status);
CREATE INDEX materials_source_trust_idx ON materials (source_trust);
CREATE INDEX material_tags_value_idx ON material_tags (LOWER(tag_value));
