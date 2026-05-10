CREATE INDEX context_assembly_snapshots_created_at_idx
    ON context_assembly_snapshots (created_at ASC);

CREATE INDEX context_assembly_snapshots_conversation_created_idx
    ON context_assembly_snapshots (conversation_id, created_at DESC, turn_no DESC, id DESC);

CREATE INDEX chat_conversations_status_updated_idx
    ON chat_conversations (status, updated_at ASC);

CREATE INDEX chat_conversations_updated_idx
    ON chat_conversations (updated_at ASC);

CREATE INDEX conversation_summary_refresh_status_updated_idx
    ON conversation_summary_refresh_jobs (status, updated_at ASC);
