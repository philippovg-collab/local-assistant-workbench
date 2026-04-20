package com.example.demo.infrastructure.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.model.AnswerMode;
import com.example.demo.model.ChatExecutionResponse;
import com.example.demo.model.ChatMode;
import com.example.demo.model.ChatRunOutputTrace;
import com.example.demo.model.KnowledgeScopeResolved;
import com.example.demo.model.LlmCallTrace;
import com.example.demo.model.PromptPolicySnapshot;
import com.example.demo.model.RetrievalTrace;
import com.example.demo.support.PostgresIntegrationTestSupport;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class PostgresChatRunTraceRepositoryIT extends PostgresIntegrationTestSupport {

    private PostgresChatRunTraceRepository repository;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        TestDatabase database = resetDatabase();
        jdbcTemplate = database.jdbcTemplate();
        repository = new PostgresChatRunTraceRepository(jdbcTemplate, database.transactionManager());
    }

    @Test
    void completeRunWithResultCreatesHeaderResultAndCompletedEventAtomically() {
        String runId = runId();
        Instant createdAt = Instant.parse("2026-04-19T00:00:00Z");
        Instant completedAt = Instant.parse("2026-04-19T00:00:05Z");
        repository.insertHeader(runId, ChatMode.DIRECT, "qwen2.5:7b", AnswerMode.BRIEF, createdAt);

        boolean completed = repository.completeRunWithResult(
            runId,
            "qwen2.5:7b",
            AnswerMode.BRIEF,
            "ready",
            completedAt,
            5000,
            response(runId, "Answer")
        );

        assertTrue(completed);
        assertEquals("COMPLETED", stringValue("SELECT status FROM chat_run_headers WHERE id = ?::uuid", runId));
        assertEquals("Answer", repository.findResult(runId).orElseThrow().answer());
        assertEquals(List.of("COMPLETED"), eventTypes(runId));
    }

    @Test
    void completeRunWithResultDoesNotWriteResultWhenTerminalTransitionIsRejected() {
        String runId = runId();
        Instant createdAt = Instant.parse("2026-04-19T00:00:00Z");
        repository.insertHeader(runId, ChatMode.DIRECT, "qwen2.5:7b", AnswerMode.BRIEF, createdAt);
        repository.cancelRun(runId, createdAt.plusSeconds(1), 1000);

        boolean completed = repository.completeRunWithResult(
            runId,
            "qwen2.5:7b",
            AnswerMode.BRIEF,
            "ready",
            createdAt.plusSeconds(5),
            5000,
            response(runId, "Late answer")
        );

        assertFalse(completed);
        assertEquals(0, intValue("SELECT COUNT(*) FROM chat_run_results WHERE run_id = ?::uuid", runId));
        assertTrue(eventTypes(runId).isEmpty());
    }

    @Test
    void insertResultIfAbsentDoesNotOverwriteExistingPayload() {
        String runId = runId();
        Instant createdAt = Instant.parse("2026-04-19T00:00:00Z");
        repository.insertHeader(runId, ChatMode.DIRECT, "qwen2.5:7b", AnswerMode.BRIEF, createdAt);

        assertTrue(repository.insertResultIfAbsent(runId, response(runId, "First"), createdAt.plusSeconds(1), "TRACE_BACKFILL"));
        assertFalse(repository.insertResultIfAbsent(runId, response(runId, "Second"), createdAt.plusSeconds(2), "TRACE_BACKFILL"));

        assertEquals("First", repository.findResult(runId).orElseThrow().answer());
    }

    @Test
    void nonTerminalTraceWritesDoNotMutateTablesAfterTerminalStatus() {
        String runId = runId();
        Instant createdAt = Instant.parse("2026-04-19T00:00:00Z");
        repository.insertHeader(runId, ChatMode.DIRECT, "qwen2.5:7b", AnswerMode.BRIEF, createdAt);
        repository.cancelRun(runId, createdAt.plusSeconds(1), 1000);

        repository.saveOutput(runId, new ChatRunOutputTrace("raw", "Late answer", List.of(), null, false, false));
        repository.savePromptSnapshot(runId, new PromptPolicySnapshot(
            null,
            null,
            null,
            null,
            "Late prompt",
            null,
            null,
            null,
            "resolved",
            List.of(),
            "hash",
            List.of(),
            KnowledgeScopeResolved.empty(),
            false
        ));
        repository.saveRetrievalSummary(runId, "DONE", new RetrievalTrace(0, 0, 0, 0, 0, 0, 0, 0, 0), null);
        repository.insertLlmCall(runId, new LlmCallTrace(
            runId(),
            "ollama",
            "qwen2.5:7b",
            List.of(),
            "raw",
            "parsed",
            1,
            2,
            3,
            10L,
            0,
            null,
            "stop",
            null,
            null,
            createdAt.plusSeconds(2)
        ));

        assertEquals("CANCELLED", stringValue("SELECT status FROM chat_run_headers WHERE id = ?::uuid", runId));
        assertEquals(0, intValue("SELECT COUNT(*) FROM chat_run_outputs WHERE run_id = ?::uuid", runId));
        assertEquals(0, intValue("SELECT COUNT(*) FROM chat_run_prompt_snapshots WHERE run_id = ?::uuid", runId));
        assertEquals(0, intValue("SELECT COUNT(*) FROM chat_run_retrieval_summaries WHERE run_id = ?::uuid", runId));
        assertEquals(0, intValue("SELECT COUNT(*) FROM chat_run_llm_calls WHERE run_id = ?::uuid", runId));
        assertTrue(eventTypes(runId).isEmpty());
    }

    private ChatExecutionResponse response(String runId, String answer) {
        return new ChatExecutionResponse(
            ChatMode.DIRECT,
            "qwen2.5:7b",
            "Prompt",
            answer,
            "ready",
            "2026-04-19T00:00:05Z",
            1,
            2,
            3,
            AnswerMode.BRIEF,
            List.of(),
            List.of(),
            KnowledgeScopeResolved.empty(),
            new RetrievalTrace(0, 0, 0, 0, 0, 0, 0, 0, 0),
            null,
            List.of(),
            runId
        );
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

    private String runId() {
        return UUID.randomUUID().toString();
    }
}
