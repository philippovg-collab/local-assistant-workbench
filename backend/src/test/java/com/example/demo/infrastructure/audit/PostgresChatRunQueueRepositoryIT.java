package com.example.demo.infrastructure.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.model.ChatExecutionRequest;
import com.example.demo.model.ChatMode;
import com.example.demo.service.audit.ChatRunQueueLease;
import com.example.demo.service.audit.EnqueuedChatRun;
import com.example.demo.service.audit.port.ChatRunQueueRepository;
import com.example.demo.support.PostgresIntegrationTestSupport;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class PostgresChatRunQueueRepositoryIT extends PostgresIntegrationTestSupport {

    private PostgresChatRunQueueRepository repository;
    private JdbcTemplate jdbcTemplate;
    private TestDatabase database;

    @BeforeEach
    void setUp() {
        database = resetDatabase();
        jdbcTemplate = database.jdbcTemplate();
        repository = new PostgresChatRunQueueRepository(jdbcTemplate, database.transactionManager());
    }

    @Test
    void enqueueCreatesHeaderRequestSnapshotEventsAndQueueRowAtomically() {
        Instant createdAt = Instant.parse("2026-04-19T00:00:00Z");

        EnqueuedChatRun run = repository.enqueue(request("Hello"), ChatMode.DIRECT, createdAt);

        assertEquals("RECEIVED", stringValue("SELECT status FROM chat_run_headers WHERE id = ?::uuid", run.runId()));
        assertEquals("Hello", stringValue("SELECT prompt FROM chat_run_request_snapshots WHERE run_id = ?::uuid", run.runId()));
        assertEquals("PENDING", stringValue("SELECT delivery_state FROM chat_run_queue WHERE run_id = ?::uuid", run.runId()));
        assertEquals(List.of("RECEIVED", "QUEUED"), eventTypes(run.runId()));
    }

    @Test
    void claimMovesPendingRunToInProgressAndReturnsSavedRequest() {
        EnqueuedChatRun run = repository.enqueue(request("Resume me"), ChatMode.RAG, Instant.parse("2026-04-19T00:00:00Z"));
        Instant claimedAt = Instant.parse("2026-04-19T00:00:05Z");

        ChatRunQueueLease lease = repository.claimNext("worker-a", claimedAt, Duration.ofMinutes(5)).orElseThrow();

        assertEquals(run.runId(), lease.runId());
        assertEquals("Resume me", lease.request().prompt());
        assertEquals(1, lease.attemptCount());
        assertEquals(claimedAt, lease.claimedAt());
        assertEquals("worker-a", lease.leaseOwner());
        assertEquals(claimedAt.plus(Duration.ofMinutes(5)), lease.leaseExpiresAt());
        assertEquals("IN_PROGRESS", stringValue("SELECT delivery_state FROM chat_run_queue WHERE run_id = ?::uuid", run.runId()));
        assertEquals(1, intValue("SELECT attempt_count FROM chat_run_queue WHERE run_id = ?::uuid", run.runId()));
        assertEquals("worker-a", stringValue("SELECT lease_owner FROM chat_run_queue WHERE run_id = ?::uuid", run.runId()));
        assertTrue(eventTypes(run.runId()).contains("CLAIMED"));
    }

    @Test
    void claimSkipsRowsLockedByAnotherTransaction() throws Exception {
        EnqueuedChatRun lockedRun = repository.enqueue(request("Locked"), ChatMode.DIRECT, Instant.parse("2026-04-19T00:00:00Z"));
        EnqueuedChatRun availableRun = repository.enqueue(request("Available"), ChatMode.DIRECT, Instant.parse("2026-04-19T00:00:01Z"));

        try (Connection connection = database.dataSource().getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(
                "SELECT run_id FROM chat_run_queue WHERE run_id = ?::uuid FOR UPDATE"
            )) {
                statement.setString(1, lockedRun.runId());
                try (ResultSet resultSet = statement.executeQuery()) {
                    assertTrue(resultSet.next());
                }

                ChatRunQueueLease lease = repository.claimNext(
                    "worker-a",
                    Instant.parse("2026-04-19T00:00:05Z"),
                    Duration.ofMinutes(5)
                ).orElseThrow();

                assertEquals(availableRun.runId(), lease.runId());
                assertNotEquals(lockedRun.runId(), lease.runId());
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void staleClaimIsRequeuedOnceAndThenFailedAsAbandoned() {
        EnqueuedChatRun run = repository.enqueue(request("Recover"), ChatMode.DIRECT, Instant.parse("2026-04-19T00:00:00Z"));
        ChatRunQueueLease firstLease = repository.claimNext(
            "worker-a",
            Instant.parse("2026-04-19T00:00:05Z"),
            Duration.ofMinutes(5)
        ).orElseThrow();

        repository.recoverExpiredLeases(Instant.parse("2026-04-19T00:06:00Z"), 2);

        assertEquals("PENDING", stringValue("SELECT delivery_state FROM chat_run_queue WHERE run_id = ?::uuid", run.runId()));
        assertTrue(eventTypes(run.runId()).contains("REQUEUED_AFTER_EXPIRED_LEASE"));

        ChatRunQueueLease secondLease = repository.claimNext(
            "worker-b",
            Instant.parse("2026-04-19T00:06:05Z"),
            Duration.ofMinutes(5)
        ).orElseThrow();
        assertEquals(2, secondLease.attemptCount());
        assertEquals("worker-b", secondLease.leaseOwner());

        repository.recoverExpiredLeases(Instant.parse("2026-04-19T00:12:00Z"), 2);

        assertEquals("FAILED", stringValue("SELECT status FROM chat_run_headers WHERE id = ?::uuid", run.runId()));
        assertEquals(
            "chat_run.execution_abandoned",
            stringValue("SELECT failure_code FROM chat_run_headers WHERE id = ?::uuid", run.runId())
        );
        assertEquals(0, intValue("SELECT COUNT(*) FROM chat_run_queue WHERE run_id = ?::uuid", run.runId()));
        assertEquals("FAILED", eventTypes(run.runId()).getLast());
    }

    @Test
    void secondClaimDoesNotReturnAlreadyClaimedRun() {
        EnqueuedChatRun run = repository.enqueue(request("Single"), ChatMode.DIRECT, Instant.parse("2026-04-19T00:00:00Z"));

        ChatRunQueueLease firstLease = repository.claimNext(
            "worker-a",
            Instant.parse("2026-04-19T00:00:05Z"),
            Duration.ofMinutes(5)
        ).orElseThrow();
        Optional<ChatRunQueueLease> secondLease = repository.claimNext(
            "worker-b",
            Instant.parse("2026-04-19T00:00:06Z"),
            Duration.ofMinutes(5)
        );

        assertEquals(run.runId(), firstLease.runId());
        assertFalse(secondLease.isPresent());
    }

    @Test
    void heartbeatPreventsExpiredLeaseRecovery() {
        EnqueuedChatRun run = repository.enqueue(request("Heartbeat"), ChatMode.DIRECT, Instant.parse("2026-04-19T00:00:00Z"));
        ChatRunQueueLease lease = repository.claimNext(
            "worker-a",
            Instant.parse("2026-04-19T00:00:05Z"),
            Duration.ofMinutes(5)
        ).orElseThrow();

        assertTrue(repository.extendLease(
            lease,
            Instant.parse("2026-04-19T00:04:00Z"),
            Instant.parse("2026-04-19T00:09:00Z")
        ));
        ChatRunQueueRepository.RecoverySummary summary = repository.recoverExpiredLeases(
            Instant.parse("2026-04-19T00:06:00Z"),
            2
        );

        assertEquals(0, summary.requeuedCount());
        assertEquals(0, summary.abandonedCount());
        assertEquals("IN_PROGRESS", stringValue("SELECT delivery_state FROM chat_run_queue WHERE run_id = ?::uuid", run.runId()));
        assertEquals("worker-a", stringValue("SELECT lease_owner FROM chat_run_queue WHERE run_id = ?::uuid", run.runId()));
    }

    @Test
    void oldOwnerCannotDeleteAfterExpiredLeaseIsReclaimed() {
        EnqueuedChatRun run = repository.enqueue(request("Ownership"), ChatMode.DIRECT, Instant.parse("2026-04-19T00:00:00Z"));
        ChatRunQueueLease firstLease = repository.claimNext(
            "worker-a",
            Instant.parse("2026-04-19T00:00:05Z"),
            Duration.ofMinutes(5)
        ).orElseThrow();
        repository.recoverExpiredLeases(Instant.parse("2026-04-19T00:06:00Z"), 2);
        ChatRunQueueLease secondLease = repository.claimNext(
            "worker-b",
            Instant.parse("2026-04-19T00:06:05Z"),
            Duration.ofMinutes(5)
        ).orElseThrow();

        assertFalse(repository.deleteQueueEntryIfOwned(firstLease));
        assertTrue(repository.deleteQueueEntryIfOwned(secondLease));
        assertEquals(0, intValue("SELECT COUNT(*) FROM chat_run_queue WHERE run_id = ?::uuid", run.runId()));
    }

    private ChatExecutionRequest request(String prompt) {
        return new ChatExecutionRequest(ChatMode.DIRECT, "qwen2.5:7b", prompt, null, List.of());
    }

    private List<String> eventTypes(String runId) {
        return jdbcTemplate.queryForList(
            "SELECT event_type FROM chat_run_events WHERE run_id = ?::uuid ORDER BY created_at ASC",
            String.class,
            runId
        );
    }

    private String stringValue(String sql, String runId) {
        return jdbcTemplate.queryForObject(sql, String.class, runId);
    }

    private int intValue(String sql, String runId) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, runId);
        return value == null ? 0 : value;
    }
}
