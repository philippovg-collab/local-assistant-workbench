package com.example.demo.infrastructure.material;

import com.example.demo.error.ErrorType;
import com.example.demo.error.StorageException;
import com.example.demo.service.material.MaterialAutoTaggingLease;
import com.example.demo.service.material.MaterialAutoTaggingStatus;
import com.example.demo.service.material.MaterialAutoTaggingTask;
import com.example.demo.service.material.port.MaterialAutoTaggingTaskRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.jdbc.core.JdbcTemplate;

final class PostgresMaterialAutoTaggingTaskAdapter extends PostgresMaterialJdbcSupport implements MaterialAutoTaggingTaskRepository {

    private static final RowMapper<MaterialAutoTaggingTask> TASK_ROW_MAPPER = (resultSet, rowNum) -> new MaterialAutoTaggingTask(
        resultSet.getObject("id").toString(),
        resultSet.getObject("material_id").toString(),
        resultSet.getString("content_hash"),
        MaterialAutoTaggingStatus.valueOf(resultSet.getString("status")),
        resultSet.getInt("attempt_count"),
        toInstantOrNull(resultSet.getTimestamp("next_retry_at")),
        toInstantOrNull(resultSet.getTimestamp("claimed_at")),
        resultSet.getString("failure_code"),
        resultSet.getString("failure_message"),
        resultSet.getString("result_code"),
        toInstant(resultSet.getTimestamp("created_at")),
        toInstant(resultSet.getTimestamp("updated_at")),
        toInstantOrNull(resultSet.getTimestamp("completed_at"))
    );

    PostgresMaterialAutoTaggingTaskAdapter(
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        super(jdbcTemplate, transactionManager);
    }

