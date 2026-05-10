package com.example.demo.infrastructure.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.error.StorageException;
import com.example.demo.model.ChatMode;
import com.example.demo.model.ContextAssemblySnapshotDetail;
import com.example.demo.model.MemoryEntryStatus;
import com.example.demo.model.MemoryEntryType;
import com.example.demo.service.audit.ChatRunLeaseToken;
import com.example.demo.service.context.ConversationSummaryPayload;
import com.example.demo.service.context.ConversationSummaryRefreshJob;
import com.example.demo.service.memory.MemoryEntryDraft;
import com.example.demo.service.memory.MemoryExtractionJob;
import com.example.demo.service.memory.MemoryExtractionLease;
import com.example.demo.support.PostgresIntegrationTestSupport;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class PostgresContextHardeningRepositoryIT extends PostgresIntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-05-10T00:00:00Z");

    @Test
    void contextSnapshotSaveRequiresCurrentChatRunLeaseOwner() {
        TestDatabase database = resetDatabase();
        JdbcTemplate jdbcTemplate = database.jdbcTemplate();
        String conversationId = insertConversation(jdbcTemplate);
        String runId = insertConversationRun(jdbcTemplate, conversationId, 1, "IN_PROGRESS");
        insertChatLease(jdbcTemplate, runId, "worker-b", 2);
        PostgresContextAssemblyTraceRepository repository = new PostgresContextAssemblyTraceRepository(
            jdbcTemplate,
            database.transactionManager(),
            JsonMapper.builder().findAndAddModules().build()
        );

        ContextAssemblySnapshotDetail staleSave = repository.save(
            snapshot(runId, conversationId),
            new ChatRunLeaseToken(runId, "worker-a", 1)
        );
        ContextAssemblySnapshotDetail currentSave = repository.save(
            snapshot(runId, conversationId),
            new ChatRunLeaseToken(runId, "worker-b", 2)
        );

        assertEquals(null, staleSave);
        assertNotNull(currentSave);
        assertEquals(1, count(jdbcTemplate, "context_assembly_snapshots"));
        assertEquals("AVAILABLE", stringValue(
            jdbcTemplate,
            "SELECT context_assembly_status FROM chat_conversation_runs WHERE run_id = ?::uuid",
            runId
        ));
    }

    @Test
    void summaryRefreshCompletionRequiresCurrentOwner() {
        TestDatabase database = resetDatabase();
        JdbcTemplate jdbcTemplate = database.jdbcTemplate();
        String conversationId = insertConversation(jdbcTemplate);
        insertSummaryJob(jdbcTemplate, conversationId, "worker-b");
        PostgresConversationSummaryRepository repository = new PostgresConversationSummaryRepository(
            jdbcTemplate,
            database.transactionManager(),
            JsonMapper.builder().findAndAddModules().build()
        );
        ConversationSummaryPayload payload = new ConversationSummaryPayload("summary", List.of(), List.of(), List.of(), List.of(2));

        boolean staleCompleted = repository.completeRefresh(
            summaryJob(conversationId, "worker-a"),
            payload,
            null,
            NOW
        );
        boolean currentCompleted = repository.completeRefresh(
            summaryJob(conversationId, "worker-b"),
            payload,
            null,
            NOW
        );

        assertFalse(staleCompleted);
        assertTrue(currentCompleted);
        assertEquals("READY", stringValue(jdbcTemplate, "SELECT summary_status FROM conversation_working_memory WHERE conversation_id = ?::uuid", conversationId));
    }

    @Test
    void memoryCandidateWritesAndTerminalUpdatesRequireCurrentOwner() {
        TestDatabase database = resetDatabase();
        JdbcTemplate jdbcTemplate = database.jdbcTemplate();
        String conversationId = insertConversation(jdbcTemplate);
        String runId = insertConversationRun(jdbcTemplate, conversationId, 1, "COMPLETED");
        String jobId = insertMemoryJob(jdbcTemplate, conversationId, runId, "worker-b", 2);
        PostgresMemoryRepository repository = new PostgresMemoryRepository(
            jdbcTemplate,
            database.transactionManager(),
            JsonMapper.builder().findAndAddModules().build()
        );

        assertThrows(StorageException.class, () -> repository.createCandidate(
            draft("stale"),
            NOW,
            memoryLease(jobId, conversationId, runId, "worker-a", 1)
        ));
        assertFalse(repository.markExtractionDone(memoryLease(jobId, conversationId, runId, "worker-a", 1), NOW));

        repository.createCandidate(draft("current"), NOW, memoryLease(jobId, conversationId, runId, "worker-b", 2));
        assertTrue(repository.markExtractionDone(memoryLease(jobId, conversationId, runId, "worker-b", 2), NOW));

        assertEquals(1, count(jdbcTemplate, "memory_entries"));
        assertEquals("DONE", stringValue(jdbcTemplate, "SELECT status FROM memory_extraction_jobs WHERE id = ?::uuid", jobId));
    }

    private ContextAssemblySnapshotDetail snapshot(String runId, String conversationId) {
        return new ContextAssemblySnapshotDetail(
            UUID.randomUUID().toString(),
            runId,
            conversationId,
            1,
            ChatMode.DIRECT,
            "prompt",
            null,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            "hash",
            false,
            null,
            Map.of(),
            false,
            null,
            null,
            null,
            null,
            null,
            null,
            NOW
        );
    }

    private String insertConversation(JdbcTemplate jdbcTemplate) {
        String conversationId = UUID.randomUUID().toString();
        jdbcTemplate.update(
            """
                INSERT INTO chat_conversations (id, title, mode, status, created_at, updated_at)
                VALUES (?, 'Hardening', 'DIRECT', 'ACTIVE', ?, ?)
                """,
            UUID.fromString(conversationId),
            Timestamp.from(NOW),
            Timestamp.from(NOW)
        );
        return conversationId;
    }

    private String insertConversationRun(
        JdbcTemplate jdbcTemplate,
        String conversationId,
        int turnNo,
        String status
    ) {
        String runId = UUID.randomUUID().toString();
        jdbcTemplate.update(
            """
                INSERT INTO chat_run_headers (id, mode, status, created_at, completed_at)
                VALUES (?, 'DIRECT', ?, ?, ?)
                """,
            UUID.fromString(runId),
            status,
            Timestamp.from(NOW),
            "COMPLETED".equals(status) ? Timestamp.from(NOW.plusSeconds(1)) : null
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
                ) VALUES (?, ?, ?, 'hash', 'prompt', ?)
                """,
            UUID.fromString(conversationId),
            UUID.fromString(runId),
            turnNo,
            Timestamp.from(NOW)
        );
        return runId;
    }

    private void insertChatLease(JdbcTemplate jdbcTemplate, String runId, String owner, int attemptCount) {
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
                ) VALUES (?, 'IN_PROGRESS', ?, ?, ?, ?, ?, ?, ?)
                """,
            UUID.fromString(runId),
            attemptCount,
            Timestamp.from(NOW),
            owner,
            Timestamp.from(NOW.plusSeconds(300)),
            Timestamp.from(NOW),
            Timestamp.from(NOW),
            Timestamp.from(NOW)
        );
    }

    private void insertSummaryJob(JdbcTemplate jdbcTemplate, String conversationId, String owner) {
        jdbcTemplate.update(
            """
                INSERT INTO conversation_summary_refresh_jobs (
                    conversation_id,
                    requested_through_turn_no,
                    status,
                    attempt_count,
                    lease_owner,
                    lease_expires_at,
                    created_at,
                    updated_at
                ) VALUES (?, 2, 'RUNNING', 0, ?, ?, ?, ?)
                """,
            UUID.fromString(conversationId),
            owner,
            Timestamp.from(NOW.plusSeconds(120)),
            Timestamp.from(NOW),
            Timestamp.from(NOW)
        );
    }

    private ConversationSummaryRefreshJob summaryJob(String conversationId, String owner) {
        return new ConversationSummaryRefreshJob(
            conversationId,
            2,
            "RUNNING",
            0,
            null,
            owner,
            NOW.plusSeconds(120),
            null,
            null,
            NOW,
            NOW
        );
    }

    private String insertMemoryJob(
        JdbcTemplate jdbcTemplate,
        String conversationId,
        String runId,
        String owner,
        int attemptCount
    ) {
        String jobId = UUID.randomUUID().toString();
        jdbcTemplate.update(
            """
                INSERT INTO memory_extraction_jobs (
                    id,
                    conversation_id,
                    run_id,
                    turn_no,
                    status,
                    attempt_count,
                    lease_owner,
                    lease_expires_at,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, 1, 'RUNNING', ?, ?, ?, ?, ?)
                """,
            UUID.fromString(jobId),
            UUID.fromString(conversationId),
            UUID.fromString(runId),
            attemptCount,
            owner,
            Timestamp.from(NOW.plusSeconds(120)),
            Timestamp.from(NOW),
            Timestamp.from(NOW)
        );
        return jobId;
    }

    private MemoryExtractionLease memoryLease(
        String jobId,
        String conversationId,
        String runId,
        String owner,
        int attemptCount
    ) {
        return new MemoryExtractionLease(new MemoryExtractionJob(
            jobId,
            conversationId,
            runId,
            1,
            "RUNNING",
            attemptCount,
            null,
            null,
            null,
            owner,
            NOW.plusSeconds(120),
            NOW,
            NOW,
            null
        ), attemptCount);
    }

    private MemoryEntryDraft draft(String key) {
        return new MemoryEntryDraft(
            MemoryEntryType.USER_PREFERENCE,
            "remember " + key,
            "memory-" + key,
            null,
            null,
            false,
            BigDecimal.valueOf(0.7),
            Map.of(),
            null,
            null,
            null,
            null,
            null
        );
    }

    private int count(JdbcTemplate jdbcTemplate, String tableName) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
        return count == null ? 0 : count;
    }

    private String stringValue(JdbcTemplate jdbcTemplate, String sql, String id) {
        return jdbcTemplate.queryForObject(sql, String.class, id);
    }
}
