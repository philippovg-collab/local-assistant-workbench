package com.example.demo.infrastructure.audit;

import com.example.demo.error.ErrorType;
import com.example.demo.error.StorageException;
import com.example.demo.model.AnswerMode;
import com.example.demo.model.ChatMode;
import com.example.demo.service.audit.StoredChatAuditRunRecord;
import com.example.demo.service.audit.port.ChatAuditRepository;
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
public class PostgresChatAuditRepository implements ChatAuditRepository {

    private static final RowMapper<StoredChatAuditRunRecord> ROW_MAPPER = (resultSet, rowNum) ->
        new StoredChatAuditRunRecord(
            resultSet.getObject("id").toString(),
            ChatMode.valueOf(resultSet.getString("mode")),
            resultSet.getString("model"),
            resultSet.getString("prompt"),
            resultSet.getString("answer"),
            resultSet.getString("context_status"),
            resultSet.getString("answer_mode") == null ? null : AnswerMode.fromValue(resultSet.getString("answer_mode")),
            resultSet.getString("audit_jsonb"),
            toInstant(resultSet.getTimestamp("created_at"))
        );

    private final JdbcTemplate jdbcTemplate;

    public PostgresChatAuditRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<StoredChatAuditRunRecord> findAll(int limit) {
        try {
            return jdbcTemplate.query(
                """
                    SELECT id, mode, model, prompt, answer, context_status, answer_mode, audit_jsonb, created_at
                    FROM chat_audit_runs
                    ORDER BY created_at DESC
                    LIMIT ?
                    """,
                ROW_MAPPER,
                limit
            );
        } catch (DataAccessException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "chat_audit.storage_read_failed",
                "Unable to load chat audit runs from PostgreSQL",
                exception
            );
        }
    }

    public Optional<StoredChatAuditRunRecord> findById(String id) {
        try {
            List<StoredChatAuditRunRecord> records = jdbcTemplate.query(
                """
                    SELECT id, mode, model, prompt, answer, context_status, answer_mode, audit_jsonb, created_at
                    FROM chat_audit_runs
                    WHERE id = ?
                    LIMIT 1
                    """,
                ROW_MAPPER,
                UUID.fromString(id)
            );
            return records.stream().findFirst();
        } catch (DataAccessException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "chat_audit.storage_read_failed",
                "Unable to load chat audit run from PostgreSQL",
                exception
            );
        }
    }

    public int deleteRunsOlderThan(Instant cutoff) {
        try {
            return jdbcTemplate.update(
                "DELETE FROM chat_audit_runs WHERE created_at < ?",
                Timestamp.from(cutoff)
            );
        } catch (DataAccessException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "chat_audit.storage_write_failed",
                "Unable to delete expired chat audit runs from PostgreSQL",
                exception
            );
        }
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? Instant.now() : timestamp.toInstant();
    }
}