    @Override
    public MaterialAutoTaggingTask enqueue(String materialId, String contentHash, Instant now) {
        UUID taskId = UUID.randomUUID();
        UUID materialUuid = UUID.fromString(materialId);
        try {
            List<MaterialAutoTaggingTask> tasks = jdbcTemplate.query(
                """
                    WITH inserted AS (
                        INSERT INTO material_auto_tagging_tasks (
                            id,
                            material_id,
                            content_hash,
                            status,
                            attempt_count,
                            next_retry_at,
                            claimed_at,
                            failure_code,
                            failure_message,
                            result_code,
                            created_at,
                            updated_at,
                            completed_at
                        ) VALUES (?, ?, ?, 'PENDING', 0, ?, NULL, NULL, NULL, NULL, ?, ?, NULL)
                        ON CONFLICT (material_id, content_hash) DO NOTHING
                        RETURNING *
                    )
                    SELECT * FROM inserted
                    UNION ALL
                    SELECT *
                    FROM material_auto_tagging_tasks
                    WHERE material_id = ?
                      AND content_hash = ?
                      AND NOT EXISTS (SELECT 1 FROM inserted)
                    LIMIT 1
                    """,
                TASK_ROW_MAPPER,
                taskId,
                materialUuid,
                contentHash,
                Timestamp.from(now),
                Timestamp.from(now),
                Timestamp.from(now),
                materialUuid,
                contentHash
            );
            if (tasks.isEmpty()) {
                throw new StorageException(
                    ErrorType.STORAGE_FAILURE,
                    "material.auto_tagging_task_unavailable",
                    "Unable to enqueue material auto-tagging task"
                );
            }
            return tasks.getFirst();
        } catch (DataAccessException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "material.storage_write_failed",
                "Unable to enqueue material auto-tagging task in PostgreSQL",
                exception
            );
        }
    }

    @Override
    public Optional<MaterialAutoTaggingTask> findLatestByMaterialId(String materialId) {
        try {
            List<MaterialAutoTaggingTask> tasks = jdbcTemplate.query(
                """
                    SELECT *
                    FROM material_auto_tagging_tasks
                    WHERE material_id = ?
                    ORDER BY created_at DESC, updated_at DESC
                    LIMIT 1
                    """,
                TASK_ROW_MAPPER,
                UUID.fromString(materialId)
            );
            return tasks.stream().findFirst();
        } catch (DataAccessException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "material.storage_read_failed",
                "Unable to load material auto-tagging task from PostgreSQL",
                exception
            );
        }
    }

    @Override
    public Map<String, MaterialAutoTaggingTask> findLatestByMaterialIds(Collection<String> materialIds) {
        if (materialIds == null || materialIds.isEmpty()) {
            return Map.of();
        }
        UUID[] ids = materialIds.stream()
            .filter(id -> id != null && !id.isBlank())
            .distinct()
            .map(UUID::fromString)
            .toArray(UUID[]::new);
        if (ids.length == 0) {
            return Map.of();
        }
        try {
            List<MaterialAutoTaggingTask> tasks = jdbcTemplate.query(
                """
                    SELECT DISTINCT ON (material_id) *
                    FROM material_auto_tagging_tasks
                    WHERE material_id = ANY(?)
                    ORDER BY material_id, created_at DESC, updated_at DESC
                    """,
                preparedStatement -> bindUuidArray(preparedStatement, 1, ids),
                TASK_ROW_MAPPER
            );
            Map<String, MaterialAutoTaggingTask> byMaterialId = new LinkedHashMap<>();
            for (MaterialAutoTaggingTask task : tasks) {
                byMaterialId.put(task.materialId(), task);
            }
            return byMaterialId;
        } catch (DataAccessException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "material.storage_read_failed",
                "Unable to load material auto-tagging tasks from PostgreSQL",
                exception
            );
        }
    }

    @Override
    public Optional<MaterialAutoTaggingLease> claimNext(Instant now) {
        try {
            return transactionTemplate.execute(transactionStatus -> {
                List<MaterialAutoTaggingTask> candidates = jdbcTemplate.query(
                    """
                        SELECT *
                        FROM material_auto_tagging_tasks
                        WHERE status = 'PENDING'
                          AND (next_retry_at IS NULL OR next_retry_at <= ?)
                        ORDER BY COALESCE(next_retry_at, created_at) ASC, created_at ASC
                        LIMIT 1
                        FOR UPDATE SKIP LOCKED
                        """,
                    TASK_ROW_MAPPER,
                    Timestamp.from(now)
                );
                if (candidates.isEmpty()) {
                    return Optional.empty();
                }

                MaterialAutoTaggingTask candidate = candidates.getFirst();
                jdbcTemplate.update(
                    """
                        UPDATE material_auto_tagging_tasks
                        SET status = 'RUNNING',
                            attempt_count = attempt_count + 1,
                            claimed_at = ?,
                            updated_at = ?,
                            result_code = NULL
                        WHERE id = ?
                        """,
                    Timestamp.from(now),
                    Timestamp.from(now),
                    UUID.fromString(candidate.id())
                );
                MaterialAutoTaggingTask claimedTask = findById(candidate.id()).orElseThrow();
                return Optional.of(new MaterialAutoTaggingLease(claimedTask, claimedTask.attemptCount()));
            });
        } catch (DataAccessException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "material.storage_write_failed",
                "Unable to claim material auto-tagging task from PostgreSQL",
                exception
            );
        }
    }

    @Override
    public void resetExpiredClaims(Instant staleBefore, Instant now) {
        try {
            jdbcTemplate.update(
                """
                    UPDATE material_auto_tagging_tasks
                    SET status = 'PENDING',
                        claimed_at = NULL,
                        updated_at = ?
                    WHERE status = 'RUNNING'
                      AND claimed_at IS NOT NULL
                      AND claimed_at < ?
                    """,
                Timestamp.from(now),
                Timestamp.from(staleBefore)
            );
        } catch (DataAccessException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "material.storage_write_failed",
                "Unable to recover stale material auto-tagging claims in PostgreSQL",
                exception
            );
        }
    }

    @Override
    public void markDone(String taskId, String resultCode, Instant now) {
        updateTerminalTask(taskId, MaterialAutoTaggingStatus.DONE, null, null, resultCode, now);
    }

    @Override
    public void markRetry(String taskId, String failureCode, String failureMessage, Instant now, Instant nextRetryAt) {
        try {
            jdbcTemplate.update(
                """
                    UPDATE material_auto_tagging_tasks
                    SET status = 'PENDING',
                        next_retry_at = ?,
                        claimed_at = NULL,
                        failure_code = ?,
                        failure_message = ?,
                        result_code = NULL,
                        updated_at = ?
                    WHERE id = ?
                    """,
                Timestamp.from(nextRetryAt),
                failureCode,
                failureMessage,
                Timestamp.from(now),
                UUID.fromString(taskId)
            );
        } catch (DataAccessException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "material.storage_write_failed",
                "Unable to reschedule material auto-tagging task in PostgreSQL",
                exception
            );
        }
    }

    @Override
    public void markFailed(String taskId, String failureCode, String failureMessage, Instant now) {
        updateTerminalTask(taskId, MaterialAutoTaggingStatus.FAILED, failureCode, failureMessage, failureCode, now);
    }

    @Override
    public boolean hasPending(Instant now) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                """
                    SELECT COUNT(*)
                    FROM material_auto_tagging_tasks
                    WHERE status = 'PENDING'
                      AND (next_retry_at IS NULL OR next_retry_at <= ?)
                    """,
                Integer.class,
                Timestamp.from(now)
            );
            return count != null && count > 0;
        } catch (DataAccessException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "material.storage_read_failed",
                "Unable to inspect material auto-tagging queue in PostgreSQL",
                exception
            );
        }
    }

    private Optional<MaterialAutoTaggingTask> findById(String taskId) {
        List<MaterialAutoTaggingTask> tasks = jdbcTemplate.query(
            """
                SELECT *
                FROM material_auto_tagging_tasks
                WHERE id = ?
                """,
            TASK_ROW_MAPPER,
            UUID.fromString(taskId)
        );
        return tasks.stream().findFirst();
    }

    private void updateTerminalTask(
        String taskId,
        MaterialAutoTaggingStatus status,
        String failureCode,
        String failureMessage,
        String resultCode,
        Instant now
    ) {
        try {
            jdbcTemplate.update(
                """
                    UPDATE material_auto_tagging_tasks
                    SET status = ?,
                        next_retry_at = NULL,
                        claimed_at = NULL,
                        failure_code = ?,
                        failure_message = ?,
                        result_code = ?,
                        updated_at = ?,
                        completed_at = ?
                    WHERE id = ?
                    """,
                status.name(),
                failureCode,
                failureMessage,
                resultCode,
                Timestamp.from(now),
                Timestamp.from(now),
                UUID.fromString(taskId)
            );
        } catch (DataAccessException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "material.storage_write_failed",
                "Unable to finalize material auto-tagging task in PostgreSQL",
                exception
            );
        }
    }
}
