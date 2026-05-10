CREATE TABLE context_assembly_snapshots (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL UNIQUE,
    conversation_id UUID NOT NULL,
    turn_no INT NOT NULL,
    assembly_mode TEXT NOT NULL,
    original_prompt TEXT NOT NULL,
    resolved_retrieval_query TEXT,
    selected_history_jsonb JSONB NOT NULL DEFAULT '[]'::jsonb,
    dropped_items_jsonb JSONB NOT NULL DEFAULT '[]'::jsonb,
    token_budget_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb,
    final_messages_hash TEXT,
    degraded_mode BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT context_assembly_snapshots_mode_check CHECK (assembly_mode IN ('DIRECT', 'RAG')),
    CONSTRAINT context_assembly_snapshots_turn_positive CHECK (turn_no > 0),
    CONSTRAINT context_assembly_snapshots_run_fk
        FOREIGN KEY (conversation_id, run_id)
        REFERENCES chat_conversation_runs (conversation_id, run_id)
        ON DELETE CASCADE
);

CREATE INDEX context_assembly_snapshots_conversation_turn_idx
    ON context_assembly_snapshots (conversation_id, turn_no ASC);

ALTER TABLE chat_conversation_runs
    ADD CONSTRAINT chat_conversation_runs_context_assembly_fk
    FOREIGN KEY (context_assembly_id)
    REFERENCES context_assembly_snapshots (id)
    ON DELETE SET NULL;
