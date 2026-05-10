package com.example.demo.infrastructure.context;

import com.example.demo.error.ErrorType;
import com.example.demo.error.StorageException;
import com.example.demo.model.ConversationSummaryMemory;
import com.example.demo.model.ConversationSummarySourceRef;
import com.example.demo.service.context.ConversationSummaryPayload;
import com.example.demo.service.context.ConversationSummaryRefreshJob;
import com.example.demo.service.context.port.ConversationSummaryRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class PostgresConversationSummaryRepository implements ConversationSummaryRepository {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<ConversationSummarySourceRef>> SOURCE_REFS = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    public PostgresConversationSummaryRepository(
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager,
        ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<ConversationSummaryMemory> findByConversationId(String conversationId) {
        try {
            return jdbcTemplate.query(
                """
                    SELECT conversation_id,
                           summary_text,
                           summary_facts_jsonb,
                           summary_active_entities_jsonb,
                           summary_source_refs_jsonb,
                           summary_through_turn_no,
                           summary_updated_from_run_id,
                           summary_updated_at,
                           summary_status,
                           summary_version
                    FROM conversation_working_memory
                    WHERE conversation_id = ?
                    LIMIT 1
                    """,
                summaryRowMapper(),
                UUID.fromString(conversationId)
            ).stream().findFirst();
        } catch (DataAccessException exception) {
            throw storageFailure("conversation_summary.storage_read_failed", "Unable to load conversation summary", exception);
        }
    }

    @Override
    public boolean requestRefresh(String conversationId, int requestedThroughTurnNo, Instant now) {
        try {
            Instant effectiveNow = now == null ? Instant.now() : now;
            return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
                jdbcTemplate.update(
                    """
                        INSERT INTO conversation_summary_refresh_jobs (
                            conversation_id,
                            requested_through_turn_no,
                            status,
                            attempt_count,
                            created_at,
                            updated_at
                        ) VALUES (?, ?, 'PENDING', 0, ?, ?)
                        ON CONFLICT (conversation_id) DO UPDATE
                        SET requested_through_turn_no = GREATEST(
                                conversation_summary_refresh_jobs.requested_through_turn_no,
                                EXCLUDED.requested_through_turn_no
                            ),
                            status = CASE
                                WHEN conversation_summary_refresh_jobs.status = 'RUNNING' THEN 'RUNNING'
                                ELSE 'PENDING'
                            END,
                            next_retry_at = CASE
                                WHEN conversation_summary_refresh_jobs.status = 'RUNNING'
                                    THEN conversation_summary_refresh_jobs.next_retry_at
                                ELSE NULL
                            END,
                            last_error_code = NULL,
                            last_error_message = NULL,
                            updated_at = EXCLUDED.updated_at
                        """,
                    UUID.fromString(conversationId),
                    requestedThroughTurnNo,
                    Timestamp.from(effectiveNow),
                    Timestamp.from(effectiveNow)
                );
                jdbcTemplate.update(
                    """
                        INSERT INTO conversation_working_memory (
                            conversation_id,
                            summary_status,
                            created_at,
                            updated_at
                        ) VALUES (?, 'EMPTY', ?, ?)
                        ON CONFLICT (conversation_id) DO UPDATE
                        SET summary_status = CASE
                                WHEN conversation_working_memory.summary_through_turn_no < ?
                                 AND conversation_working_memory.summary_status <> 'EMPTY'
                                    THEN 'STALE'
                                ELSE conversation_working_memory.summary_status
                            END,
                            updated_at = EXCLUDED.updated_at
                        """,
                    UUID.fromString(conversationId),
                    Timestamp.from(effectiveNow),
                    Timestamp.from(effectiveNow),
                    requestedThroughTurnNo
                );
                return true;
            }));
        } catch (DataAccessException exception) {
            throw storageFailure("conversation_summary.storage_write_failed", "Unable to enqueue conversation summary refresh", exception);
        }
    }

    @Override
    public Optional<ConversationSummaryRefreshJob> claimNextRefreshJob(
        String leaseOwner,
        Instant now,
        Duration leaseDuration
    ) {
        try {
            Instant effectiveNow = now == null ? Instant.now() : now;
            Duration effectiveLease = leaseDuration == null || leaseDuration.isNegative() || leaseDuration.isZero()
                ? Duration.ofSeconds(120)
                : leaseDuration;
            return jdbcTemplate.query(
                """
                    WITH next_job AS (
                        SELECT conversation_id
                        FROM conversation_summary_refresh_jobs
                        WHERE (
                            status = 'PENDING'
                            AND (next_retry_at IS NULL OR next_retry_at <= ?)
                        ) OR (
                            status = 'RUNNING'
                            AND lease_expires_at IS NOT NULL
                            AND lease_expires_at <= ?
                        )
                        ORDER BY updated_at ASC, conversation_id ASC
                        LIMIT 1
                        FOR UPDATE SKIP LOCKED
                    )
                    UPDATE conversation_summary_refresh_jobs job
                    SET status = 'RUNNING',
                        lease_owner = ?,
                        lease_expires_at = ?,
                        updated_at = ?
                    FROM next_job
                    WHERE job.conversation_id = next_job.conversation_id
                    RETURNING job.conversation_id,
                              job.requested_through_turn_no,
                              job.status,
                              job.attempt_count,
                              job.next_retry_at,
                              job.lease_owner,
                              job.lease_expires_at,
                              job.last_error_code,
                              job.last_error_message,
                              job.created_at,
                              job.updated_at
                    """,
                jobRowMapper(),
                Timestamp.from(effectiveNow),
                Timestamp.from(effectiveNow),
                leaseOwner,
                Timestamp.from(effectiveNow.plus(effectiveLease)),
                Timestamp.from(effectiveNow)
            ).stream().findFirst();
        } catch (DataAccessException exception) {
            throw storageFailure("conversation_summary.storage_write_failed", "Unable to claim conversation summary refresh", exception);
        }
    }

    @Override
    public boolean completeRefresh(
        ConversationSummaryRefreshJob job,
        ConversationSummaryPayload payload,
        String updatedFromRunId,
        Instant now
    ) {
        if (job == null || payload == null) {
            return false;
        }
        try {
            Instant effectiveNow = now == null ? Instant.now() : now;
            return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
                if (!currentRefreshJobOwned(job)) {
                    return false;
                }
                int updatedRows = jdbcTemplate.update(
                    """
                        INSERT INTO conversation_working_memory (
                            conversation_id,
                            summary_text,
                            summary_facts_jsonb,
                            summary_active_entities_jsonb,
                            summary_source_refs_jsonb,
                            summary_through_turn_no,
                            summary_updated_from_run_id,
                            summary_updated_at,
                            summary_status,
                            summary_version,
                            created_at,
                            updated_at
                        ) VALUES (?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?, 'READY', 1, ?, ?)
                        ON CONFLICT (conversation_id) DO UPDATE
                        SET summary_text = EXCLUDED.summary_text,
                            summary_facts_jsonb = EXCLUDED.summary_facts_jsonb,
                            summary_active_entities_jsonb = EXCLUDED.summary_active_entities_jsonb,
                            summary_source_refs_jsonb = EXCLUDED.summary_source_refs_jsonb,
                            summary_through_turn_no = EXCLUDED.summary_through_turn_no,
                            summary_updated_from_run_id = EXCLUDED.summary_updated_from_run_id,
                            summary_updated_at = EXCLUDED.summary_updated_at,
                            summary_status = 'READY',
                            summary_version = conversation_working_memory.summary_version + 1,
                            version = conversation_working_memory.version + 1,
                            updated_at = EXCLUDED.updated_at
                        WHERE conversation_working_memory.summary_through_turn_no < EXCLUDED.summary_through_turn_no
                        """,
                    UUID.fromString(job.conversationId()),
                    payload.summaryText(),
                    writeJson(payload.facts()),
                    writeJson(payload.activeEntities()),
                    writeJson(payload.sourceRefs()),
                    job.requestedThroughTurnNo(),
                    updatedFromRunId == null ? null : UUID.fromString(updatedFromRunId),
                    Timestamp.from(effectiveNow),
                    Timestamp.from(effectiveNow),
                    Timestamp.from(effectiveNow)
                );
                deleteRefreshJob(job.conversationId(), job.leaseOwner());
                return updatedRows > 0;
            }));
        } catch (DataAccessException exception) {
            throw storageFailure("conversation_summary.storage_write_failed", "Unable to save conversation summary", exception);
        }
    }

    @Override
    public void failRefresh(
        ConversationSummaryRefreshJob job,
        String errorCode,
        String errorMessage,
        Instant nextRetryAt,
        boolean exhausted,
        Instant now
    ) {
        if (job == null) {
            return;
        }
        try {
            Instant effectiveNow = now == null ? Instant.now() : now;
            transactionTemplate.executeWithoutResult(status -> {
                jdbcTemplate.update(
                    """
                        UPDATE conversation_summary_refresh_jobs
                        SET status = ?,
                            attempt_count = attempt_count + 1,
                            next_retry_at = ?,
                            lease_owner = NULL,
                            lease_expires_at = NULL,
                            last_error_code = ?,
                            last_error_message = ?,
                            updated_at = ?
                        WHERE conversation_id = ?
                          AND lease_owner = ?
                          AND status = 'RUNNING'
                        """,
                    exhausted ? "FAILED" : "PENDING",
                    exhausted || nextRetryAt == null ? null : Timestamp.from(nextRetryAt),
                    trimTo(errorCode, 120),
                    trimTo(errorMessage, 1000),
                    Timestamp.from(effectiveNow),
                    UUID.fromString(job.conversationId()),
                    job.leaseOwner()
                );
                if (exhausted) {
                    jdbcTemplate.update(
                        """
                            UPDATE conversation_working_memory
                            SET summary_status = 'FAILED',
                                updated_at = ?
                            WHERE conversation_id = ?
                              AND summary_through_turn_no < ?
                            """,
                        Timestamp.from(effectiveNow),
                        UUID.fromString(job.conversationId()),
                        job.requestedThroughTurnNo()
                    );
                }
            });
        } catch (DataAccessException exception) {
            throw storageFailure("conversation_summary.storage_write_failed", "Unable to mark conversation summary refresh failed", exception);
        }
    }

    @Override
    public void deleteRefreshJob(String conversationId) {
        try {
            jdbcTemplate.update(
                "DELETE FROM conversation_summary_refresh_jobs WHERE conversation_id = ?",
                UUID.fromString(conversationId)
            );
        } catch (DataAccessException exception) {
            throw storageFailure("conversation_summary.storage_write_failed", "Unable to delete conversation summary refresh", exception);
        }
    }

    @Override
    public boolean deleteRefreshJob(ConversationSummaryRefreshJob job) {
        if (job == null || job.conversationId() == null || job.leaseOwner() == null) {
            return false;
        }
        try {
            return jdbcTemplate.update(
                """
                    DELETE FROM conversation_summary_refresh_jobs
                    WHERE conversation_id = ?
                      AND status = 'RUNNING'
                      AND lease_owner = ?
                    """,
                UUID.fromString(job.conversationId()),
                job.leaseOwner()
            ) > 0;
        } catch (DataAccessException exception) {
            throw storageFailure("conversation_summary.storage_write_failed", "Unable to delete conversation summary refresh", exception);
        }
    }

    @Override
    public boolean hasPendingRefreshJobs() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                """
                    SELECT COUNT(*)
                    FROM conversation_summary_refresh_jobs
                    WHERE status = 'PENDING'
                       OR (status = 'RUNNING' AND lease_expires_at <= NOW())
                    """,
                Integer.class
            );
            return count != null && count > 0;
        } catch (DataAccessException exception) {
            throw storageFailure("conversation_summary.storage_read_failed", "Unable to inspect conversation summary refresh queue", exception);
        }
    }

    private void deleteRefreshJob(String conversationId, String leaseOwner) {
        jdbcTemplate.update(
            """
                DELETE FROM conversation_summary_refresh_jobs
                WHERE conversation_id = ?
                  AND (? IS NULL OR lease_owner = ?)
                """,
            UUID.fromString(conversationId),
            leaseOwner,
            leaseOwner
        );
    }

    private boolean currentRefreshJobOwned(ConversationSummaryRefreshJob job) {
        if (job == null || job.conversationId() == null || job.leaseOwner() == null) {
            return false;
        }
        List<String> rows = jdbcTemplate.queryForList(
            """
                SELECT conversation_id::text
                FROM conversation_summary_refresh_jobs
                WHERE conversation_id = ?
                  AND status = 'RUNNING'
                  AND lease_owner = ?
                FOR UPDATE
                """,
            String.class,
            UUID.fromString(job.conversationId()),
            job.leaseOwner()
        );
        return !rows.isEmpty();
    }

    private RowMapper<ConversationSummaryMemory> summaryRowMapper() {
        return (resultSet, rowNum) -> new ConversationSummaryMemory(
            resultSet.getObject("conversation_id").toString(),
            resultSet.getString("summary_text"),
            readJson(resultSet.getString("summary_facts_jsonb"), STRING_LIST),
            readJson(resultSet.getString("summary_active_entities_jsonb"), STRING_LIST),
            readJson(resultSet.getString("summary_source_refs_jsonb"), SOURCE_REFS),
            toInt(resultSet.getObject("summary_through_turn_no", Long.class)),
            resultSet.getObject("summary_updated_from_run_id") == null
                ? null
                : resultSet.getObject("summary_updated_from_run_id").toString(),
            toInstantOrNull(resultSet.getTimestamp("summary_updated_at")),
            resultSet.getString("summary_status"),
            resultSet.getInt("summary_version")
        );
    }

    private RowMapper<ConversationSummaryRefreshJob> jobRowMapper() {
        return (resultSet, rowNum) -> new ConversationSummaryRefreshJob(
            resultSet.getObject("conversation_id").toString(),
            toInt(resultSet.getObject("requested_through_turn_no", Long.class)),
            resultSet.getString("status"),
            resultSet.getInt("attempt_count"),
            toInstantOrNull(resultSet.getTimestamp("next_retry_at")),
            resultSet.getString("lease_owner"),
            toInstantOrNull(resultSet.getTimestamp("lease_expires_at")),
            resultSet.getString("last_error_code"),
            resultSet.getString("last_error_message"),
            toInstant(resultSet.getTimestamp("created_at")),
            toInstant(resultSet.getTimestamp("updated_at"))
        );
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (JsonProcessingException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "conversation_summary.json_write_failed",
                "Unable to serialize conversation summary payload",
                exception
            );
        }
    }

    private <T> T readJson(String value, TypeReference<T> type) {
        try {
            return objectMapper.readValue(value == null ? "[]" : value, type);
        } catch (JsonProcessingException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "conversation_summary.json_read_failed",
                "Unable to deserialize conversation summary payload",
                exception
            );
        }
    }

    private static String trimTo(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private static int toInt(Long value) {
        if (value == null) {
            return 0;
        }
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : value.intValue();
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? Instant.now() : timestamp.toInstant();
    }

    private static Instant toInstantOrNull(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static StorageException storageFailure(String code, String message, DataAccessException exception) {
        return new StorageException(ErrorType.STORAGE_FAILURE, code, message, exception);
    }
}
