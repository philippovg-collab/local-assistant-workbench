package com.example.demo.infrastructure.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.model.AnswerMode;
import com.example.demo.model.ChatMode;
import com.example.demo.model.KnowledgeScope;
import com.example.demo.model.RetrievalFilters;
import com.example.demo.service.context.StoredConversationStickyState;
import com.example.demo.support.PostgresIntegrationTestSupport;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class PostgresConversationStickyStateRepositoryIT extends PostgresIntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-05-10T00:00:00Z");

    @Test
    void upsertRoundtripsStickyStateAndCascadesWithConversation() {
        TestDatabase database = resetDatabase();
        JdbcTemplate jdbcTemplate = database.jdbcTemplate();
        String conversationId = insertConversation(jdbcTemplate);
        String runId = insertRunHeader(jdbcTemplate, ChatMode.RAG, "COMPLETED");
        PostgresConversationStickyStateRepository repository = repository(database);

        StoredConversationStickyState saved = repository.upsertCompletedTurn(
            conversationId,
            "qwen2.5:7b",
            AnswerMode.STRICT_SOURCES_ONLY,
            "grid",
            new KnowledgeScope(List.of(), List.of(), List.of(), "grid", false),
            RetrievalFilters.empty(),
            List.of(),
            null,
            runId,
            2,
            NOW.plusSeconds(10)
        ).orElseThrow();

        StoredConversationStickyState found = repository.findByConversationId(conversationId).orElseThrow();

        assertEquals(saved.conversationId(), found.conversationId());
        assertEquals("qwen2.5:7b", found.model());
        assertEquals(AnswerMode.STRICT_SOURCES_ONLY, found.answerMode());
        assertEquals("grid", found.instructionWorkspaceKey());
        assertEquals("grid", found.knowledgeScope().workspaceKey());
        assertTrue(found.retrievalFilters().isEmpty());
        assertEquals(List.of(), found.instructionIds());
        assertNull(found.scenarioInstructionIds());
        assertEquals(runId, found.updatedFromRunId());
        assertEquals(2, found.updatedThroughTurnNo());
        assertEquals(1, found.version());

        jdbcTemplate.update("DELETE FROM chat_conversations WHERE id = ?::uuid", conversationId);

        assertEquals(0, count(jdbcTemplate, "conversation_working_memory"));
        assertFalse(repository.findByConversationId(conversationId).isPresent());
    }

    @Test
    void lowerTurnCompletionCannotOverwriteNewerStickyState() {
        TestDatabase database = resetDatabase();
        JdbcTemplate jdbcTemplate = database.jdbcTemplate();
        String conversationId = insertConversation(jdbcTemplate);
        String olderRunId = insertRunHeader(jdbcTemplate, ChatMode.RAG, "COMPLETED");
        String newerRunId = insertRunHeader(jdbcTemplate, ChatMode.RAG, "COMPLETED");
        PostgresConversationStickyStateRepository repository = repository(database);

        repository.upsertCompletedTurn(
            conversationId,
            "newer-model",
            AnswerMode.BRIEF,
            null,
            null,
            null,
            List.of("newer-instruction"),
            List.of(),
            newerRunId,
            4,
            NOW.plusSeconds(40)
        ).orElseThrow();

        assertFalse(repository.upsertCompletedTurn(
            conversationId,
            "older-model",
            AnswerMode.STRICT_SOURCES_ONLY,
            null,
            null,
            null,
            List.of("older-instruction"),
            List.of(),
            olderRunId,
            3,
            NOW.plusSeconds(50)
        ).isPresent());

        StoredConversationStickyState found = repository.findByConversationId(conversationId).orElseThrow();

        assertEquals("newer-model", found.model());
        assertEquals(AnswerMode.BRIEF, found.answerMode());
        assertEquals(List.of("newer-instruction"), found.instructionIds());
        assertEquals(newerRunId, found.updatedFromRunId());
        assertEquals(4, found.updatedThroughTurnNo());
        assertEquals(1, found.version());
    }

    private PostgresConversationStickyStateRepository repository(TestDatabase database) {
        return new PostgresConversationStickyStateRepository(
            database.jdbcTemplate(),
            JsonMapper.builder().findAndAddModules().build()
        );
    }

    private String insertConversation(JdbcTemplate jdbcTemplate) {
        String conversationId = UUID.randomUUID().toString();
        jdbcTemplate.update(
            """
                INSERT INTO chat_conversations (id, title, mode, status, created_at, updated_at)
                VALUES (?, 'Sticky repository', 'RAG', 'ACTIVE', ?, ?)
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
}
