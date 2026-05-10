package com.example.demo.infrastructure.reference;

import com.example.demo.error.ErrorType;
import com.example.demo.error.StorageException;
import com.example.demo.service.reference.StoredReferenceProjectRecord;
import com.example.demo.service.reference.StoredReferenceWorkspaceRecord;
import com.example.demo.service.reference.port.ReferenceDataRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresReferenceDataRepository implements ReferenceDataRepository {

    private static final RowMapper<StoredReferenceWorkspaceRecord> WORKSPACE_ROW_MAPPER = (resultSet, rowNum) ->
        new StoredReferenceWorkspaceRecord(
            resultSet.getString("key"),
            resultSet.getString("name_ru"),
            resultSet.getString("description"),
            resultSet.getBoolean("active"),
            resultSet.getInt("sort_order"),
            resultSet.getBoolean("is_default"),
            toInstant(resultSet.getTimestamp("created_at")),
            toInstant(resultSet.getTimestamp("updated_at"))
        );

    private static final RowMapper<StoredReferenceProjectRecord> PROJECT_ROW_MAPPER = (resultSet, rowNum) ->
        new StoredReferenceProjectRecord(
            resultSet.getString("key"),
            resultSet.getString("workspace_key"),
            resultSet.getString("name_ru"),
            resultSet.getBoolean("active"),
            resultSet.getInt("sort_order"),
            toInstant(resultSet.getTimestamp("created_at")),
            toInstant(resultSet.getTimestamp("updated_at"))
        );

    private final JdbcTemplate jdbcTemplate;

    public PostgresReferenceDataRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<StoredReferenceWorkspaceRecord> findWorkspaces(boolean activeOnly) {
        try {
            return jdbcTemplate.query(
                """
                    SELECT key, name_ru, description, active, sort_order, is_default, created_at, updated_at
                    FROM reference_workspaces
                    WHERE (? = false OR active = true)
                    ORDER BY sort_order ASC, name_ru ASC
                    """,
                WORKSPACE_ROW_MAPPER,
                activeOnly
            );
        } catch (DataAccessException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "reference_workspace.storage_read_failed",
                "Unable to read reference workspaces from PostgreSQL",
                exception
            );
        }
    }

    public Optional<StoredReferenceWorkspaceRecord> findWorkspaceByKey(String key) {
        try {
            List<StoredReferenceWorkspaceRecord> records = jdbcTemplate.query(
                """
                    SELECT key, name_ru, description, active, sort_order, is_default, created_at, updated_at
                    FROM reference_workspaces
                    WHERE key = ?
                    LIMIT 1
                    """,
                WORKSPACE_ROW_MAPPER,
                key
            );
            return records.stream().findFirst();
        } catch (DataAccessException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "reference_workspace.storage_read_failed",
                "Unable to load reference workspace from PostgreSQL",
                exception
            );
        }
    }

    public boolean workspaceExists(String key) {
        try {
            Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM reference_workspaces WHERE key = ?)",
                Boolean.class,
                key
            );
            return Boolean.TRUE.equals(exists);
        } catch (DataAccessException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "reference_workspace.storage_read_failed",
                "Unable to check reference workspace existence in PostgreSQL",
                exception
            );
        }
    }

    public int countDefaultWorkspaces() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reference_workspaces WHERE is_default = true",
                Integer.class
            );
            return count == null ? 0 : count;
        } catch (DataAccessException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "reference_workspace.storage_read_failed",
                "Unable to count default reference workspaces in PostgreSQL",
                exception
            );
        }
    }

    public void clearDefaultWorkspacesExcept(String key, Instant updatedAt) {
        try {
            jdbcTemplate.update(
                """
                    UPDATE reference_workspaces
                    SET is_default = false,
                        updated_at = ?
                    WHERE is_default = true
                      AND key <> ?
                    """,
                Timestamp.from(updatedAt),
                key
            );
        } catch (DataAccessException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "reference_workspace.storage_write_failed",
                "Unable to update default reference workspace in PostgreSQL",
                exception
            );
        }
    }

    public StoredReferenceWorkspaceRecord saveWorkspace(StoredReferenceWorkspaceRecord record) {
        try {
            jdbcTemplate.update(
                """
                    INSERT INTO reference_workspaces (
                        key,
                        name_ru,
                        description,
                        active,
                        sort_order,
                        is_default,
                        created_at,
                        updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (key) DO UPDATE
                    SET name_ru = EXCLUDED.name_ru,
                        description = EXCLUDED.description,
                        active = EXCLUDED.active,
                        sort_order = EXCLUDED.sort_order,
                        is_default = EXCLUDED.is_default,
                        updated_at = EXCLUDED.updated_at
                    """,
                record.key(),
                record.nameRu(),
                record.description(),
                record.active(),
                record.sortOrder(),
                record.isDefault(),
                Timestamp.from(record.createdAt()),
                Timestamp.from(record.updatedAt())
            );
            return findWorkspaceByKey(record.key()).orElse(record);
        } catch (DataAccessException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "reference_workspace.storage_write_failed",
                "Unable to persist reference workspace in PostgreSQL",
                exception
            );
        }
    }

    public List<StoredReferenceProjectRecord> findProjects(boolean activeOnly, String workspaceKey) {
        try {
            return jdbcTemplate.query(
                """
                    SELECT key, workspace_key, name_ru, active, sort_order, created_at, updated_at
                    FROM reference_projects
                    WHERE (? = false OR active = true)
                      AND (CAST(? AS text) IS NULL OR workspace_key = ?)
                    ORDER BY sort_order ASC, name_ru ASC
                    """,
                PROJECT_ROW_MAPPER,
                activeOnly,
                workspaceKey,
                workspaceKey
            );
        } catch (DataAccessException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "reference_project.storage_read_failed",
                "Unable to read reference projects from PostgreSQL",
                exception
            );
        }
    }

    public Optional<StoredReferenceProjectRecord> findProjectByKey(String key) {
        try {
            List<StoredReferenceProjectRecord> records = jdbcTemplate.query(
                """
                    SELECT key, workspace_key, name_ru, active, sort_order, created_at, updated_at
                    FROM reference_projects
                    WHERE key = ?
                    LIMIT 1
                    """,
                PROJECT_ROW_MAPPER,
                key
            );
            return records.stream().findFirst();
        } catch (DataAccessException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "reference_project.storage_read_failed",
                "Unable to load reference project from PostgreSQL",
                exception
            );
        }
    }

    public StoredReferenceProjectRecord saveProject(StoredReferenceProjectRecord record) {
        try {
            jdbcTemplate.update(
                """
                    INSERT INTO reference_projects (
                        key,
                        workspace_key,
                        name_ru,
                        active,
                        sort_order,
                        created_at,
                        updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (key) DO UPDATE
                    SET workspace_key = EXCLUDED.workspace_key,
                        name_ru = EXCLUDED.name_ru,
                        active = EXCLUDED.active,
                        sort_order = EXCLUDED.sort_order,
                        updated_at = EXCLUDED.updated_at
                    """,
                record.key(),
                record.workspaceKey(),
                record.nameRu(),
                record.active(),
                record.sortOrder(),
                Timestamp.from(record.createdAt()),
                Timestamp.from(record.updatedAt())
            );
            return findProjectByKey(record.key()).orElse(record);
        } catch (DataAccessException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "reference_project.storage_write_failed",
                "Unable to persist reference project in PostgreSQL",
                exception
            );
        }
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? Instant.now() : timestamp.toInstant();
    }
}
