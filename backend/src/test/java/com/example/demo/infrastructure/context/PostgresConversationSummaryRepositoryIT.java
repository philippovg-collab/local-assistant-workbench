package com.example.demo.infrastructure.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.model.ChatMode;
import com.example.demo.model.ConversationSummaryMemory;
import com.example.demo.model.ConversationSummarySourceRef;
import com.example.demo.service.context.ConversationSummaryPayload;
import com.example.demo.service.context.ConversationSummaryRefreshJob;
import com.example.demo.support.PostgresIntegrationTestSupport;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class PostgresConversationSummaryRepositoryIT extends PostgresIntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-05-10T00:00:00Z");

    @Test
    void requestClaimCompleteRoundtripsSummaryAndCascadesWithConversation() {
        TestDatabase database = resetDatabase();
        JdbcTemplate jdbcTemplate = database.jdbcTemplate();
        String conversationId = insertConversation(jdbcTemplate);
        String runId = insertRunHeader(jdbcTemplate, ChatMode.RAG, "COMPLETED");
        PostgresConversationSummaryRepository repository = repository(database);

        assertTrue(repository.requestRefresh(conversationId, 3, NOW));
        ConversationSummaryRefreshJob job = repository
            .claimNextRefreshJob("worker-1", NOW.plusSeconds(1), Duration.ofSeconds(60))
            .orElseThrow();

        assertEquals(conversationId, job.conversationId());
        assertEquals(3, job.requestedThroughTurnNo());
        assertEquals("RUNNING", job.status());

        assertTrue(repository.completeRefresh(job, payload("summary-v1"), runId, NOW.plusSeconds(2)));
        ConversationSummaryMemory found = repository.findByConversationId(conversationId).orElseThrow();

        assertEquals("summary-v1", found.summaryText());
        assertEquals(List.of("fact"), found.facts());
        assertEquals(List.of("entity"), found.activeEntities());
        assertEquals(new ConversationSummarySourceRef("material-1", "Doc", "D-1", "Project", "Counterparty", 4),
            found.sourceRefs().getFirst());
        assertEquals(3, found.summaryThroughTurnNo());
        assertEquals(runId, found.updatedFromRunId());
        assertEquals("READY", found.status());
        assertFalse(repository.hasPendingRefreshJobs());

        jdbcTemplate.update("DELETE FROM chat_conversations WHERE id = ?::uuid", conversationId);

        assertEquals(0, count(jdbcTemplate, "conversation_working_memory"));
        assertEquals(0, count(jdbcTemplate, "conversation_summary_refresh_jobs"));
    }

    @Test
    void leasePreventsDuplicateClaimUntilExpired() {
        TestDatabase database = resetDatabase();
        String conversationId = insertConversation(database.jdbcTemplate());
        PostgresConversationSummaryRepository repository = repository(database);

        repository.requestRefresh(conversationId, 2, NOW);
        ConversationSummaryRefreshJob first = repository
            .claimNextRefreshJob("worker-1", NOW, Duration.ofSeconds(60))
            .orElseThrow();

        assertFalse(repository.claimNextRefreshJob("worker-2", NOW.plusSeconds(30), Duration.ofSeconds(60)).isPresent());
        ConversationSummaryRefreshJob reclaimed = repository
            .claimNextRefreshJob("worker-2", NOW.plusSeconds(61), Duration.ofSeconds(60))
            .orElseThrow();

        assertEquals(first.conversationId(), reclaimed.conversationId());
        assertEquals("worker-2", reclaimed.leaseOwner());
    }

    @Test
    void failureRetryAndExhaustionUpdateJobAndSummaryStatus() {
        TestDatabase database = resetDatabase();
        JdbcTemplate jdbcTemplate = database.jdbcTemplate();
        String conversationId = insertConversation(jdbcTemplate);
        PostgresConversationSummaryRepository repository = repository(database);

        repository.requestRefresh(conversationId, 2, NOW);
        ConversationSummaryRefreshJob first = repository
            .claimNextRefreshJob("worker-1", NOW, Duration.ofSeconds(60))
            .orElseThrow();
        repository.failRefresh(
            first,
            "InvalidJson",
            "strict JSON required",
            NOW.plusSeconds(30),
            false,
            NOW.plusSeconds(1)
        );

        assertEquals("PENDING", stringValue(jdbcTemplate, "status", conversationId));
        assertEquals(1, intValue(jdbcTemplate, "attempt_count", conversationId));
        assertFalse(repository.claimNextRefreshJob("worker-2", NOW.plusSeconds(10), Duration.ofSeconds(60)).isPresent());

        ConversationSummaryRefreshJob second = repository
            .claimNextRefreshJob("worker-2", NOW.plusSeconds(31), Duration.ofSeconds(60))
            .orElseThrow();
        repository.failRefresh(second, "InvalidJson", "strict JSON required", null, true, NOW.plusSeconds(32));

        assertEquals("FAILED", stringValue(jdbcTemplate, "status", conversationId));
        assertEquals("FAILED", repository.findByConversationId(conversationId).orElseThrow().status());
    }

    @Test
    void lowerRequestedTurnCannotRewindReadySummary() {
        TestDatabase database = resetDatabase();
        JdbcTemplate jdbcTemplate = database.jdbcTemplate();
        String conversationId = insertConversation(jdbcTemplate);
        String runId = insertRunHeader(jdbcTemplate, ChatMode.DIRECT, "COMPLETED");
        PostgresConversationSummaryRepository repository = repository(database);

        repository.requestRefresh(conversationId, 5, NOW);
        ConversationSummaryRefreshJob first = repository
            .claimNextRefreshJob("worker-1", NOW, Duration.ofSeconds(60))
            .orElseThrow();
        assertTrue(repository.completeRefresh(first, payload("summary-v5"), runId, NOW.plusSeconds(1)));

        repository.requestRefresh(conversationId, 3, NOW.plusSeconds(2));
        ConversationSummaryRefreshJob lower = repository
            .claimNextRefreshJob("worker-2", NOW.plusSeconds(3), Duration.ofSeconds(60))
            .orElseThrow();

        assertFalse(repository.completeRefresh(lower, payload("summary-v3"), runId, NOW.plusSeconds(4)));
        ConversationSummaryMemory found = repository.findByConversationId(conversationId).orElseThrow();

        assertEquals("summary-v5", found.summaryText());
        assertEquals(5, found.summaryThroughTurnNo());
    }

    private PostgresConversationSummaryRepository repository(TestDatabase database) {
        return new PostgresConversationSummaryRepository(
            database.jdbcTemplate(),
            database.transactionManager(),
            JsonMapper.builder().findAndAddModules().build()
        );
    }

    private ConversationSummaryPayload payload(String summaryText) {
        return new ConversationSummaryPayload(
            summaryText,
            List.of("fact"),
            List.of("entity"),
            List.of(new ConversationSummarySourceRef("material-1", "Doc", "D-1", "Project", "Counterparty", 4)),
            List.of(1, 2)
        );
    }

    private String insertConversation(JdbcTemplate jdbcTemplate) {
        String conversationId = UUID.randomUUID().toString();
        jdbcTemplate.update(
            """
                INSERT INTO chat_conversations (id, title, mode, status, created_at, updated_at)
                VALUES (?, 'Summary repository', 'RAG', 'ACTIVE', ?, ?)
                """,
            UUID.fromString(conversationId),
            Timestamp.from(NOW),
            Timestamp.from(NOW)
        );
        return conversationId;
    }

    private String insertRunHeader(JdbcTemplate jdbcTemplate, ChatMode mode, String status) {
        String runId = UUID.randomUUID().toString();
        jdbcTemplate.update(
            """
                INSERT INTO chat_run_headers (id, mode, status, created_at, completed_at)
                VALUES (?, ?, ?, ?, ?)
                """,
            UUID.fromString(runId),
            mode.name(),
            status,
            Timestamp.from(NOW),
            "COMPLETED".equals(status) ? Timestamp.from(NOW.plusSeconds(1)) : null
        );
        return runId;
    }

    private int count(JdbcTemplate jdbcTemplate, String tableName) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
        return count == null ? 0 : count;
    }

    private String stringValue(JdbcTemplate jdbcTemplate, String column, String conversationId) {
        return jdbcTemplate.queryForObject(
            "SELECT " + column + " FROM conversation_summary_refresh_jobs WHERE conversation_id = ?::uuid",
            String.class,
            conversationId
        );
    }

    private int intValue(JdbcTemplate jdbcTemplate, String column, String conversationId) {
        Integer value = jdbcTemplate.queryForObject(
            "SELECT " + column + " FROM conversation_summary_refresh_jobs WHERE conversation_id = ?::uuid",
            Integer.class,
            conversationId
        );
        return value == null ? 0 : value;
    }
}
