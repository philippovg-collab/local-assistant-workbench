ALTER TABLE memory_entries
    ADD CONSTRAINT memory_entries_source_conversation_fk
    FOREIGN KEY (source_conversation_id)
    REFERENCES chat_conversations (id)
    ON DELETE SET NULL;

ALTER TABLE memory_entries
    ADD CONSTRAINT memory_entries_source_run_fk
    FOREIGN KEY (source_run_id)
    REFERENCES chat_run_headers (id)
    ON DELETE SET NULL;
