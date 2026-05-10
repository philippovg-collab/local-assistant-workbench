CREATE TABLE conversation_working_memory (
    conversation_id UUID PRIMARY KEY REFERENCES chat_conversations (id) ON DELETE CASCADE,
    sticky_model TEXT,
    sticky_answer_mode TEXT,
    sticky_instruction_workspace_key TEXT,
    sticky_knowledge_scope_jsonb JSONB,
    sticky_retrieval_filters_jsonb JSONB,
    sticky_instruction_ids_jsonb JSONB,
    sticky_scenario_instruction_ids_jsonb JSONB,
    updated_from_run_id UUID REFERENCES chat_run_headers (id) ON DELETE SET NULL,
    updated_through_turn_no INT,
    version INT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT conversation_working_memory_turn_nonnegative
        CHECK (updated_through_turn_no IS NULL OR updated_through_turn_no >= 0)
);

CREATE INDEX conversation_working_memory_updated_run_idx
    ON conversation_working_memory (updated_from_run_id)
    WHERE updated_from_run_id IS NOT NULL;

ALTER TABLE context_assembly_snapshots
    ADD COLUMN sticky_resolution_jsonb JSONB NOT NULL DEFAULT '{}'::jsonb;
