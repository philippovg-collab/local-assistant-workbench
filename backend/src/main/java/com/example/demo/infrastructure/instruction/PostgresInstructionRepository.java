package com.example.demo.infrastructure.instruction;

import com.example.demo.api.ApiException;
import com.example.demo.model.InstructionCategory;
import com.example.demo.model.InstructionScopeLevel;
import com.example.demo.service.instruction.port.InstructionRepository;
import com.example.demo.service.instruction.port.InstructionScopeQuery;
import com.example.demo.service.instruction.port.StoredInstructionRecord;
import com.example.demo.service.instruction.port.StoredInstructionRevisionRecord;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresInstructionRepository implements InstructionRepository {

    private static final RowMapper<StoredInstructionRecord> ROW_MAPPER = (resultSet, rowNum) -> new StoredInstructionRecord(
        resultSet.getObject("id").toString(),
        resultSet.getString("title"),
        InstructionCategory.fromValue(resultSet.getString("category")),
        resultSet.getString("content"),
        resultSet.getString("normalized_content"),
        InstructionScopeLevel.fromValue(resultSet.getString("scope_level")),
        resultSet.getString("scope_target_id"),
        resultSet.getInt("revision"),
        resultSet.getBoolean("is_active"),
        toInstant(resultSet.getTimestamp("created_at")),
        toInstant(resultSet.getTimestamp("updated_at"))
    );

    private static final RowMapper<StoredInstructionRevisionRecord> REVISION_ROW_MAPPER = (resultSet, rowNum) ->
        new StoredInstructionRevisionRecord(
            resultSet.getObject("instruction_id").toString(),
            resultSet.getInt("revision"),
            resultSet.getString("title"),
            InstructionCategory.fromValue(resultSet.getString("category")),
            resultSet.getString("content"),
            resultSet.getString("normalized_content"),
            InstructionScopeLevel.fromValue(resultSet.getString("scope_level")),
            resultSet.getString("scope_target_id"),
            resultSet.getBoolean("is_active"),
            resultSet.getObject("restored_from_revision", Integer.class),
            toInstant(resultSet.getTimestamp("created_at")),
            toInstant(resultSet.getTimestamp("updated_at"))
        );

    private final JdbcTemplate jdbcTemplate;

    public PostgresInstructionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<StoredInstructionRecord> findAll() {
        try {
            return jdbcTemplate.query(
                """
                    SELECT
                        id,
                        title,
                        category,
                        content,
                        normalized_content,
                        scope_level,
                        scope_target_id,
                        revision,
                        is_active,
                        created_at,
                        updated_at
                    FROM instructions
                    ORDER BY created_at DESC
                    """,
                ROW_MAPPER
            );
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "instruction.storage_read_failed",
                "Unable to read instructions from PostgreSQL",
                exception
            );
        }
    }

    @Override
    public Optional<StoredInstructionRecord> findById(String id) {
        try {
            List<StoredInstructionRecord> records = jdbcTemplate.query(
                """
                    SELECT
                        id,
                        title,
                        category,
                        content,
                        normalized_content,
                        scope_level,
                        scope_target_id,
                        revision,
                        is_active,
                        created_at,
                        updated_at
                    FROM instructions
                    WHERE id = ?
                    LIMIT 1
                    """,
                ROW_MAPPER,
                UUID.fromString(id)
            );
            return records.stream().findFirst();
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "instruction.storage_read_failed",
                "Unable to load instruction from PostgreSQL",
                exception
            );
        }
    }

    @Override
    public List<StoredInstructionRecord> findAllByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        String placeholders = ids.stream().map(ignored -> "?").collect(Collectors.joining(", "));
        Object[] params = ids.stream().map(UUID::fromString).toArray();

        try {
            List<StoredInstructionRecord> records = jdbcTemplate.query(
                """
                    SELECT
                        id,
                        title,
                        category,
                        content,
                        normalized_content,
                        scope_level,
                        scope_target_id,
                        revision,
                        is_active,
                        created_at,
                        updated_at
                    FROM instructions
                    WHERE id IN (""" + placeholders + ")",
                ROW_MAPPER,
                params
            );
            Map<String, StoredInstructionRecord> byId = new LinkedHashMap<>();
            for (StoredInstructionRecord record : records) {
                byId.put(record.id(), record);
            }
            return ids.stream()
                .map(byId::get)
                .filter(record -> record != null)
                .toList();
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "instruction.storage_read_failed",
                "Unable to load instructions from PostgreSQL",
                exception
            );
        }
    }

    @Override
    public List<StoredInstructionRecord> findByScope(InstructionScopeQuery scope) {
        if (scope == null || scope.scopeLevel() == null) {
            return List.of();
        }

        StringBuilder sql = new StringBuilder("""
            SELECT
                id,
                title,
                category,
                content,
                normalized_content,
                scope_level,
                scope_target_id,
                revision,
                is_active,
                created_at,
                updated_at
            FROM instructions
            WHERE scope_level = ?
            """);
        java.util.ArrayList<Object> params = new java.util.ArrayList<>();
        params.add(scope.scopeLevel().value());

        if (scope.scopeTargetId() == null || scope.scopeTargetId().isBlank()) {
            sql.append(" AND scope_target_id IS NULL");
        } else {
            sql.append(" AND scope_target_id = ?");
            params.add(scope.scopeTargetId());
        }

        if (scope.activeOnly()) {
            sql.append(" AND is_active = true");
        }

        sql.append(" ORDER BY updated_at ASC, created_at ASC");

        try {
            return jdbcTemplate.query(sql.toString(), ROW_MAPPER, params.toArray());
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "instruction.storage_read_failed",
                "Unable to load scoped instructions from PostgreSQL",
                exception
            );
        }
    }

    @Override
    public List<StoredInstructionRevisionRecord> findRevisions(String instructionId) {
        try {
            return jdbcTemplate.query(
                """
                    SELECT
                        instruction_id,
                        revision,
                        title,
                        category,
                        content,
                        normalized_content,
                        scope_level,
                        scope_target_id,
                        is_active,
                        restored_from_revision,
                        created_at,
                        updated_at
                    FROM instruction_revisions
                    WHERE instruction_id = ?
                    ORDER BY revision DESC
                    """,
                REVISION_ROW_MAPPER,
                UUID.fromString(instructionId)
            );
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "instruction.storage_read_failed",
                "Unable to load instruction revisions from PostgreSQL",
                exception
            );
        }
    }

    @Override
    public Optional<StoredInstructionRevisionRecord> findRevision(String instructionId, int revision) {
        try {
            List<StoredInstructionRevisionRecord> records = jdbcTemplate.query(
                """
                    SELECT
                        instruction_id,
                        revision,
                        title,
                        category,
                        content,
                        normalized_content,
                        scope_level,
                        scope_target_id,
                        is_active,
                        restored_from_revision,
                        created_at,
                        updated_at
                    FROM instruction_revisions
                    WHERE instruction_id = ? AND revision = ?
                    LIMIT 1
                    """,
                REVISION_ROW_MAPPER,
                UUID.fromString(instructionId),
                revision
            );
            return records.stream().findFirst();
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "instruction.storage_read_failed",
                "Unable to load instruction revision from PostgreSQL",
                exception
            );
        }
    }

    @Override
    public void save(StoredInstructionRecord record) {
        try {
            jdbcTemplate.update(
                """
                    INSERT INTO instructions (
                        id,
                        title,
                        category,
                        content,
                        normalized_content,
                        scope_level,
                        scope_target_id,
                        revision,
                        is_active,
                        created_at,
                        updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE
                    SET title = EXCLUDED.title,
                        category = EXCLUDED.category,
                        content = EXCLUDED.content,
                        normalized_content = EXCLUDED.normalized_content,
                        scope_level = EXCLUDED.scope_level,
                        scope_target_id = EXCLUDED.scope_target_id,
                        revision = EXCLUDED.revision,
                        is_active = EXCLUDED.is_active,
                        updated_at = EXCLUDED.updated_at
                    """,
                UUID.fromString(record.id()),
                record.title(),
                record.category().value(),
                record.content(),
                record.normalizedContent(),
                record.scopeLevel().value(),
                record.scopeTargetId(),
                record.revision(),
                record.active(),
                Timestamp.from(record.createdAt()),
                Timestamp.from(record.updatedAt())
            );
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "instruction.storage_write_failed",
                "Unable to persist instruction in PostgreSQL",
                exception
            );
        }
    }

    @Override
    public void appendRevision(StoredInstructionRevisionRecord record) {
        try {
            jdbcTemplate.update(
                """
                    INSERT INTO instruction_revisions (
                        instruction_id,
                        revision,
                        title,
                        category,
                        content,
                        normalized_content,
                        scope_level,
                        scope_target_id,
                        is_active,
                        restored_from_revision,
                        created_at,
                        updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                UUID.fromString(record.instructionId()),
                record.revision(),
                record.title(),
                record.category().value(),
                record.content(),
                record.normalizedContent(),
                record.scopeLevel().value(),
                record.scopeTargetId(),
                record.active(),
                record.restoredFromRevision(),
                Timestamp.from(record.createdAt()),
                Timestamp.from(record.updatedAt())
            );
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "instruction.storage_write_failed",
                "Unable to persist instruction revision in PostgreSQL",
                exception
            );
        }
    }

    @Override
    public void delete(String id) {
        try {
            jdbcTemplate.update("DELETE FROM instructions WHERE id = ?", UUID.fromString(id));
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "instruction.storage_delete_failed",
                "Unable to delete instruction from PostgreSQL",
                exception
            );
        }
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? Instant.now() : timestamp.toInstant();
    }
}
