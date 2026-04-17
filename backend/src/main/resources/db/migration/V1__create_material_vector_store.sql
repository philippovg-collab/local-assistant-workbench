CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE materials (
    id UUID PRIMARY KEY,
    title TEXT NOT NULL,
    source_type TEXT NOT NULL,
    original_file_name TEXT,
    media_type TEXT,
    content TEXT NOT NULL,
    normalized_content TEXT NOT NULL,
    content_hash TEXT NOT NULL,
    source_key TEXT NOT NULL,
    extractor TEXT NOT NULL,
    ocr_used BOOLEAN NOT NULL DEFAULT FALSE,
    page_count INTEGER,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX materials_content_hash_uidx ON materials (content_hash);
CREATE INDEX materials_source_key_updated_at_idx ON materials (source_key, updated_at DESC);

CREATE TABLE material_chunks (
    material_id UUID NOT NULL REFERENCES materials (id) ON DELETE CASCADE,
    chunk_index INTEGER NOT NULL,
    chunk_text TEXT NOT NULL,
    page INTEGER,
    extractor TEXT NOT NULL,
    ocr_used BOOLEAN NOT NULL DEFAULT FALSE,
    search_vector TSVECTOR NOT NULL,
    embedding VECTOR(768) NOT NULL,
    PRIMARY KEY (material_id, chunk_index)
);

CREATE INDEX material_chunks_search_vector_idx ON material_chunks USING GIN (search_vector);
CREATE INDEX material_chunks_embedding_hnsw_idx ON material_chunks USING HNSW (embedding vector_cosine_ops);
