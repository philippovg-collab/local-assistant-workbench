package com.example.demo.infrastructure.conversation;

import com.example.demo.error.ErrorType;
import com.example.demo.error.StorageException;
import com.example.demo.model.AnswerMode;
import com.example.demo.model.ChatMode;
import com.example.demo.service.conversation.StoredConversation;
import com.example.demo.service.conversation.StoredConversationRun;
import com.example.demo.service.conversation.port.ConversationRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresConversationRepository implements ConversationRepository {

    private static final String CONVERSATION_SELECT = """
        SELECT
            c.id,
            c.workspace_key,
            c.title,
            c.mode,
            c.status,
            c.default_model,
            c.default_answer_mode,
            c.created_at,
            c.updated_at,
            c.last_run_at,
            COUNT(cr.run_id) AS turn_count
        FROM chat_conversations c
        LEFT JOIN chat_conversation_runs cr ON cr.conversation_id = c.id
        """;

    private static final String CONVERSATION_GROUPING = """
        GROUP BY c.id, c.workspace_key, c.title, c.mode, c.status, c.default_model,
                 c.default_answer_mode, c.created_at, c.updated_at, c.last_run_at
        """;

    private static final RowMapper<StoredConversation> CONVERSATION_ROW_MAPPER = (resultSet, rowNum) ->
        new StoredConversation(
            resultSet.getString("id"),
            resultSet.getString("workspace_key"),
            resultSet.getString("title"),
            ChatMode.fromValue(resultSet.getString("mode")),
            resultSet.getString("status"),
            resultSet.getString("default_model"),
            AnswerMode.fromValue(resultSet.getString("default_answer_mode")),
            toInstant(resultSet.getTimestamp("created_at")),
            toInstant(resultSet.getTimestamp("updated_at")),
            toInstantOrNull(resultSet.getTimestamp("last_run_at")),
            resultSet.getInt("turn_count")
        );

    private static final RowMapper<StoredConversationRun> RUN_ROW_MAPPER = (resultSet, rowNum) ->
        new StoredConversationRun(
            resultSet.getString("conversation_id"),
            resultSet.getString("run_id"),
            resultSet.getInt("turn_no"),
            resultSet.getString("parent_run_id"),
            resultSet.getString("client_turn_id"),
            resultSet.getString("request_hash"),
            resultSet.getString("user_prompt"),
            resultSet.getString("context_assembly_id"),
            resultSet.getString("context_assembly_status"),
            toInstant(resultSet.getTimestamp("created_at")),
            resultSet.getString("status"),
            toInstantOrNull(resultSet.getTimestamp("completed_at")),
            toInstantOrNull(resultSet.getTimestamp("failed_at")),
            resultSet.getString("failure_code"),
            resultSet.getString("failure_message")
        );

    private final JdbcTemplate jdbcTemplate;

    public PostgresConversationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public StoredConversation createConversation(
        String id,
        String workspaceKey,
        String title,
        ChatMode mode,
        String defaultModel,
        AnswerMode defaultAnswerMode,
        Instant createdAt
    ) {
        try {
            Instant effectiveCreatedAt = createdAt == null ? Instant.now() : createdAt;
            jdbcTemplate.update(
                """
                    INSERT INTO chat_conversations (
                        id,
                        workspace_key,
                        title,
                        mode,
                        status,
                        default_model,
                        default_answer_mode,
                        created_at,
                        updated_at
                    ) VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?, ?, ?)
                    """,
                UUID.fromString(id),
                workspaceKey,
                title,
                mode.name(),
                defaultModel,
                defaultAnswerMode == null ? null : defaultAnswerMode.value(),
                Timestamp.from(effectiveCreatedAt),
                Timestamp.from(effectiveCreatedAt)
            );
            return findConversation(id).orElseThrow();
        } catch (DataAccessException exception) {
            throw storageFailure("conversation.storage_write_failed", "Unable to create conversation", exception);
        }
    }

    @Override
    public List<StoredConversation> listConversations(String workspaceKey, ChatMode mode, int limit) {
        try {
            return jdbcTemplate.query(
                CONVERSATION_SELECT
                    + """
                    WHERE (? IS NULL OR c.workspace_key = ?)
                      AND (? IS NULL OR c.mode = ?)
                    """
                    + CONVERSATION_GROUPING
                    + """
                    ORDER BY c.last_run_at DESC NULLS LAST, c.updated_at DESC, c.created_at DESC
                    LIMIT ?
                    """,
                CONVERSATION_ROW_MAPPER,
                workspaceKey,
                workspaceKey,
                mode == null ? null : mode.name(),
                mode == null ? null : mode.name(),
                Math.max(1, limit)
            );
        } catch (DataAccessException exception) {
            throw storageFailure("conversation.storage_read_failed", "Unable to list conversations", exception);
        }
    }

    @Override
    public Optional<StoredConversation> findConversation(String conversationId) {
        try {
            return jdbcTemplate.query(
                CONVERSATION_SELECT
                    + """
                    WHERE c.id = ?
                    """
                    + CONVERSATION_GROUPING
                    + """
                    LIMIT 1
                    """,
                CONVERSATION_ROW_MAPPER,
                UUID.fromString(conversationId)
            ).stream().findFirst();
        } catch (DataAccessException exception) {
            throw storageFailure("conversation.storage_read_failed", "Unable to load conversation", exception);
        }
    }

    @Override
    public Optional<StoredConversation> findConversationForUpdate(String conversationId) {
        try {
            return jdbcTemplate.query(
                """
                    SELECT
                        c.id,
                        c.workspace_key,
                        c.title,
                        c.mode,
                        c.status,
                        c.default_model,
                        c.default_answer_mode,
                        c.created_at,
                        c.updated_at,
                        c.last_run_at,
                        (
                            SELECT COUNT(*)
                            FROM chat_conversation_runs cr
                            WHERE cr.conversation_id = c.id
                        ) AS turn_count
                    FROM chat_conversations c
                    WHERE c.id = ?
                    FOR UPDATE OF c
                    """,
                CONVERSATION_ROW_MAPPER,
                UUID.fromString(conversationId)
            ).stream().findFirst();
        } catch (DataAccessException exception) {
            throw storageFailure("conversation.storage_read_failed", "Unable to lock conversation", exception);
        }
    }

    @Override
    public StoredConversation updateConversation(String conversationId, String title, String status, Instant updatedAt) {
        try {
            Instant effectiveUpdatedAt = updatedAt == null ? Instant.now() : updatedAt;
            jdbcTemplate.update(
                """
                    UPDATE chat_conversations
                    SET title = COALESCE(?, title),
                        status = COALESCE(?, status),
                        updated_at = ?
                    WHERE id = ?
                    """,
                title,
                status,
                Timestamp.from(effectiveUpdatedAt),
                UUID.fromString(conversationId)
            );
            return findConversation(conversationId).orElseThrow();
        } catch (DataAccessException exception) {
            throw storageFailure("conversation.storage_write_failed", "Unable to update conversation", exception);
        }
    }

    @Override
    public Optional<StoredConversationRun> findRunByClientTurnId(String conversationId, String clientTurnId) {
        try {
            return jdbcTemplate.query(
                runSelect()
                    + """
                    WHERE cr.conversation_id = ?
                      AND cr.client_turn_id = ?
                    LIMIT 1
                    """,
                RUN_ROW_MAPPER,
                UUID.fromString(conversationId),
                clientTurnId
            ).stream().findFirst();
        } catch (DataAccessException exception) {
            throw storageFailure("conversation.storage_read_failed", "Unable to load conversation turn", exception);
        }
    }

    @Override
    public Optional<StoredConversationRun> findRunByRunId(String runId) {
        try {
            return jdbcTemplate.query(
                runSelect()
                    + """
                    WHERE cr.run_id = ?
                    LIMIT 1
                    """,
                RUN_ROW_MAPPER,
                UUID.fromString(runId)
            ).stream().findFirst();
        } catch (DataAccessException exception) {
            throw storageFailure("conversation.storage_read_failed", "Unable to load conversation run binding", exception);
        }
    }

    @Override
    public boolean runBelongsToConversation(String conversationId, String runId) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                """
                    SELECT COUNT(*)
                    FROM chat_conversation_runs
                    WHERE conversation_id = ?
                      AND run_id = ?
                    """,
                Integer.class,
                UUID.fromString(conversationId),
                UUID.fromString(runId)
            );
            return count != null && count > 0;
        } catch (DataAccessException exception) {
            throw storageFailure("conversation.storage_read_failed", "Unable to check conversation parent run", exception);
        }
    }

    @Override
    public int nextTurnNo(String conversationId) {
        try {
            Integer nextTurnNo = jdbcTemplate.queryForObject(
                """
                    SELECT COALESCE(MAX(turn_no), 0) + 1
                    FROM chat_conversation_runs
                    WHERE conversation_id = ?
                    """,
                Integer.class,
                UUID.fromString(conversationId)
            );
            return nextTurnNo == null ? 1 : nextTurnNo;
        } catch (DataAccessException exception) {
            throw storageFailure("conversation.storage_read_failed", "Unable to allocate conversation turn number", exception);
        }
    }

    @Override
    public void insertRun(
        String conversationId,
        String runId,
        int turnNo,
        String parentRunId,
        String clientTurnId,
        String requestHash,
        String userPrompt,
        String contextAssemblyId,
        Instant createdAt
    ) {
        try {
            Instant effectiveCreatedAt = createdAt == null ? Instant.now() : createdAt;
            jdbcTemplate.update(
                """
                    INSERT INTO chat_conversation_runs (
                        conversation_id,
                        run_id,
                        turn_no,
                        parent_run_id,
                        client_turn_id,
                        request_hash,
                        user_prompt,
                        context_assembly_id,
                        created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                UUID.fromString(conversationId),
                UUID.fromString(runId),
                turnNo,
                parentRunId == null ? null : UUID.fromString(parentRunId),
                clientTurnId,
                requestHash,
                userPrompt,
                contextAssemblyId == null ? null : UUID.fromString(contextAssemblyId),
                Timestamp.from(effectiveCreatedAt)
            );
        } catch (DataAccessException exception) {
            throw storageFailure("conversation.storage_write_failed", "Unable to bind chat run to conversation", exception);
        }
    }

    @Override
    public void touchConversation(String conversationId, Instant runAt) {
        try {
            Instant effectiveRunAt = runAt == null ? Instant.now() : runAt;
            jdbcTemplate.update(
                """
                    UPDATE chat_conversations
                    SET last_run_at = ?,
                        updated_at = ?
                    WHERE id = ?
                    """,
                Timestamp.from(effectiveRunAt),
                Timestamp.from(effectiveRunAt),
                UUID.fromString(conversationId)
            );
        } catch (DataAccessException exception) {
            throw storageFailure("conversation.storage_write_failed", "Unable to update conversation activity", exception);
        }
    }

    @Override
    public List<StoredConversationRun> listRuns(String conversationId) {
        try {
            return jdbcTemplate.query(
                runSelect()
                    + """
                    WHERE cr.conversation_id = ?
                    ORDER BY cr.turn_no ASC
                    """,
                RUN_ROW_MAPPER,
                UUID.fromString(conversationId)
            );
        } catch (DataAccessException exception) {
            throw storageFailure("conversation.storage_read_failed", "Unable to list conversation runs", exception);
        }
    }

    private String runSelect() {
        return """
            SELECT
                cr.conversation_id,
                cr.run_id,
                cr.turn_no,
                cr.parent_run_id,
                cr.client_turn_id,
                cr.request_hash,
                cr.user_prompt,
                cr.context_assembly_id,
                cr.context_assembly_status,
                cr.created_at,
                h.status,
                h.completed_at,
                h.failed_at,
                h.failure_code,
                h.failure_message
            FROM chat_conversation_runs cr
            JOIN chat_run_headers h ON h.id = cr.run_id
            """;
    }

    private static StorageException storageFailure(String code, String message, DataAccessException exception) {
        return new StorageException(ErrorType.STORAGE_FAILURE, code, message, exception);
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? Instant.now() : timestamp.toInstant();
    }

    private static Instant toInstantOrNull(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
