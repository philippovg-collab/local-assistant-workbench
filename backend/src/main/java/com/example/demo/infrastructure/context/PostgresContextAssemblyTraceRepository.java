package com.example.demo.infrastructure.context;

import com.example.demo.error.ErrorType;
import com.example.demo.error.StorageException;
import com.example.demo.model.ChatMode;
import com.example.demo.model.ContextAssemblyDroppedItem;
import com.example.demo.model.ContextAssemblyDroppedMemoryItem;
import com.example.demo.model.ContextAssemblyHistoryItem;
import com.example.demo.model.ContextAssemblyMemoryItem;
import com.example.demo.model.ContextAssemblySnapshotDetail;
import com.example.demo.model.ContextTokenBudget;
import com.example.demo.model.RetrievalQueryResolution;
import com.example.demo.service.audit.ChatRunLeaseToken;
import com.example.demo.service.context.port.ContextAssemblyTraceRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
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
public class PostgresContextAssemblyTraceRepository implements ContextAssemblyTraceRepository {

    private static final TypeReference<List<ContextAssemblyHistoryItem>> HISTORY_ITEMS =
        new TypeReference<>() {
        };
    private static final TypeReference<List<ContextAssemblyDroppedItem>> DROPPED_ITEMS =
        new TypeReference<>() {
        };
    private static final TypeReference<List<ContextAssemblyMemoryItem>> MEMORY_ITEMS =
        new TypeReference<>() {
        };
    private static final TypeReference<List<ContextAssemblyDroppedMemoryItem>> DROPPED_MEMORY_ITEMS =
        new TypeReference<>() {
        };

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    public PostgresContextAssemblyTraceRepository(
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager,
        ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.objectMapper = objectMapper;
    }

    @Override
    public ContextAssemblySnapshotDetail save(ContextAssemblySnapshotDetail snapshot) {
        return save(snapshot, null);
    }

