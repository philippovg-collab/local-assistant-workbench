CREATE TABLE chat_run_headers (
    id UUID PRIMARY KEY,
    mode TEXT NOT NULL,
    status TEXT NOT NULL,
    requested_model TEXT,
    resolved_model TEXT,
    requested_answer_mode TEXT,
    applied_answer_mode TEXT,
    context_status TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    failed_at TIMESTAMPTZ,
    latency_ms_total BIGINT,
    failure_stage TEXT,
    failure_code TEXT,
    failure_message TEXT,
    backend_version TEXT,
    git_commit TEXT,
    schema_version TEXT NOT NULL DEFAULT 'p0'
);

CREATE INDEX chat_run_headers_created_at_idx ON chat_run_headers (created_at DESC);
CREATE INDEX chat_run_headers_status_created_at_idx ON chat_run_headers (status, created_at DESC);

CREATE TABLE chat_run_request_snapshots (
    run_id UUID PRIMARY KEY REFERENCES chat_run_headers (id) ON DELETE CASCADE,
    request_jsonb JSONB NOT NULL,
    normalized_request_jsonb JSONB NOT NULL,
    prompt TEXT NOT NULL,
    knowledge_scope_jsonb JSONB,
    retrieval_filters_jsonb JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE chat_run_prompt_snapshots (
    run_id UUID PRIMARY KEY REFERENCES chat_run_headers (id) ON DELETE CASCADE,
    base_system_prompt TEXT,
    system_instructions_text TEXT,
    safety_instructions_text TEXT,
    context_instructions_text TEXT,
    user_instructions_text TEXT,
    temporary_instruction_text TEXT,
    answer_mode_block_text TEXT,
    grounding_block_text TEXT,
    grounding_rules_applied BOOLEAN NOT NULL DEFAULT false,
    resolved_system_prompt TEXT,
    messages_jsonb JSONB NOT NULL DEFAULT '[]'::jsonb,
    prompt_hash TEXT,
    instruction_trace_jsonb JSONB NOT NULL DEFAULT '[]'::jsonb,
    knowledge_scope_resolved_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX chat_run_prompt_snapshots_prompt_hash_idx ON chat_run_prompt_snapshots (prompt_hash);

CREATE TABLE chat_run_retrieval_summaries (
    run_id UUID PRIMARY KEY REFERENCES chat_run_headers (id) ON DELETE CASCADE,
    retrieval_status TEXT NOT NULL,
    trace_jsonb JSONB,
    debug_jsonb JSONB,
    lexical_provider TEXT,
    relevance_profile TEXT,
    embedding_model TEXT,
    chunk_profile TEXT,
    query_hints_jsonb JSONB,
    manual_filters_jsonb JSONB,
    effective_filters_jsonb JSONB,
    rollout_flags_jsonb JSONB,
    applied_capabilities_jsonb JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE chat_run_llm_calls (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES chat_run_headers (id) ON DELETE CASCADE,
    provider TEXT NOT NULL,
    model TEXT,
    request_messages_jsonb JSONB NOT NULL,
    raw_response_text TEXT,
    parsed_answer_text TEXT,
    prompt_tokens INT,
    completion_tokens INT,
    total_tokens INT,
    latency_ms BIGINT,
    retry_count INT NOT NULL DEFAULT 0,
    timeout_seconds INT,
    finish_reason TEXT,
    error_code TEXT,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX chat_run_llm_calls_run_created_at_idx ON chat_run_llm_calls (run_id, created_at ASC);

CREATE TABLE chat_run_outputs (
    run_id UUID PRIMARY KEY REFERENCES chat_run_headers (id) ON DELETE CASCADE,
    raw_model_answer TEXT,
    final_user_answer TEXT,
    sources_jsonb JSONB NOT NULL DEFAULT '[]'::jsonb,
    postprocess_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb,
    abstained BOOLEAN,
    strict_sources_blocked_answer BOOLEAN,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE chat_run_events (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES chat_run_headers (id) ON DELETE CASCADE,
    event_type TEXT NOT NULL,
    event_payload_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX chat_run_events_run_created_at_idx ON chat_run_events (run_id, created_at ASC);
