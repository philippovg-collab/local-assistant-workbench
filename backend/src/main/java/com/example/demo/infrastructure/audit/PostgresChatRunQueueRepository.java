package com.example.demo.infrastructure.audit;

import com.example.demo.api.ApiException;
import com.example.demo.model.ChatExecutionRequest;
import com.example.demo.model.ChatMode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class PostgresChatRunQueueRepository {

    private static final ObjectMapper JSON_MAPPER = JsonMapper.builder().findAndAddModules().build();

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public PostgresChatRunQueueRepository(
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public EnqueuedChatRun enqueue(ChatExecutionRequest request, ChatMode mode, Instant createdAt) {
        Instant effectiveCreatedAt = createdAt == null ? Instant.now() : createdAt;
        ChatMode effectiveMode = mode == null ? ChatMode.DIRECT : mode;
        ChatExecutionRequest normalizedRequest = normalizedRequest(effectiveMode, request);
        String runId = UUID.randomUUID().toString();

        write(() -> {
            UUID runUuid = UUID.fromString(runId);
            jdbcTemplate.update(
                """
                    INSERT INTO chat_run_headers (
                        id,
                        mode,
                        status,
                        requested_model,
                        requested_answer_mode,
                        created_at
                    ) VALUES (?, ?, 'RECEIVED', ?, ?, ?)
                    """,
                runUuid,
                effectiveMode.name(),
                request.model(),
                request.answerMode() == null ? null : request.answerMode().value(),
                Timestamp.from(effectiveCreatedAt)
            );
            jdbcTemplate.update(
                """
                    INSERT INTO chat_run_request_snapshots (
                        run_id,
                        request_jsonb,
                        normalized_request_jsonb,
                        prompt,
                        knowledge_scope_jsonb,
                        retrieval_filters_jsonb,
                        created_at
                    ) VALUES (?, ?::jsonb, ?::jsonb, ?, ?::jsonb, ?::jsonb, ?)
                    """,
                runUuid,
                writeJson(request),
                writeJson(normalizedRequest),
                normalizedRequest.prompt(),
                writeJson(normalizedRequest.knowledgeScope()),
                writeJson(normalizedRequest.retrievalFilters()),
                Timestamp.from(effectiveCreatedAt)
            );
            insertEvent(runUuid, "RECEIVED", Map.of("mode", effectiveMode.toValue()), effectiveCreatedAt);
            jdbcTemplate.update(
                """
                    INSERT INTO chat_run_queue (
                        run_id,
                        delivery_state,
                        attempt_count,
                        claimed_at,
                        created_at,
                        updated_at
                    ) VALUES (?, 'PENDING', 0, NULL, ?, ?)
                    """,
                runUuid,
                Timestamp.from(effectiveCreatedAt),
                Timestamp.from(effectiveCreatedAt)
            );
            insertEvent(runUuid, "QUEUED", Map.of(), effectiveCreatedAt);
            return null;
        });

        return new EnqueuedChatRun(runId, effectiveCreatedAt);
    }

    public Optional<ChatRunQueueLease> claimNext(Instant claimedAt) {
        Instant effectiveClaimedAt = claimedAt == null ? Instant.now() : claimedAt;
        return write(() -> {
            List<UUID> runIds = jdbcTemplate.query(
                """
                    SELECT q.run_id
                    FROM chat_run_queue q
                    JOIN chat_run_headers h ON h.id = q.run_id
                    JOIN chat_run_request_snapshots r ON r.run_id = q.run_id
                    WHERE q.delivery_state = 'PENDING'
                      AND h.status <> 'FAILED'
                      AND h.status <> 'COMPLETED'
                      AND h.status <> 'CANCELLED'
                    ORDER BY q.created_at ASC, q.run_id ASC
                    LIMIT 1
                    FOR UPDATE SKIP LOCKED
                    """,
                (resultSet, rowNum) -> resultSet.getObject("run_id", UUID.class)
            );
            if (runIds.isEmpty()) {
                return Optional.empty();
            }

            UUID runId = runIds.getFirst();
            int updated = jdbcTemplate.update(
                """
                    UPDATE chat_run_queue
                    SET delivery_state = 'IN_PROGRESS',
                        attempt_count = attempt_count + 1,
                        claimed_at = ?,
                        updated_at = ?
                    WHERE run_id = ?
                      AND delivery_state = 'PENDING'
                    """,
                Timestamp.from(effectiveClaimedAt),
                Timestamp.from(effectiveClaimedAt),
                runId
            );
            if (updated == 0) {
                return Optional.empty();
            }

            ChatRunQueueLease lease = loadLease(runId).orElseThrow();
            insertEvent(runId, "CLAIMED", Map.of("attemptCount", lease.attemptCount()), effectiveClaimedAt);
            return Optional.of(lease);
        });
    }

    public void recoverStaleClaims(Instant staleBefore, Instant observedAt) {
        if (staleBefore == null) {
            return;
        }
        Instant effectiveObservedAt = observedAt == null ? Instant.now() : observedAt;
        write(() -> {
            List<StaleClaim> requeueCandidates = findStaleClaims(staleBefore, true);
            for (StaleClaim claim : requeueCandidates) {
                int updated = jdbcTemplate.update(
                    """
                        UPDATE chat_run_queue
                        SET delivery_state = 'PENDING',
                            claimed_at = NULL,
                            updated_at = ?
                        WHERE run_id = ?
                          AND delivery_state = 'IN_PROGRESS'
                          AND claimed_at = ?
                          AND attempt_count < 2
                        """,
                    Timestamp.from(effectiveObservedAt),
                    claim.runId(),
                    Timestamp.from(claim.claimedAt())
                );
                if (updated > 0) {
                    insertEvent(
                        claim.runId(),
                        "REQUEUED_AFTER_STALE_CLAIM",
                        Map.of("attemptCount", claim.attemptCount()),
                        effectiveObservedAt
                    );
                }
            }

            List<StaleClaim> abandonedClaims = findStaleClaims(staleBefore, false);
            for (StaleClaim claim : abandonedClaims) {
                String message = "Chat run execution claim expired after restart and the resume limit was exhausted.";
                int transitioned = jdbcTemplate.update(
                    """
                        UPDATE chat_run_headers
                        SET status = 'FAILED',
                            failed_at = ?,
                            latency_ms_total = ?,
                            failure_stage = 'QUEUE',
                            failure_code = 'chat_run.execution_abandoned',
                            failure_message = ?
                        WHERE id = ?
                          AND status <> 'FAILED'
                          AND status <> 'COMPLETED'
                          AND status <> 'CANCELLED'
                        """,
                    Timestamp.from(effectiveObservedAt),
                    Duration.between(claim.createdAt(), effectiveObservedAt).toMillis(),
                    message,
                    claim.runId()
                );
                if (transitioned > 0) {
                    insertEvent(claim.runId(), "FAILED", Map.of(
                        "stage",
                        "QUEUE",
                        "code",
                        "chat_run.execution_abandoned",
                        "message",
                        message
                    ), effectiveObservedAt);
                }
                deleteQueueEntry(claim.runId());
            }

            deleteTerminalQueueEntries();
            return null;
        });
    }

    public void deleteQueueEntry(String runId) {
        if (runId == null || runId.isBlank()) {
            return;
        }
        write(() -> {
            deleteQueueEntry(UUID.fromString(runId));
            return null;
        });
    }

    public boolean hasPendingRuns() {
        return read(() -> {
            Integer count = jdbcTemplate.queryForObject(
                """
                    SELECT COUNT(*)
                    FROM chat_run_queue q
                    JOIN chat_run_headers h ON h.id = q.run_id
                    WHERE q.delivery_state = 'PENDING'
                      AND h.status <> 'FAILED'
                      AND h.status <> 'COMPLETED'
                      AND h.status <> 'CANCELLED'
                    """,
                Integer.class
            );
            return count != null && count > 0;
        });
    }

    private List<StaleClaim> findStaleClaims(Instant staleBefore, boolean resumable) {
        return jdbcTemplate.query(
            """
                SELECT q.run_id, q.attempt_count, q.claimed_at, h.created_at
                FROM chat_run_queue q
                JOIN chat_run_headers h ON h.id = q.run_id
                WHERE q.delivery_state = 'IN_PROGRESS'
                  AND q.claimed_at IS NOT NULL
                  AND q.claimed_at < ?
                  AND q.attempt_count %s
                  AND h.status <> 'FAILED'
                  AND h.status <> 'COMPLETED'
                  AND h.status <> 'CANCELLED'
                ORDER BY q.claimed_at ASC, q.run_id ASC
                FOR UPDATE OF q, h SKIP LOCKED
                """.formatted(resumable ? "< 2" : ">= 2"),
            (resultSet, rowNum) -> new StaleClaim(
                resultSet.getObject("run_id", UUID.class),
                resultSet.getInt("attempt_count"),
                toInstant(resultSet.getTimestamp("claimed_at")),
                toInstant(resultSet.getTimestamp("created_at"))
            ),
            Timestamp.from(staleBefore)
        );
    }

    private Optional<ChatRunQueueLease> loadLease(UUID runId) {
        return jdbcTemplate.query(
            """
                SELECT q.run_id, q.attempt_count, q.claimed_at, h.created_at, r.request_jsonb
                FROM chat_run_queue q
                JOIN chat_run_headers h ON h.id = q.run_id
                JOIN chat_run_request_snapshots r ON r.run_id = q.run_id
                WHERE q.run_id = ?
                LIMIT 1
                """,
            leaseRowMapper(),
            runId
        ).stream().findFirst();
    }

    private RowMapper<ChatRunQueueLease> leaseRowMapper() {
        return (resultSet, rowNum) -> new ChatRunQueueLease(
            resultSet.getObject("run_id").toString(),
            readJson(resultSet.getString("request_jsonb"), ChatExecutionRequest.class),
            toInstant(resultSet.getTimestamp("created_at")),
            toInstant(resultSet.getTimestamp("claimed_at")),
            resultSet.getInt("attempt_count")
        );
    }

    private void insertEvent(UUID runId, String eventType, Object payload, Instant createdAt) {
        jdbcTemplate.update(
            """
                INSERT INTO chat_run_events (
                    id,
                    run_id,
                    event_type,
                    event_payload_jsonb,
                    created_at
                ) VALUES (?, ?, ?, ?::jsonb, ?)
                """,
            UUID.randomUUID(),
            runId,
            eventType,
            writeJson(payload),
            Timestamp.from(createdAt)
        );
    }

    private void deleteQueueEntry(UUID runId) {
        jdbcTemplate.update(
            "DELETE FROM chat_run_queue WHERE run_id = ?",
            runId
        );
    }

    private void deleteTerminalQueueEntries() {
        jdbcTemplate.update(
            """
                DELETE FROM chat_run_queue q
                USING chat_run_headers h
                WHERE h.id = q.run_id
                  AND (h.status = 'FAILED' OR h.status = 'COMPLETED' OR h.status = 'CANCELLED')
                """
        );
    }

    private ChatExecutionRequest normalizedRequest(ChatMode mode, ChatExecutionRequest request) {
        return new ChatExecutionRequest(
            mode,
            request.model(),
            request.prompt(),
            request.systemPrompt(),
            request.instructionIds(),
            request.answerMode(),
            request.knowledgeScope(),
            request.instructionWorkspaceKey(),
            request.retrievalFilters(),
            request.dismissedRetrievalHintKeys(),
            request.scenarioInstructionIds(),
            request.temporaryInstruction()
        );
    }

    private <T> T write(QueueOperation<T> operation) {
        try {
            return transactionTemplate.execute(status -> operation.execute());
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "chat_run.queue_write_failed",
                "Unable to persist chat run queue state in PostgreSQL",
                exception
            );
        }
    }

    private <T> T read(QueueOperation<T> operation) {
        try {
            return operation.execute();
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "chat_run.queue_read_failed",
                "Unable to load chat run queue state from PostgreSQL",
                exception
            );
        }
    }

    private String writeJson(Object value) {
        try {
            return JSON_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "chat_run.queue_encode_failed",
                "Unable to encode chat run queue JSON",
                exception
            );
        }
    }

    private <T> T readJson(String rawJson, Class<T> type) {
        try {
            return JSON_MAPPER.readValue(rawJson, type);
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "chat_run.queue_decode_failed",
                "Unable to decode chat run queue JSON",
                exception
            );
        }
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record StaleClaim(
        UUID runId,
        int attemptCount,
        Instant claimedAt,
        Instant createdAt
    ) {
    }

    @FunctionalInterface
    private interface QueueOperation<T> {
        T execute();
    }
}
