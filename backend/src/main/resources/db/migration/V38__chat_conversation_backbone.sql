CREATE TABLE chat_conversations (
    id UUID PRIMARY KEY,
    workspace_key TEXT,
    title TEXT NOT NULL,
    mode TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    default_model TEXT,
    default_answer_mode TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_run_at TIMESTAMPTZ,
    CONSTRAINT chat_conversations_mode_check CHECK (mode IN ('DIRECT', 'RAG')),
    CONSTRAINT chat_conversations_status_check CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE INDEX chat_conversations_list_idx
    ON chat_conversations (workspace_key, status, updated_at DESC, id DESC);

CREATE INDEX chat_conversations_last_run_idx
    ON chat_conversations (last_run_at DESC NULLS LAST, updated_at DESC);

CREATE TABLE chat_conversation_runs (
    conversation_id UUID NOT NULL REFERENCES chat_conversations (id) ON DELETE CASCADE,
    run_id UUID NOT NULL UNIQUE REFERENCES chat_run_headers (id) ON DELETE CASCADE,
    turn_no INT NOT NULL,
    parent_run_id UUID REFERENCES chat_run_headers (id) ON DELETE SET NULL,
    client_turn_id TEXT,
    request_hash TEXT NOT NULL,
    user_prompt TEXT NOT NULL,
    context_assembly_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (conversation_id, run_id),
    CONSTRAINT chat_conversation_runs_turn_positive CHECK (turn_no > 0),
    CONSTRAINT chat_conversation_runs_turn_unique UNIQUE (conversation_id, turn_no)
);

CREATE UNIQUE INDEX chat_conversation_runs_client_turn_unique_idx
    ON chat_conversation_runs (conversation_id, client_turn_id)
    WHERE client_turn_id IS NOT NULL;

CREATE INDEX chat_conversation_runs_order_idx
    ON chat_conversation_runs (conversation_id, turn_no ASC);

CREATE INDEX chat_conversation_runs_parent_idx
    ON chat_conversation_runs (conversation_id, parent_run_id)
    WHERE parent_run_id IS NOT NULL;
