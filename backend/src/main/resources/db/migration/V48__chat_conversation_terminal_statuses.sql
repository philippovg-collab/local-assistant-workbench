ALTER TABLE chat_conversations
    DROP CONSTRAINT IF EXISTS chat_conversations_status_check;

ALTER TABLE chat_conversations
    ADD CONSTRAINT chat_conversations_status_check
    CHECK (status IN ('ACTIVE', 'ARCHIVED', 'SOFT_DELETED', 'DELETED'));