    @Override
    public ContextAssemblySnapshotDetail save(
        ContextAssemblySnapshotDetail snapshot,
        ChatRunLeaseToken leaseToken
    ) {
        try {
            return transactionTemplate.execute(status -> {
                if (!mutableRunExists(snapshot.runId(), leaseToken)) {
                    return null;
                }
                String snapshotId = jdbcTemplate.queryForObject(
                    """
                        INSERT INTO context_assembly_snapshots (
                            id,
                            run_id,
                            conversation_id,
                            turn_no,
                            assembly_mode,
                            original_prompt,
                            resolved_retrieval_query,
                            selected_history_jsonb,
                            dropped_items_jsonb,
                            selected_memory_jsonb,
                            dropped_memory_jsonb,
                            token_budget_jsonb,
                            final_messages_hash,
                            degraded_mode,
                            retrieval_query_resolution_jsonb,
                            sticky_resolution_jsonb,
                            summary_used,
                            summary_through_turn_no,
                            summary_status,
                            summary_token_estimate,
                            summary_degraded_reason,
                            memory_status,
                            memory_degraded_reason,
                            created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (run_id) DO UPDATE
                        SET turn_no = EXCLUDED.turn_no,
                            assembly_mode = EXCLUDED.assembly_mode,
                            original_prompt = EXCLUDED.original_prompt,
                            resolved_retrieval_query = EXCLUDED.resolved_retrieval_query,
                            selected_history_jsonb = EXCLUDED.selected_history_jsonb,
                            dropped_items_jsonb = EXCLUDED.dropped_items_jsonb,
                            selected_memory_jsonb = EXCLUDED.selected_memory_jsonb,
                            dropped_memory_jsonb = EXCLUDED.dropped_memory_jsonb,
                            token_budget_jsonb = EXCLUDED.token_budget_jsonb,
                            final_messages_hash = EXCLUDED.final_messages_hash,
                            degraded_mode = EXCLUDED.degraded_mode,
                            retrieval_query_resolution_jsonb = EXCLUDED.retrieval_query_resolution_jsonb,
                            sticky_resolution_jsonb = EXCLUDED.sticky_resolution_jsonb,
                            summary_used = EXCLUDED.summary_used,
                            summary_through_turn_no = EXCLUDED.summary_through_turn_no,
                            summary_status = EXCLUDED.summary_status,
                            summary_token_estimate = EXCLUDED.summary_token_estimate,
                            summary_degraded_reason = EXCLUDED.summary_degraded_reason,
                            memory_status = EXCLUDED.memory_status,
                            memory_degraded_reason = EXCLUDED.memory_degraded_reason
                        RETURNING id::text
                        """,
                    String.class,
                    UUID.fromString(snapshot.id()),
                    UUID.fromString(snapshot.runId()),
                    UUID.fromString(snapshot.conversationId()),
                    snapshot.turnNo(),
                    snapshot.assemblyMode().name(),
                    snapshot.originalPrompt(),
                    snapshot.resolvedRetrievalQuery(),
                    writeJson(snapshot.selectedHistory()),
                    writeJson(snapshot.droppedItems()),
                    writeJson(snapshot.selectedMemory()),
                    writeJson(snapshot.droppedMemory()),
                    writeJson(snapshot.tokenBudget()),
                    snapshot.finalMessagesHash(),
                    Boolean.TRUE.equals(snapshot.degradedMode()),
                    writeJson(snapshot.retrievalQueryResolution()),
                    writeJson(snapshot.stickyResolution()),
                    Boolean.TRUE.equals(snapshot.summaryUsed()),
                    snapshot.summaryThroughTurnNo(),
                    snapshot.summaryStatus(),
                    snapshot.summaryTokenEstimate(),
                    snapshot.summaryDegradedReason(),
                    snapshot.memoryStatus(),
                    snapshot.memoryDegradedReason(),
                    Timestamp.from(snapshot.createdAt() == null ? Instant.now() : snapshot.createdAt())
                );
                jdbcTemplate.update(
                    """
                        UPDATE chat_conversation_runs
                        SET context_assembly_id = ?,
                            context_assembly_status = 'AVAILABLE'
                        WHERE conversation_id = ?
                          AND run_id = ?
                        """,
                    UUID.fromString(snapshotId),
                    UUID.fromString(snapshot.conversationId()),
                    UUID.fromString(snapshot.runId())
                );
                return findByRunId(snapshot.runId()).orElseThrow();
            });
        } catch (DataAccessException exception) {
            throw storageFailure("context_assembly.storage_write_failed", "Unable to save context assembly snapshot", exception);
        }
    }

    @Override
    public Optional<ContextAssemblySnapshotDetail> findByRunId(String runId) {
        try {
            return jdbcTemplate.query(
                """
                    SELECT id,
                           run_id,
                           conversation_id,
                           turn_no,
                           assembly_mode,
                           original_prompt,
                           resolved_retrieval_query,
                           selected_history_jsonb,
                           dropped_items_jsonb,
                           selected_memory_jsonb,
                           dropped_memory_jsonb,
                           token_budget_jsonb,
                           final_messages_hash,
                           degraded_mode,
                           retrieval_query_resolution_jsonb,
                           sticky_resolution_jsonb,
                           summary_used,
                           summary_through_turn_no,
                           summary_status,
                           summary_token_estimate,
                           summary_degraded_reason,
                           memory_status,
                           memory_degraded_reason,
                           created_at
                    FROM context_assembly_snapshots
                    WHERE run_id = ?
                    LIMIT 1
                    """,
                rowMapper(),
                UUID.fromString(runId)
            ).stream().findFirst();
        } catch (DataAccessException exception) {
            throw storageFailure("context_assembly.storage_read_failed", "Unable to load context assembly snapshot", exception);
        }
    }

