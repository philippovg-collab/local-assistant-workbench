package com.example.demo.infrastructure.instruction;

import com.example.demo.api.ApiException;
import com.example.demo.model.InstructionCategory;
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
                        created_at,
                        updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE
                    SET title = EXCLUDED.title,
                        category = EXCLUDED.category,
                        content = EXCLUDED.content,
                        normalized_content = EXCLUDED.normalized_content,
                        updated_at = EXCLUDED.updated_at
                    """,
                UUID.fromString(record.id()),
                record.title(),
                record.category().value(),
                record.content(),
                record.normalizedContent(),
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
