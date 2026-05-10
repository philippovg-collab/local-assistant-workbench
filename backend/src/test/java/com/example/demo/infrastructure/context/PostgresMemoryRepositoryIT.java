package com.example.demo.infrastructure.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.model.MemoryEntryResponse;
import com.example.demo.model.MemoryEntryAction;
import com.example.demo.model.MemoryEntryStatus;
import com.example.demo.model.MemoryEntryType;
import com.example.demo.service.memory.MemoryEntryDraft;
import com.example.demo.service.memory.MemoryExtractionLease;
import com.example.demo.support.PostgresIntegrationTestSupport;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class PostgresMemoryRepositoryIT extends PostgresIntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-05-10T00:00:00Z");

    @Test
    void reviewTransitionsSelectionAndDeleteRedactsEntryAndContextSnapshots() {
        TestDatabase database = resetDatabase();
        JdbcTemplate jdbcTemplate = database.jdbcTemplate();
        String conversationId = insertConversation(jdbcTemplate);
        String runId = insertConversationRun(jdbcTemplate, conversationId, 1);
        PostgresMemoryRepository repository = repository(database);

        MemoryEntryResponse created = repository.createEntry(
            draft("short-answer", "Пользователь предпочитает короткие ответы."),
            "operator",
            "manual",
            NOW
        );
        MemoryEntryResponse approved = repository.changeStatus(
            created.id(),
            MemoryEntryStatus.PENDING_REVIEW,
            MemoryEntryStatus.APPROVED,
            MemoryEntryAction.APPROVE,
            "operator",
            "looks-good",
            NOW.plusSeconds(1)
        );
        MemoryEntryResponse pinned = repository.setPinned(
            approved.id(),
            true,
            "operator",
            "important",
            NOW.plusSeconds(2)
        );
        insertSnapshotWithMemory(jdbcTemplate, conversationId, runId, pinned);

        List<MemoryEntryResponse> selected = repository.selectApprovedForContext("workspace-a", "project-a", 10);
        MemoryEntryResponse deleted = repository.softDelete(
            pinned.id(),
            "operator",
            "user-requested",
            NOW.plusSeconds(3)
        );
        String selectedMemoryJson = jdbcTemplate.queryForObject(
            "SELECT selected_memory_jsonb::text FROM context_assembly_snapshots WHERE run_id = ?::uuid",
            String.class,
            runId
        );

        assertEquals(List.of(pinned.id()), selected.stream().map(MemoryEntryResponse::id).toList());
        assertEquals(MemoryEntryStatus.DELETED, deleted.status());
        assertEquals(null, deleted.contentText());
        assertEquals(null, deleted.sourceTextPreview());
        assertFalse(Boolean.TRUE.equals(deleted.pinned()));
        assertTrue(repository.selectApprovedForContext("workspace-a", "project-a", 10).isEmpty());
        assertFalse(selectedMemoryJson.contains("Пользователь предпочитает короткие ответы."));
        assertTrue(selectedMemoryJson.contains(pinned.id()));
        assertEquals(4, count(jdbcTemplate, "memory_review_actions"));
    }

    @Test
    void duplicateCandidateDedupeAndExtractionRetryLeaseRoundtrip() {
        TestDatabase database = resetDatabase();
        JdbcTemplate jdbcTemplate = database.jdbcTemplate();
        String conversationId = insertConversation(jdbcTemplate);
        String runId = insertConversationRun(jdbcTemplate, conversationId, 1);
        PostgresMemoryRepository repository = repository(database);

        MemoryEntryResponse first = repository.createCandidate(
            draft("duplicate", "Пользователь предпочитает русский язык."),
            NOW
        );
        MemoryEntryResponse second = repository.createCandidate(
            draft("duplicate", "Пользователь предпочитает русский язык."),
            NOW.plusSeconds(1)
        );

        assertEquals(first.id(), second.id());
        assertEquals(1, count(jdbcTemplate, "memory_entries"));

        repository.enqueueExtractionJob(conversationId, runId, 1, NOW);
        MemoryExtractionLease lease = repository.claimNextExtractionJob(
            "worker-a",
            NOW.plusSeconds(1),
            Duration.ofSeconds(60)
        ).orElseThrow();

        assertTrue(repository.markExtractionRetry(
            lease,
            "TestFailure",
            "retry later",
            NOW.plusSeconds(2),
            NOW.plusSeconds(30)
        ));
        assertTrue(repository.claimNextExtractionJob(
            "worker-a",
            NOW.plusSeconds(10),
            Duration.ofSeconds(60)
        ).isEmpty());

        MemoryExtractionLease retryLease = repository.claimNextExtractionJob(
            "worker-a",
            NOW.plusSeconds(31),
            Duration.ofSeconds(60)
        ).orElseThrow();
        assertTrue(repository.markExtractionDone(retryLease, NOW.plusSeconds(32)));
        assertEquals("DONE", jdbcTemplate.queryForObject(
            "SELECT status FROM memory_extraction_jobs WHERE id = ?::uuid",
            String.class,
            retryLease.job().id()
        ));
    }

    private PostgresMemoryRepository repository(TestDatabase database) {
        return new PostgresMemoryRepository(
            database.jdbcTemplate(),
            database.transactionManager(),
            JsonMapper.builder().findAndAddModules().build()
        );
    }

    private MemoryEntryDraft draft(String key, String content) {
        return new MemoryEntryDraft(
            MemoryEntryType.USER_PREFERENCE,
            content,
            key,
            "workspace-a",
            "project-a",
            false,
            BigDecimal.valueOf(0.9),
            Map.of("test", true),
            null,
            null,
            null,
            null,
            null
        );
    }

    private String insertConversation(JdbcTemplate jdbcTemplate) {
        String conversationId = UUID.randomUUID().toString();
        jdbcTemplate.update(
            """
                INSERT INTO chat_conversations (id, workspace_key, title, mode, status, created_at, updated_at)
                VALUES (?, 'workspace-a', 'Memory review', 'RAG', 'ACTIVE', ?, ?)
                """,
            UUID.fromString(conversationId),
            Timestamp.from(NOW),
            Timestamp.from(NOW)
        );
        return conversationId;
    }

    private String insertConversationRun(JdbcTemplate jdbcTemplate, String conversationId, int turnNo) {
        String runId = UUID.randomUUID().toString();
        jdbcTemplate.update(
            """
                INSERT INTO chat_run_headers (id, mode, status, context_status, created_at, completed_at, schema_version)
                VALUES (?, 'RAG', 'COMPLETED', 'ready', ?, ?, 'test')
                """,
            UUID.fromString(runId),
            Timestamp.from(NOW.plusSeconds(turnNo)),
            Timestamp.from(NOW.plusSeconds(turnNo + 1L))
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
            "hash-" + turnNo,
            "Запомни: предпочитаю короткие ответы",
            Timestamp.from(NOW.plusSeconds(turnNo))
        );
        return runId;
    }

    private void insertSnapshotWithMemory(
        JdbcTemplate jdbcTemplate,
        String conversationId,
        String runId,
        MemoryEntryResponse memory
    ) {
        String snapshotId = UUID.randomUUID().toString();
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
                    selected_memory_jsonb,
                    dropped_memory_jsonb,
                    token_budget_jsonb,
                    created_at
                ) VALUES (?, ?, ?, 1, 'RAG', 'prompt', '[]'::jsonb, '[]'::jsonb, ?::jsonb, '[]'::jsonb, '{}'::jsonb, ?)
                """,
            UUID.fromString(snapshotId),
            UUID.fromString(runId),
            UUID.fromString(conversationId),
            """
                [{
                  "id": "%s",
                  "entryType": "user_preference",
                  "contentText": "%s",
                  "workspaceKey": "workspace-a",
                  "projectKey": "project-a",
                  "pinned": true,
                  "estimatedTokens": 8
                }]
                """.formatted(memory.id(), memory.contentText()),
            Timestamp.from(NOW.plusSeconds(2))
        );
    }

    private int count(JdbcTemplate jdbcTemplate, String tableName) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
        return count == null ? 0 : count;
    }
}
