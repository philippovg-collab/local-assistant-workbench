CREATE TABLE llm_provider_configs (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL,
    provider_type TEXT NOT NULL,
    purpose TEXT NOT NULL,
    base_url TEXT NOT NULL,
    api_key_ciphertext TEXT NULL,
    auth_header_name TEXT NOT NULL DEFAULT 'Authorization',
    auth_scheme TEXT NOT NULL DEFAULT 'Bearer',
    chat_completions_path TEXT NOT NULL DEFAULT '/v1/chat/completions',
    models_path TEXT NOT NULL DEFAULT '/v1/models',
    embeddings_path TEXT NOT NULL DEFAULT '/v1/embeddings',
    default_model TEXT NULL,
    embedding_model TEXT NULL,
    temperature NUMERIC(4,3) NOT NULL DEFAULT 0.2,
    timeout_seconds INTEGER NOT NULL DEFAULT 600,
    expected_embedding_dimension INTEGER NULL,
    active_chat BOOLEAN NOT NULL DEFAULT FALSE,
    active_embedding BOOLEAN NOT NULL DEFAULT FALSE,
    status TEXT NOT NULL DEFAULT 'UNKNOWN',
    last_probe_at TIMESTAMPTZ NULL,
    last_successful_probe_at TIMESTAMPTZ NULL,
    last_error_code TEXT NULL,
    last_error_message TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT llm_provider_configs_provider_type_chk
        CHECK (provider_type IN ('OPENAI_COMPATIBLE')),
    CONSTRAINT llm_provider_configs_purpose_chk
        CHECK (purpose IN ('CHAT', 'EMBEDDING', 'CHAT_AND_EMBEDDING')),
    CONSTRAINT llm_provider_configs_status_chk
        CHECK (status IN ('UNKNOWN', 'UP', 'DOWN', 'DEGRADED')),
    CONSTRAINT llm_provider_configs_name_chk
        CHECK (length(btrim(name)) > 0),
    CONSTRAINT llm_provider_configs_base_url_chk
        CHECK (
            base_url ~* '^https?://'
            AND base_url !~ '[[:space:]]'
            AND base_url !~* '^https?://[/?#]'
            AND base_url !~* '^https?://[^/?#]*@'
        ),
    CONSTRAINT llm_provider_configs_timeout_chk
        CHECK (timeout_seconds BETWEEN 1 AND 3600),
    CONSTRAINT llm_provider_configs_temperature_chk
        CHECK (temperature >= 0 AND temperature <= 2),
    CONSTRAINT llm_provider_configs_expected_embedding_dimension_chk
        CHECK (expected_embedding_dimension IS NULL OR expected_embedding_dimension > 0),
    CONSTRAINT llm_provider_configs_paths_chk
        CHECK (
            chat_completions_path ~ '^/'
            AND models_path ~ '^/'
            AND embeddings_path ~ '^/'
            AND chat_completions_path !~ '[[:space:]]'
            AND models_path !~ '[[:space:]]'
            AND embeddings_path !~ '[[:space:]]'
        ),
    CONSTRAINT llm_provider_configs_auth_header_name_chk
        CHECK (auth_header_name ~ '^[A-Za-z0-9-]+$'),
    CONSTRAINT llm_provider_configs_auth_scheme_chk
        CHECK (auth_scheme ~ '^[A-Za-z][A-Za-z0-9._~-]*$')
);

CREATE UNIQUE INDEX llm_provider_configs_active_chat_uidx
    ON llm_provider_configs (active_chat)
    WHERE active_chat = true;

CREATE UNIQUE INDEX llm_provider_configs_active_embedding_uidx
    ON llm_provider_configs (active_embedding)
    WHERE active_embedding = true;

CREATE INDEX llm_provider_configs_updated_at_idx
    ON llm_provider_configs (updated_at DESC);
