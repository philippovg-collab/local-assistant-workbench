package com.example.demo.infrastructure.context;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.demo.service.context.ContextRetentionCleanupResult;
import com.example.demo.support.PostgresIntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class PostgresContextMaintenanceRepositoryIT extends PostgresIntegrationTestSupport {

    @Test
    void cleanupDeletesOldSnapshotsKeepsLatestAndPurgesOnlyEmptyStaleConversations() {
        TestDatabase database = resetDatabase();
        JdbcTemplate jdbcTemplate = database.jdbcTemplate();
        PostgresContextMaintenanceRepository repository = new PostgresContextMaintenanceRepository(
            jdbcTemplate,
            database.transactionManager()
        );
        Instant now = Instant.parse("2026-05-10T00:00:00Z");
        String conversationId = insertConversation(jdbcTemplate, now.minus(40, ChronoUnit.DAYS));
        for (int turnNo = 1; turnNo <= 7; turnNo++) {
            insertRunWithSnapshot(
                jdbcTemplate,
                conversationId,
                turnNo,
                now.minus(40, ChronoUnit.DAYS).plus(turnNo, ChronoUnit.HOURS)
            );
        }
        String softDeletedConversationId = insertConversation(jdbcTemplate, now.minus(40, ChronoUnit.DAYS), "SOFT_DELETED");
        insertRunWithSnapshot(jdbcTemplate, softDeletedConversationId, 1, now.minus(40, ChronoUnit.DAYS));
        String emptyConversationId = insertConversation(jdbcTemplate, now.minus(10, ChronoUnit.DAYS));

        ContextRetentionCleanupResult result = repository.deleteExpiredContextArtifacts(
            now.minus(30, ChronoUnit.DAYS),
            now.minus(14, ChronoUnit.DAYS),
            now.minus(30, ChronoUnit.DAYS),
            now.minus(7, ChronoUnit.DAYS),
            2,
            500
        );

        assertEquals(5, result.deletedSnapshots());
        assertEquals(1, result.purgedSoftDeletedConversations());
        assertEquals(1, result.purgedEmptyConversations());
        assertEquals(2, count(jdbcTemplate, "context_assembly_snapshots"));
        assertEquals(5, countRunsByContextStatus(jdbcTemplate, conversationId, "EXPIRED"));
        assertEquals(2, countRunsByContextStatus(jdbcTemplate, conversationId, "AVAILABLE"));
        assertEquals(1, countById(jdbcTemplate, "chat_conversations", conversationId));
        assertEquals(0, countById(jdbcTemplate, "chat_conversations", softDeletedConversationId));
        assertEquals(0, countRuns(jdbcTemplate, softDeletedConversationId));
        assertEquals(0, countSnapshots(jdbcTemplate, softDeletedConversationId));
        assertEquals(0, countById(jdbcTemplate, "chat_conversations", emptyConversationId));
    }

    @Test
    void healthQueriesWorkAgainstMigratedSchema() {
        TestDatabase database = resetDatabase();
        PostgresContextMaintenanceRepository repository = new PostgresContextMaintenanceRepository(
            database.jdbcTemplate(),
            database.transactionManager()
        );

        repository.assertConversationStoreReadable();
        repository.assertContextSnapshotStoreReadable();
        repository.assertStickyStateStoreReadable();
        repository.assertRetrievalResolutionStoreReadable();

        assertEquals(0, repository.summaryJobHealth(Instant.parse("2026-05-10T00:00:00Z")).failedCount());
    }

    private String insertConversation(JdbcTemplate jdbcTemplate, Instant timestamp) {
        return insertConversation(jdbcTemplate, timestamp, "ACTIVE");
    }

    private String insertConversation(JdbcTemplate jdbcTemplate, Instant timestamp, String status) {
        String conversationId = UUID.randomUUID().toString();
        jdbcTemplate.update(
            """
                INSERT INTO chat_conversations (
                    id,
                    title,
                    mode,
                    status,
                    created_at,
                    updated_at
                ) VALUES (?, ?, 'DIRECT', ?, ?, ?)
                """,
            UUID.fromString(conversationId),
            "Context cleanup",
            status,
            Timestamp.from(timestamp),
            Timestamp.from(timestamp)
        );
        return conversationId;
    }

    private void insertRunWithSnapshot(
        JdbcTemplate jdbcTemplate,
        String conversationId,
        int turnNo,
        Instant timestamp
    ) {
        String runId = UUID.randomUUID().toString();
        String snapshotId = UUID.randomUUID().toString();
        jdbcTemplate.update(
            """
                INSERT INTO chat_run_headers (
                    id,
                    mode,
                    status,
                    context_status,
                    created_at,
                    completed_at,
                    schema_version
                ) VALUES (?, 'DIRECT', 'COMPLETED', 'ready', ?, ?, 'test')
                """,
            UUID.fromString(runId),
            Timestamp.from(timestamp),
            Timestamp.from(timestamp.plusSeconds(1))
        );
        jdbcTemplate.update(
            """
                INSERT INTO chat_conversation_runs (
                    conversation_id,
                    run_id,
                    turn_no,
                    request_hash,
                    user_prompt,
                    created_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
            UUID.fromString(conversationId),
            UUID.fromString(runId),
            turnNo,
            "hash-" + runId,
            "prompt",
            Timestamp.from(timestamp)
        );
        jdbcTemplate.update(
            """
                INSERT INTO context_assembly_snapshots (
                    id,
                    run_id,
                    conversation_id,
                    turn_no,
                    assembly_mode,
                    original_prompt,
                    selected_history_jsonb,
                    dropped_items_jsonb,
                    token_budget_jsonb,
                    created_at
                ) VALUES (?, ?, ?, ?, 'DIRECT', 'prompt', '[]'::jsonb, '[]'::jsonb, '{}'::jsonb, ?)
                """,
            UUID.fromString(snapshotId),
            UUID.fromString(runId),
            UUID.fromString(conversationId),
            turnNo,
            Timestamp.from(timestamp)
        );
        jdbcTemplate.update(
            """
                UPDATE chat_conversation_runs
                SET context_assembly_id = ?,
                    context_assembly_status = 'AVAILABLE'
                WHERE run_id = ?
                """,
            UUID.fromString(snapshotId),
            UUID.fromString(runId)
        );
    }

    private int count(JdbcTemplate jdbcTemplate, String tableName) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
        return count == null ? 0 : count;
    }

    private int countById(JdbcTemplate jdbcTemplate, String tableName, String id) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + tableName + " WHERE id = ?",
            Integer.class,
            UUID.fromString(id)
        );
        return count == null ? 0 : count;
    }

    private int countRunsByContextStatus(JdbcTemplate jdbcTemplate, String conversationId, String status) {
        Integer count = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM chat_conversation_runs
                WHERE conversation_id = ?::uuid
                  AND context_assembly_status = ?
                """,
            Integer.class,
            conversationId,
            status
        );
        return count == null ? 0 : count;
    }

    private int countRuns(JdbcTemplate jdbcTemplate, String conversationId) {
        Integer count = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM chat_conversation_runs
                WHERE conversation_id = ?::uuid
                """,
            Integer.class,
            conversationId
        );
        return count == null ? 0 : count;
    }

    private int countSnapshots(JdbcTemplate jdbcTemplate, String conversationId) {
        Integer count = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM context_assembly_snapshots
                WHERE conversation_id = ?::uuid
                """,
            Integer.class,
            conversationId
        );
        return count == null ? 0 : count;
    }
}
