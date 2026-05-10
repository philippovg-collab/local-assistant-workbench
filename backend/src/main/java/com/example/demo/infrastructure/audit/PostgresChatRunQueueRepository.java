package com.example.demo.infrastructure.audit;

import com.example.demo.error.ErrorType;
import com.example.demo.error.StorageException;
import com.example.demo.model.ChatExecutionRequest;
import com.example.demo.model.ChatMode;
import com.example.demo.service.audit.ChatRunQueueLease;
import com.example.demo.service.audit.EnqueuedChatRun;
import com.example.demo.service.audit.port.ChatRunQueueRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class PostgresChatRunQueueRepository implements ChatRunQueueRepository {

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
                        lease_owner,
                        lease_expires_at,
                        heartbeat_at,
                        created_at,
                        updated_at
                    ) VALUES (?, 'PENDING', 0, NULL, NULL, NULL, NULL, ?, ?)
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

    public Optional<ChatRunQueueLease> claimNext(String workerId, Instant claimedAt, Duration leaseDuration) {
        String effectiveWorkerId = workerId == null || workerId.isBlank() ? "unknown" : workerId;
        Instant effectiveClaimedAt = claimedAt == null ? Instant.now() : claimedAt;
        Duration effectiveLeaseDuration = leaseDuration == null || leaseDuration.isNegative() || leaseDuration.isZero()
            ? Duration.ofSeconds(300)
            : leaseDuration;
        Instant leaseExpiresAt = effectiveClaimedAt.plus(effectiveLeaseDuration);
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
                        lease_owner = ?,
                        heartbeat_at = ?,
                        lease_expires_at = ?,
                        updated_at = ?
                    WHERE run_id = ?
                      AND delivery_state = 'PENDING'
                    """,
                Timestamp.from(effectiveClaimedAt),
                effectiveWorkerId,
                Timestamp.from(effectiveClaimedAt),
                Timestamp.from(leaseExpiresAt),
                Timestamp.from(effectiveClaimedAt),
                runId
            );
            if (updated == 0) {
                return Optional.empty();
            }

            ChatRunQueueLease lease = loadLease(runId).orElseThrow();
            insertEvent(runId, "CLAIMED", Map.of(
                "attemptCount",
                lease.attemptCount(),
                "leaseOwner",
                lease.leaseOwner(),
                "leaseExpiresAt",
                lease.leaseExpiresAt().toString()
            ), effectiveClaimedAt);
            return Optional.of(lease);
        });
    }

    public boolean extendLease(ChatRunQueueLease lease, Instant heartbeatAt, Instant leaseExpiresAt) {
        if (lease == null || lease.runId() == null || lease.leaseOwner() == null || leaseExpiresAt == null) {
            return false;
        }
        Instant effectiveHeartbeatAt = heartbeatAt == null ? Instant.now() : heartbeatAt;
        return write(() -> jdbcTemplate.update(
            """
                UPDATE chat_run_queue
                SET heartbeat_at = ?,
                    lease_expires_at = ?,
                    updated_at = ?
                WHERE run_id = ?
                  AND delivery_state = 'IN_PROGRESS'
                  AND lease_owner = ?
                  AND attempt_count = ?
                """,
            Timestamp.from(effectiveHeartbeatAt),
            Timestamp.from(leaseExpiresAt),
            Timestamp.from(effectiveHeartbeatAt),
            UUID.fromString(lease.runId()),
            lease.leaseOwner(),
            lease.attemptCount()
        ) > 0);
    }

    public boolean deleteQueueEntryIfOwned(ChatRunQueueLease lease) {
        if (lease == null || lease.runId() == null || lease.leaseOwner() == null) {
            return false;
        }
        return write(() -> jdbcTemplate.update(
            """
                DELETE FROM chat_run_queue
                WHERE run_id = ?
                  AND lease_owner = ?
                  AND attempt_count = ?
                """,
            UUID.fromString(lease.runId()),
            lease.leaseOwner(),
            lease.attemptCount()
        ) > 0);
    }

    public ChatRunQueueRepository.RecoverySummary recoverExpiredLeases(Instant observedAt, int maxAttempts) {
        Instant effectiveObservedAt = observedAt == null ? Instant.now() : observedAt;
        int effectiveMaxAttempts = Math.max(1, maxAttempts);
        return write(() -> {
            int requeuedCount = 0;
            List<ExpiredClaim> requeueCandidates = findExpiredClaims(effectiveObservedAt, effectiveMaxAttempts, true);
            for (ExpiredClaim claim : requeueCandidates) {
                int updated = jdbcTemplate.update(
                    """
                        UPDATE chat_run_queue
                        SET delivery_state = 'PENDING',
                            claimed_at = NULL,
                            lease_owner = NULL,
                            lease_expires_at = NULL,
                            heartbeat_at = NULL,
                            updated_at = ?
                        WHERE run_id = ?
                          AND delivery_state = 'IN_PROGRESS'
                          AND lease_owner = ?
                          AND lease_expires_at = ?
                          AND attempt_count = ?
                          AND attempt_count < ?
                        """,
                    Timestamp.from(effectiveObservedAt),
                    claim.runId(),
                    claim.leaseOwner(),
                    Timestamp.from(claim.leaseExpiresAt()),
                    claim.attemptCount(),
                    effectiveMaxAttempts
                );
                if (updated > 0) {
                    requeuedCount += updated;
                    insertEvent(
                        claim.runId(),
                        "REQUEUED_AFTER_EXPIRED_LEASE",
                        recoveryPayload(claim),
                        effectiveObservedAt
                    );
                }
            }

            int abandonedCount = 0;
            List<ExpiredClaim> abandonedClaims = findExpiredClaims(effectiveObservedAt, effectiveMaxAttempts, false);
            for (ExpiredClaim claim : abandonedClaims) {
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
                          AND EXISTS (
                              SELECT 1
                              FROM chat_run_queue q
                              WHERE q.run_id = chat_run_headers.id
                                AND q.delivery_state = 'IN_PROGRESS'
                                AND q.lease_owner = ?
                                AND q.lease_expires_at = ?
                                AND q.attempt_count = ?
                          )
                        """,
                    Timestamp.from(effectiveObservedAt),
                    Duration.between(claim.createdAt(), effectiveObservedAt).toMillis(),
                    message,
                    claim.runId(),
                    claim.leaseOwner(),
                    Timestamp.from(claim.leaseExpiresAt()),
                    claim.attemptCount()
                );
                if (transitioned > 0) {
                    abandonedCount += transitioned;
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
            return new ChatRunQueueRepository.RecoverySummary(requeuedCount, abandonedCount);
        });
    }

    public boolean deletePendingQueueEntry(String runId) {
        if (runId == null || runId.isBlank()) {
            return false;
        }
        return write(() -> jdbcTemplate.update(
            """
                DELETE FROM chat_run_queue
                WHERE run_id = ?
                  AND delivery_state = 'PENDING'
                """,
            UUID.fromString(runId)
        ) > 0);
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

    private List<ExpiredClaim> findExpiredClaims(Instant observedAt, int maxAttempts, boolean resumable) {
        return jdbcTemplate.query(
            """
                SELECT q.run_id, q.attempt_count, q.lease_owner, q.lease_expires_at, h.created_at
                FROM chat_run_queue q
                JOIN chat_run_headers h ON h.id = q.run_id
                WHERE q.delivery_state = 'IN_PROGRESS'
                  AND q.lease_owner IS NOT NULL
                  AND q.lease_expires_at IS NOT NULL
                  AND q.lease_expires_at < ?
                  AND q.attempt_count %s
                  AND h.status <> 'FAILED'
                  AND h.status <> 'COMPLETED'
                  AND h.status <> 'CANCELLED'
                ORDER BY q.lease_expires_at ASC, q.run_id ASC
                FOR UPDATE OF q, h SKIP LOCKED
                """.formatted(resumable ? "< ?" : ">= ?"),
            (resultSet, rowNum) -> new ExpiredClaim(
                resultSet.getObject("run_id", UUID.class),
                resultSet.getInt("attempt_count"),
                resultSet.getString("lease_owner"),
                toInstant(resultSet.getTimestamp("lease_expires_at")),
                toInstant(resultSet.getTimestamp("created_at"))
            ),
            Timestamp.from(observedAt),
            maxAttempts
        );
    }

    private Optional<ChatRunQueueLease> loadLease(UUID runId) {
        return jdbcTemplate.query(
            """
                SELECT q.run_id, q.attempt_count, q.claimed_at, q.lease_owner, q.lease_expires_at,
                       h.created_at, r.request_jsonb
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
            resultSet.getInt("attempt_count"),
            resultSet.getString("lease_owner"),
            toInstant(resultSet.getTimestamp("lease_expires_at"))
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

    private Map<String, Object> recoveryPayload(ExpiredClaim claim) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("attemptCount", claim.attemptCount());
        payload.put("leaseOwner", claim.leaseOwner());
        payload.put("leaseExpiresAt", claim.leaseExpiresAt().toString());
        return payload;
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
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
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
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
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
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
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
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "chat_run.queue_decode_failed",
                "Unable to decode chat run queue JSON",
                exception
            );
        }
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record ExpiredClaim(
        UUID runId,
        int attemptCount,
        String leaseOwner,
        Instant leaseExpiresAt,
        Instant createdAt
    ) {
    }

    @FunctionalInterface
    private interface QueueOperation<T> {
        T execute();
    }
}
