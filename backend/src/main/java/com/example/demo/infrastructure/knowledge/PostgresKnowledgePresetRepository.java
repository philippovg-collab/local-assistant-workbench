package com.example.demo.infrastructure.knowledge;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.example.demo.api.ApiException;
import com.example.demo.model.KnowledgeScope;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresKnowledgePresetRepository {

    private static final ObjectMapper JSON_MAPPER = JsonMapper.builder().findAndAddModules().build();

    private static final RowMapper<StoredKnowledgePresetRecord> PRESET_ROW_MAPPER = (resultSet, rowNum) ->
        new StoredKnowledgePresetRecord(
            resultSet.getObject("id").toString(),
            resultSet.getString("name"),
            resultSet.getString("description"),
            readScope(resultSet.getString("scope_jsonb")),
            resultSet.getInt("revision"),
            resultSet.getBoolean("is_active"),
            toInstant(resultSet.getTimestamp("created_at")),
            toInstant(resultSet.getTimestamp("updated_at"))
        );

    private static final RowMapper<StoredKnowledgePresetRevisionRecord> REVISION_ROW_MAPPER = (resultSet, rowNum) ->
        new StoredKnowledgePresetRevisionRecord(
            resultSet.getObject("preset_id").toString(),
            resultSet.getInt("revision"),
            resultSet.getString("name"),
            resultSet.getString("description"),
            readScope(resultSet.getString("scope_jsonb")),
            resultSet.getBoolean("is_active"),
            resultSet.getObject("restored_from_revision", Integer.class),
            toInstant(resultSet.getTimestamp("created_at")),
            toInstant(resultSet.getTimestamp("updated_at"))
        );

    private final JdbcTemplate jdbcTemplate;

    public PostgresKnowledgePresetRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<StoredKnowledgePresetRecord> findAll() {
        try {
            return jdbcTemplate.query(
                """
                    SELECT id, name, description, scope_jsonb, revision, is_active, created_at, updated_at
                    FROM knowledge_presets
                    ORDER BY updated_at DESC, created_at DESC
                    """,
                PRESET_ROW_MAPPER
            );
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "knowledge_preset.storage_read_failed",
                "Unable to read knowledge presets from PostgreSQL",
                exception
            );
        }
    }

    public Optional<StoredKnowledgePresetRecord> findById(String id) {
        try {
            List<StoredKnowledgePresetRecord> records = jdbcTemplate.query(
                """
                    SELECT id, name, description, scope_jsonb, revision, is_active, created_at, updated_at
                    FROM knowledge_presets
                    WHERE id = ?
                    LIMIT 1
                    """,
                PRESET_ROW_MAPPER,
                UUID.fromString(id)
            );
            return records.stream().findFirst();
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "knowledge_preset.storage_read_failed",
                "Unable to load knowledge preset from PostgreSQL",
                exception
            );
        }
    }

    public void save(StoredKnowledgePresetRecord record) {
        try {
            jdbcTemplate.update(
                """
                    INSERT INTO knowledge_presets (
                        id,
                        name,
                        description,
                        scope_jsonb,
                        revision,
                        is_active,
                        created_at,
                        updated_at
                    ) VALUES (?, ?, ?, ?::jsonb, ?, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE
                    SET name = EXCLUDED.name,
                        description = EXCLUDED.description,
                        scope_jsonb = EXCLUDED.scope_jsonb,
                        revision = EXCLUDED.revision,
                        is_active = EXCLUDED.is_active,
                        updated_at = EXCLUDED.updated_at
                    """,
                UUID.fromString(record.id()),
                record.name(),
                record.description(),
                writeScope(record.scope()),
                record.revision(),
                record.active(),
                Timestamp.from(record.createdAt()),
                Timestamp.from(record.updatedAt())
            );
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "knowledge_preset.storage_write_failed",
                "Unable to persist knowledge preset in PostgreSQL",
                exception
            );
        }
    }

    public List<StoredKnowledgePresetRevisionRecord> findRevisions(String presetId) {
        try {
            return jdbcTemplate.query(
                """
                    SELECT preset_id, revision, name, description, scope_jsonb, is_active, restored_from_revision, created_at, updated_at
                    FROM knowledge_preset_revisions
                    WHERE preset_id = ?
                    ORDER BY revision DESC
                    """,
                REVISION_ROW_MAPPER,
                UUID.fromString(presetId)
            );
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "knowledge_preset.storage_read_failed",
                "Unable to load knowledge preset revisions from PostgreSQL",
                exception
            );
        }
    }

    public Optional<StoredKnowledgePresetRevisionRecord> findRevision(String presetId, int revision) {
        try {
            List<StoredKnowledgePresetRevisionRecord> records = jdbcTemplate.query(
                """
                    SELECT preset_id, revision, name, description, scope_jsonb, is_active, restored_from_revision, created_at, updated_at
                    FROM knowledge_preset_revisions
                    WHERE preset_id = ? AND revision = ?
                    LIMIT 1
                    """,
                REVISION_ROW_MAPPER,
                UUID.fromString(presetId),
                revision
            );
            return records.stream().findFirst();
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "knowledge_preset.storage_read_failed",
                "Unable to load knowledge preset revision from PostgreSQL",
                exception
            );
        }
    }

    public void appendRevision(StoredKnowledgePresetRevisionRecord record) {
        try {
            jdbcTemplate.update(
                """
                    INSERT INTO knowledge_preset_revisions (
                        preset_id,
                        revision,
                        name,
                        description,
                        scope_jsonb,
                        is_active,
                        restored_from_revision,
                        created_at,
                        updated_at
                    ) VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?)
                    """,
                UUID.fromString(record.presetId()),
                record.revision(),
                record.name(),
                record.description(),
                writeScope(record.scope()),
                record.active(),
                record.restoredFromRevision(),
                Timestamp.from(record.createdAt()),
                Timestamp.from(record.updatedAt())
            );
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "knowledge_preset.storage_write_failed",
                "Unable to persist knowledge preset revision in PostgreSQL",
                exception
            );
        }
    }

    public void delete(String presetId) {
        try {
            jdbcTemplate.update("DELETE FROM knowledge_presets WHERE id = ?", UUID.fromString(presetId));
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "knowledge_preset.storage_delete_failed",
                "Unable to delete knowledge preset from PostgreSQL",
                exception
            );
        }
    }

    private static KnowledgeScope readScope(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return KnowledgeScope.empty();
        }

        try {
            return JSON_MAPPER.readValue(rawJson, KnowledgeScope.class);
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "knowledge_preset.storage_decode_failed",
                "Unable to decode knowledge preset scope from PostgreSQL",
                exception
            );
        }
    }

    private static String writeScope(KnowledgeScope scope) {
        try {
            return JSON_MAPPER.writeValueAsString(scope == null ? KnowledgeScope.empty() : scope);
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "knowledge_preset.storage_encode_failed",
                "Unable to encode knowledge preset scope for PostgreSQL",
                exception
            );
        }
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? Instant.now() : timestamp.toInstant();
    }
}
