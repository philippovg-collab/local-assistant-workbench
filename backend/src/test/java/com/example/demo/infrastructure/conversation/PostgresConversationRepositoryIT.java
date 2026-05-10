package com.example.demo.infrastructure.conversation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.error.StorageException;
import com.example.demo.model.ChatMode;
import com.example.demo.service.conversation.port.ConversationRepository;
import com.example.demo.support.PostgresIntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class PostgresConversationRepositoryIT extends PostgresIntegrationTestSupport {

    @Test
    void persistsRunsWithConstraintsCascadeAndOrderedStatusJoin() {
        TestDatabase database = resetDatabase();
        JdbcTemplate jdbcTemplate = database.jdbcTemplate();
        ConversationRepository repository = new PostgresConversationRepository(jdbcTemplate);
        String conversationId = UUID.randomUUID().toString();
        String run1 = UUID.randomUUID().toString();
        String run2 = UUID.randomUUID().toString();
        Instant now = Instant.parse("2026-05-10T00:00:00Z");

        repository.createConversation(conversationId, "grid", "Dispatch", ChatMode.RAG, "qwen2.5:7b", null, now);
        insertHeader(jdbcTemplate, run1, "COMPLETED", now);
        insertHeader(jdbcTemplate, run2, "RECEIVED", now.plusSeconds(1));
        repository.insertRun(conversationId, run2, 2, null, "turn-2", "hash-2", "Second", null, now.plusSeconds(1));
        repository.insertRun(conversationId, run1, 1, null, "turn-1", "hash-1", "First", null, now);

        var runs = repository.listRuns(conversationId);

        assertEquals(List.of(run1, run2), runs.stream().map(run -> run.runId()).toList());
        assertEquals(List.of(1, 2), runs.stream().map(run -> run.turnNo()).toList());
        assertEquals("COMPLETED", runs.getFirst().status());
        assertEquals("RECEIVED", runs.get(1).status());
        assertEquals("NONE", runs.getFirst().contextAssemblyStatus());
        assertTrue(repository.runBelongsToConversation(conversationId, run1));

        String duplicateTurnRun = UUID.randomUUID().toString();
        insertHeader(jdbcTemplate, duplicateTurnRun, "RECEIVED", now.plusSeconds(2));
        assertThrows(StorageException.class, () ->
            repository.insertRun(conversationId, duplicateTurnRun, 1, null, "turn-3", "hash-3", "Duplicate turn", null, now)
        );

        String duplicateClientRun = UUID.randomUUID().toString();
        insertHeader(jdbcTemplate, duplicateClientRun, "RECEIVED", now.plusSeconds(3));
        assertThrows(StorageException.class, () ->
            repository.insertRun(conversationId, duplicateClientRun, 3, null, "turn-1", "hash-4", "Duplicate client", null, now)
        );

        String otherConversationId = UUID.randomUUID().toString();
        repository.createConversation(otherConversationId, "grid", "Other", ChatMode.RAG, "qwen2.5:7b", null, now);
        assertThrows(StorageException.class, () ->
            repository.insertRun(otherConversationId, run1, 1, null, "other-turn-1", "hash-5", "Duplicate run", null, now)
        );

        jdbcTemplate.update("DELETE FROM chat_conversations WHERE id = ?", UUID.fromString(conversationId));

        assertEquals(0, countConversationRuns(jdbcTemplate, conversationId));
        assertEquals(1, countHeaders(jdbcTemplate, run1));
    }

    @Test
    void allocatesUniqueSequentialTurnsUnderParallelSubmits() throws Exception {
        TestDatabase database = resetDatabase();
        JdbcTemplate jdbcTemplate = database.jdbcTemplate();
        ConversationRepository repository = new PostgresConversationRepository(jdbcTemplate);
        TransactionTemplate transactionTemplate = new TransactionTemplate(database.transactionManager());
        String conversationId = UUID.randomUUID().toString();
        Instant now = Instant.parse("2026-05-10T00:00:00Z");
        repository.createConversation(conversationId, "grid", "Parallel", ChatMode.RAG, "qwen2.5:7b", null, now);
        int workers = 6;
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(workers);
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int index = 0; index < workers; index++) {
            int taskIndex = index;
            tasks.add(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return transactionTemplate.execute(status -> {
                    String runId = UUID.randomUUID().toString();
                    insertHeader(jdbcTemplate, runId, "RECEIVED", now.plusSeconds(taskIndex));
                    int turnNo = repository.nextTurnNo(conversationId);
                    repository.insertRun(
                        conversationId,
                        runId,
                        turnNo,
                        null,
                        "client-" + taskIndex,
                        "hash-" + taskIndex,
                        "Prompt " + taskIndex,
                        null,
                        now.plusSeconds(taskIndex)
                    );
                    return turnNo;
                });
            });
        }

        var futures = tasks.stream().map(executor::submit).toList();
        start.countDown();
        List<Integer> turnNumbers = new ArrayList<>();
        for (var future : futures) {
            turnNumbers.add(future.get(10, TimeUnit.SECONDS));
        }
        executor.shutdownNow();
        Collections.sort(turnNumbers);

        assertEquals(List.of(1, 2, 3, 4, 5, 6), turnNumbers);
        assertEquals(List.of(1, 2, 3, 4, 5, 6), repository.listRuns(conversationId).stream()
            .map(run -> run.turnNo())
            .toList());
    }

    @Test
    void hidesTerminalConversationStatusesFromUserFacingReads() {
        TestDatabase database = resetDatabase();
        JdbcTemplate jdbcTemplate = database.jdbcTemplate();
        ConversationRepository repository = new PostgresConversationRepository(jdbcTemplate);
        String conversationId = UUID.randomUUID().toString();
        Instant now = Instant.parse("2026-05-10T00:00:00Z");
        repository.createConversation(conversationId, "grid", "Deleted", ChatMode.RAG, "qwen2.5:7b", null, now);

        jdbcTemplate.update(
            "UPDATE chat_conversations SET status = 'SOFT_DELETED', updated_at = ? WHERE id = ?",
            Timestamp.from(now.plusSeconds(1)),
            UUID.fromString(conversationId)
        );

        assertTrue(repository.listConversations("grid", ChatMode.RAG, 50).isEmpty());
        assertFalse(repository.findConversation(conversationId).isPresent());
        assertFalse(repository.findConversationForUpdate(conversationId).isPresent());
    }

    private static void insertHeader(JdbcTemplate jdbcTemplate, String runId, String status, Instant createdAt) {
        jdbcTemplate.update(
            """
                INSERT INTO chat_run_headers (
                    id,
                    mode,
                    status,
                    requested_model,
                    resolved_model,
                    context_status,
                    created_at
                ) VALUES (?, 'RAG', ?, 'qwen2.5:7b', 'qwen2.5:7b', 'ready', ?)
                """,
            UUID.fromString(runId),
            status,
            Timestamp.from(createdAt)
        );
    }

    private static int countConversationRuns(JdbcTemplate jdbcTemplate, String conversationId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM chat_conversation_runs WHERE conversation_id = ?",
            Integer.class,
            UUID.fromString(conversationId)
        );
        return count == null ? 0 : count;
    }

    private static int countHeaders(JdbcTemplate jdbcTemplate, String runId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM chat_run_headers WHERE id = ?",
            Integer.class,
            UUID.fromString(runId)
        );
        return count == null ? 0 : count;
    }
}
