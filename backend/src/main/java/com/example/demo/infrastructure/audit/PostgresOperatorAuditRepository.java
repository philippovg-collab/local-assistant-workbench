package com.example.demo.infrastructure.audit;

import com.example.demo.error.ErrorType;
import com.example.demo.error.StorageException;
import com.example.demo.service.audit.OperatorAuditEvent;
import com.example.demo.service.audit.port.OperatorAuditRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresOperatorAuditRepository implements OperatorAuditRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PostgresOperatorAuditRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(OperatorAuditEvent event) {
        try {
            jdbcTemplate.update(
                """
                    INSERT INTO operator_audit_events (
                        id,
                        occurred_at,
                        actor,
                        event_type,
                        outcome,
                        request_id,
                        method,
                        path,
                        entity_type,
                        entity_id,
                        workspace_key,
                        status_code,
                        failure_code,
                        failure_message,
                        remote_addr,
                        user_agent,
                        metadata_jsonb
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                    """,
                event.id(),
                Timestamp.from(event.occurredAt()),
                event.actor(),
                event.eventType(),
                event.outcome(),
                event.requestId(),
                event.method(),
                event.path(),
                event.entityType(),
                event.entityId(),
                event.workspaceKey(),
                event.statusCode(),
                event.failureCode(),
                event.failureMessage(),
                event.remoteAddr(),
                event.userAgent(),
                writeMetadata(event.metadata())
            );
        } catch (DataAccessException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "operator_audit.storage_write_failed",
                "Unable to write operator audit event to PostgreSQL",
                exception
            );
        }
    }

    @Override
    public int deleteEventsOlderThan(Instant cutoff) {
        try {
            return jdbcTemplate.update(
                "DELETE FROM operator_audit_events WHERE occurred_at < ?",
                Timestamp.from(cutoff)
            );
        } catch (DataAccessException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "operator_audit.storage_write_failed",
                "Unable to delete expired operator audit events from PostgreSQL",
                exception
            );
        }
    }

    private String writeMetadata(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (JsonProcessingException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "operator_audit.storage_encode_failed",
                "Unable to encode operator audit metadata",
                exception
            );
        }
    }
}
