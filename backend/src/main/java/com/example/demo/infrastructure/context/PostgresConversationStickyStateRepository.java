package com.example.demo.infrastructure.context;

import com.example.demo.error.ErrorType;
import com.example.demo.error.StorageException;
import com.example.demo.model.AnswerMode;
import com.example.demo.model.KnowledgeScope;
import com.example.demo.model.RetrievalFilters;
import com.example.demo.service.context.StoredConversationStickyState;
import com.example.demo.service.context.port.ConversationStickyStateRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresConversationStickyStateRepository implements ConversationStickyStateRepository {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PostgresConversationStickyStateRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<StoredConversationStickyState> findByConversationId(String conversationId) {
        try {
            return jdbcTemplate.query(
                selectSql() + " WHERE conversation_id = ? LIMIT 1",
                rowMapper(),
                UUID.fromString(conversationId)
            ).stream().findFirst();
        } catch (DataAccessException exception) {
            throw storageFailure("conversation_sticky.storage_read_failed", "Unable to load conversation sticky state", exception);
        }
    }

    @Override
    public Optional<StoredConversationStickyState> upsertCompletedTurn(
        String conversationId,
        String model,
        AnswerMode answerMode,
        String instructionWorkspaceKey,
        KnowledgeScope knowledgeScope,
        RetrievalFilters retrievalFilters,
        List<String> instructionIds,
        List<String> scenarioInstructionIds,
        String updatedFromRunId,
        int updatedThroughTurnNo,
        Instant updatedAt
    ) {
        try {
            Instant effectiveUpdatedAt = updatedAt == null ? Instant.now() : updatedAt;
            return jdbcTemplate.query(
                """
                    INSERT INTO conversation_working_memory (
                        conversation_id,
                        sticky_model,
                        sticky_answer_mode,
                        sticky_instruction_workspace_key,
                        sticky_knowledge_scope_jsonb,
                        sticky_retrieval_filters_jsonb,
                        sticky_instruction_ids_jsonb,
                        sticky_scenario_instruction_ids_jsonb,
                        updated_from_run_id,
                        updated_through_turn_no,
                        version,
                        created_at,
                        updated_at
                    ) VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?, 1, ?, ?)
                    ON CONFLICT (conversation_id) DO UPDATE
                    SET sticky_model = EXCLUDED.sticky_model,
                        sticky_answer_mode = EXCLUDED.sticky_answer_mode,
                        sticky_instruction_workspace_key = EXCLUDED.sticky_instruction_workspace_key,
                        sticky_knowledge_scope_jsonb = EXCLUDED.sticky_knowledge_scope_jsonb,
                        sticky_retrieval_filters_jsonb = EXCLUDED.sticky_retrieval_filters_jsonb,
                        sticky_instruction_ids_jsonb = EXCLUDED.sticky_instruction_ids_jsonb,
                        sticky_scenario_instruction_ids_jsonb = EXCLUDED.sticky_scenario_instruction_ids_jsonb,
                        updated_from_run_id = EXCLUDED.updated_from_run_id,
                        updated_through_turn_no = EXCLUDED.updated_through_turn_no,
                        version = conversation_working_memory.version + 1,
                        updated_at = EXCLUDED.updated_at
                    WHERE conversation_working_memory.updated_through_turn_no IS NULL
                       OR conversation_working_memory.updated_through_turn_no <= EXCLUDED.updated_through_turn_no
                    RETURNING conversation_id,
                              sticky_model,
                              sticky_answer_mode,
                              sticky_instruction_workspace_key,
                              sticky_knowledge_scope_jsonb,
                              sticky_retrieval_filters_jsonb,
                              sticky_instruction_ids_jsonb,
                              sticky_scenario_instruction_ids_jsonb,
                              updated_from_run_id,
                              updated_through_turn_no,
                              version,
                              created_at,
                              updated_at
                    """,
                rowMapper(),
                UUID.fromString(conversationId),
                model,
                answerMode == null ? null : answerMode.value(),
                instructionWorkspaceKey,
                writeNullableJson(knowledgeScope),
                writeNullableJson(retrievalFilters),
                writeNullableJson(instructionIds),
                writeNullableJson(scenarioInstructionIds),
                updatedFromRunId == null ? null : UUID.fromString(updatedFromRunId),
                updatedThroughTurnNo,
                Timestamp.from(effectiveUpdatedAt),
                Timestamp.from(effectiveUpdatedAt)
            ).stream().findFirst();
        } catch (DataAccessException exception) {
            throw storageFailure("conversation_sticky.storage_write_failed", "Unable to update conversation sticky state", exception);
        }
    }

    private String selectSql() {
        return """
            SELECT conversation_id,
                   sticky_model,
                   sticky_answer_mode,
                   sticky_instruction_workspace_key,
                   sticky_knowledge_scope_jsonb,
                   sticky_retrieval_filters_jsonb,
                   sticky_instruction_ids_jsonb,
                   sticky_scenario_instruction_ids_jsonb,
                   updated_from_run_id,
                   updated_through_turn_no,
                   version,
                   created_at,
                   updated_at
            FROM conversation_working_memory
            """;
    }

    private RowMapper<StoredConversationStickyState> rowMapper() {
        return (resultSet, rowNum) -> new StoredConversationStickyState(
            resultSet.getObject("conversation_id").toString(),
            resultSet.getString("sticky_model"),
            AnswerMode.fromValue(resultSet.getString("sticky_answer_mode")),
            resultSet.getString("sticky_instruction_workspace_key"),
            readNullableJson(resultSet.getString("sticky_knowledge_scope_jsonb"), KnowledgeScope.class),
            readNullableJson(resultSet.getString("sticky_retrieval_filters_jsonb"), RetrievalFilters.class),
            readNullableJson(resultSet.getString("sticky_instruction_ids_jsonb"), STRING_LIST),
            readNullableJson(resultSet.getString("sticky_scenario_instruction_ids_jsonb"), STRING_LIST),
            resultSet.getObject("updated_from_run_id") == null ? null : resultSet.getObject("updated_from_run_id").toString(),
            resultSet.getObject("updated_through_turn_no", Integer.class),
            resultSet.getInt("version"),
            toInstant(resultSet.getTimestamp("created_at")),
            toInstant(resultSet.getTimestamp("updated_at"))
        );
    }

    private String writeNullableJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "conversation_sticky.json_write_failed",
                "Unable to serialize conversation sticky state",
                exception
            );
        }
    }

    private <T> T readNullableJson(String value, Class<T> type) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "conversation_sticky.json_read_failed",
                "Unable to deserialize conversation sticky state",
                exception
            );
        }
    }

    private <T> T readNullableJson(String value, TypeReference<T> type) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "conversation_sticky.json_read_failed",
                "Unable to deserialize conversation sticky state",
                exception
            );
        }
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? Instant.now() : timestamp.toInstant();
    }

    private static StorageException storageFailure(String code, String message, DataAccessException exception) {
        return new StorageException(ErrorType.STORAGE_FAILURE, code, message, exception);
    }
}