    private RowMapper<ContextAssemblySnapshotDetail> rowMapper() {
        return (resultSet, rowNum) -> new ContextAssemblySnapshotDetail(
            resultSet.getObject("id").toString(),
            resultSet.getObject("run_id").toString(),
            resultSet.getObject("conversation_id").toString(),
            resultSet.getInt("turn_no"),
            ChatMode.fromValue(resultSet.getString("assembly_mode")),
            resultSet.getString("original_prompt"),
            resultSet.getString("resolved_retrieval_query"),
            readJson(resultSet.getString("selected_history_jsonb"), HISTORY_ITEMS),
            readJson(resultSet.getString("dropped_items_jsonb"), DROPPED_ITEMS),
            readJson(resultSet.getString("selected_memory_jsonb"), MEMORY_ITEMS),
            readJson(resultSet.getString("dropped_memory_jsonb"), DROPPED_MEMORY_ITEMS),
            readJson(resultSet.getString("token_budget_jsonb"), ContextTokenBudget.class),
            resultSet.getString("final_messages_hash"),
            resultSet.getBoolean("degraded_mode"),
            readNullableJson(resultSet.getString("retrieval_query_resolution_jsonb"), RetrievalQueryResolution.class),
            readJson(resultSet.getString("sticky_resolution_jsonb"), new TypeReference<Map<String, Object>>() {
            }),
            resultSet.getBoolean("summary_used"),
            toInt(resultSet.getObject("summary_through_turn_no", Long.class)),
            resultSet.getString("summary_status"),
            resultSet.getObject("summary_token_estimate", Integer.class),
            resultSet.getString("summary_degraded_reason"),
            resultSet.getString("memory_status"),
            resultSet.getString("memory_degraded_reason"),
            toInstant(resultSet.getTimestamp("created_at"))
        );
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "context_assembly.json_write_failed",
                "Unable to serialize context assembly payload",
                exception
            );
        }
    }

    private <T> T readJson(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "context_assembly.json_read_failed",
                "Unable to deserialize context assembly payload",
                exception
            );
        }
    }

    private <T> T readNullableJson(String value, Class<T> type) {
        if (value == null || value.isBlank() || "{}".equals(value.trim()) || "null".equals(value.trim())) {
            return null;
        }
        return readJson(value, type);
    }

    private <T> T readJson(String value, TypeReference<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "context_assembly.json_read_failed",
                "Unable to deserialize context assembly payload",
                exception
            );
        }
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? Instant.now() : timestamp.toInstant();
    }

    private static Integer toInt(Long value) {
        if (value == null) {
            return null;
        }
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : value.intValue();
    }

    private static StorageException storageFailure(String code, String message, DataAccessException exception) {
        return new StorageException(ErrorType.STORAGE_FAILURE, code, message, exception);
    }

    private boolean mutableRunExists(String runId, ChatRunLeaseToken leaseToken) {
        List<UUID> rows = jdbcTemplate.query(
            mutableRunSql(leaseToken),
            (resultSet, rowNum) -> resultSet.getObject("id", UUID.class),
            mutableRunArgs(runId, leaseToken)
        );
        return !rows.isEmpty();
    }

    private String mutableRunSql(ChatRunLeaseToken leaseToken) {
        if (leaseToken == null) {
            return """
                SELECT id
                FROM chat_run_headers
                WHERE id = ?
                  AND status <> 'FAILED'
                  AND status <> 'COMPLETED'
                  AND status <> 'CANCELLED'
                FOR UPDATE
                """;
        }
        return """
            SELECT h.id
            FROM chat_run_headers h
            WHERE h.id = ?
              AND h.status <> 'FAILED'
              AND h.status <> 'COMPLETED'
              AND h.status <> 'CANCELLED'
              AND EXISTS (
                  SELECT 1
                  FROM chat_run_queue q
                  WHERE q.run_id = h.id
                    AND q.delivery_state = 'IN_PROGRESS'
                    AND q.lease_owner = ?
                    AND q.attempt_count = ?
              )
            FOR UPDATE
            """;
    }

    private Object[] mutableRunArgs(String runId, ChatRunLeaseToken leaseToken) {
        if (leaseToken == null) {
            return new Object[] {UUID.fromString(runId)};
        }
        return new Object[] {
            UUID.fromString(runId),
            leaseToken.leaseOwner(),
            leaseToken.attemptCount()
        };
    }
}
