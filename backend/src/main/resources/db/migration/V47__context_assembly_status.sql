ALTER TABLE chat_conversation_runs
    ADD COLUMN context_assembly_status TEXT NOT NULL DEFAULT 'NONE';

ALTER TABLE chat_conversation_runs
    ADD CONSTRAINT chat_conversation_runs_context_assembly_status_check
    CHECK (context_assembly_status IN ('NONE', 'AVAILABLE', 'EXPIRED'));

UPDATE chat_conversation_runs
SET context_assembly_status = 'AVAILABLE'
WHERE context_assembly_id IS NOT NULL;

CREATE INDEX chat_conversation_runs_context_status_idx
    ON chat_conversation_runs (conversation_id, context_assembly_status, turn_no ASC);
