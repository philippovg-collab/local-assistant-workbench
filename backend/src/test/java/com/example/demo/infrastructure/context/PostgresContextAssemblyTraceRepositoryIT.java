package com.example.demo.infrastructure.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.model.ChatMode;
import com.example.demo.model.ContextAssemblyDroppedItem;
import com.example.demo.model.ContextAssemblyDroppedMemoryItem;
import com.example.demo.model.ContextAssemblyHistoryItem;
import com.example.demo.model.ContextAssemblyMemoryItem;
import com.example.demo.model.ContextAssemblySnapshotDetail;
import com.example.demo.model.ContextTokenBudget;
import com.example.demo.model.MemoryEntryType;
import com.example.demo.model.RetrievalQueryResolution;
import com.example.demo.model.RetrievalQueryResolutionDecision;
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

class PostgresContextAssemblyTraceRepositoryIT extends PostgresIntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-05-10T00:00:00Z");

    @Test
    void saveFindRoundtripPersistsJsonPayloadAndMarksRunAvailable() {
        TestDatabase database = resetDatabase();
        JdbcTemplate jdbcTemplate = database.jdbcTemplate();
        String conversationId = insertConversation(jdbcTemplate);
        String runId = insertConversationRun(jdbcTemplate, conversationId, 2, "IN_PROGRESS");
        String priorRunId = insertConversationRun(jdbcTemplate, conversationId, 1, "COMPLETED");
        PostgresContextAssemblyTraceRepository repository = repository(database);

        ContextAssemblySnapshotDetail saved = repository.save(snapshot(runId, conversationId, priorRunId));
        ContextAssemblySnapshotDetail found = repository.findByRunId(runId).orElseThrow();

        assertNotNull(saved);
        assertEquals(saved.id(), found.id());
        assertEquals(runId, found.runId());
        assertEquals(conversationId, found.conversationId());
        assertEquals(ChatMode.RAG, found.assemblyMode());
        assertEquals("current prompt", found.originalPrompt());
        assertEquals("resolved query", found.resolvedRetrievalQuery());
        assertEquals(2, found.selectedHistory().size());
        assertEquals("prior answer", found.selectedHistory().get(1).content());
        assertEquals("token_budget", found.droppedItems().getFirst().reason());
        assertEquals(1, found.selectedMemory().size());
        assertEquals(MemoryEntryType.USER_PREFERENCE, found.selectedMemory().getFirst().entryType());
        assertEquals(1, found.droppedMemory().size());
        assertEquals(250, found.tokenBudget().selectedHistoryTokens());
        assertEquals("hash-1", found.finalMessagesHash());
        assertTrue(found.degradedMode());
        assertEquals(RetrievalQueryResolutionDecision.FALLBACK_ORIGINAL, found.retrievalQueryResolution().decision());
        assertEquals("sticky", ((Map<?, ?>) found.stickyResolution().get("fieldSources")).get("model"));
        assertTrue(found.summaryUsed());
        assertEquals("READY", found.summaryStatus());
        assertEquals("ready", found.memoryStatus());
        assertEquals(saved.id(), stringValue(
            jdbcTemplate,
            "SELECT context_assembly_id::text FROM chat_conversation_runs WHERE run_id = ?::uuid",
            runId
        ));
        assertEquals("AVAILABLE", stringValue(
            jdbcTemplate,
            "SELECT context_assembly_status FROM chat_conversation_runs WHERE run_id = ?::uuid",
            runId
        ));
    }

    @Test
    void deletingConversationRunCascadesContextSnapshot() {
        TestDatabase database = resetDatabase();
        JdbcTemplate jdbcTemplate = database.jdbcTemplate();
        String conversationId = insertConversation(jdbcTemplate);
        String runId = insertConversationRun(jdbcTemplate, conversationId, 1, "IN_PROGRESS");
        PostgresContextAssemblyTraceRepository repository = repository(database);
        repository.save(snapshot(runId, conversationId, runId));

        assertEquals(1, count(jdbcTemplate, "context_assembly_snapshots"));

        jdbcTemplate.update(
            "DELETE FROM chat_conversation_runs WHERE conversation_id = ?::uuid AND run_id = ?::uuid",
            conversationId,
            runId
        );

        assertEquals(0, count(jdbcTemplate, "context_assembly_snapshots"));
        assertFalse(repository.findByRunId(runId).isPresent());
    }

    private PostgresContextAssemblyTraceRepository repository(TestDatabase database) {
        return new PostgresContextAssemblyTraceRepository(
            database.jdbcTemplate(),
            database.transactionManager(),
            JsonMapper.builder().findAndAddModules().build()
        );
    }

    private ContextAssemblySnapshotDetail snapshot(String runId, String conversationId, String priorRunId) {
        return new ContextAssemblySnapshotDetail(
            UUID.randomUUID().toString(),
            runId,
            conversationId,
            2,
            ChatMode.RAG,
            "current prompt",
            "resolved query",
            List.of(
                new ContextAssemblyHistoryItem(priorRunId, 1, "user", "prior prompt", 120, NOW.minusSeconds(60)),
                new ContextAssemblyHistoryItem(priorRunId, 1, "assistant", "prior answer", 130, NOW.minusSeconds(30))
            ),
            List.of(new ContextAssemblyDroppedItem(priorRunId, 1, "token_budget", 50)),
            List.of(new ContextAssemblyMemoryItem(
                "memory-1",
                MemoryEntryType.USER_PREFERENCE,
                "Use short answers.",
                "workspace-a",
                null,
                true,
                BigDecimal.valueOf(0.9d),
                8,
                NOW
            )),
            List.of(new ContextAssemblyDroppedMemoryItem(
                "memory-2",
                MemoryEntryType.PROJECT_NOTE,
                "workspace-a",
                "project-a",
                false,
                "token_budget",
                12
            )),
            new ContextTokenBudget(6, 1000, 1, 250, 1, 50),
            "hash-1",
            true,
            new RetrievalQueryResolution(
                "current prompt",
                "resolved query",
                "resolved query",
                RetrievalQueryResolutionDecision.FALLBACK_ORIGINAL,
                0.8d,
                List.of("fallback"),
                List.of(priorRunId),
                List.of(),
                false,
                null
            ),
            Map.of("active", true, "fieldSources", Map.of("model", "sticky")),
            true,
            1,
            "READY",
            24,
            null,
            "ready",
            null,
            NOW
        );
    }

    private String insertConversation(JdbcTemplate jdbcTemplate) {
        String conversationId = UUID.randomUUID().toString();
        jdbcTemplate.update(
            """
                INSERT INTO chat_conversations (id, title, mode, status, created_at, updated_at)
                VALUES (?, 'Trace repository', 'RAG', 'ACTIVE', ?, ?)
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
                VALUES (?, 'RAG', ?, ?, ?)
                """,
            UUID.fromString(runId),
            status,
            Timestamp.from(NOW.plusSeconds(turnNo)),
            "COMPLETED".equals(status) ? Timestamp.from(NOW.plusSeconds(turnNo + 1L)) : null
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
            "prompt-" + turnNo,
            Timestamp.from(NOW.plusSeconds(turnNo))
        );
        return runId;
    }

    private int count(JdbcTemplate jdbcTemplate, String tableName) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Integer.class);
        return count == null ? 0 : count;
    }

    private String stringValue(JdbcTemplate jdbcTemplate, String sql, String id) {
        return jdbcTemplate.queryForObject(sql, String.class, id);
    }
}
