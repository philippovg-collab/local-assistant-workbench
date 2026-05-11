package com.example.demo.infrastructure.eval;

import com.example.demo.error.ErrorType;
import com.example.demo.error.StorageException;
import com.example.demo.model.eval.EvalExecutionConfig;
import com.example.demo.model.eval.EvalFailureCode;
import com.example.demo.model.eval.EvalRun;
import com.example.demo.model.eval.EvalRunItem;
import com.example.demo.model.eval.EvalRunItemArtifact;
import com.example.demo.model.eval.EvalRunItemArtifactType;
import com.example.demo.model.eval.EvalRunItemStatus;
import com.example.demo.model.eval.EvalRunKind;
import com.example.demo.model.eval.EvalRunStatus;
import com.example.demo.service.eval.port.EvalRunRepository;
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
public class PostgresEvalRunRepository implements EvalRunRepository {

    private final JdbcTemplate jdbcTemplate;
    private final EvalJsonSupport jsonSupport;

    public PostgresEvalRunRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.jsonSupport = new EvalJsonSupport(objectMapper);
    }

    @Override
    public List<EvalRun> findRuns() {
        try {
            return jdbcTemplate.query(
                """
                    SELECT id,
                           dataset_id,
                           snapshot_id,
                           run_kind,
                           status,
                           execution_config_jsonb,
                           config_hash,
                           started_at,
                           completed_at,
                           summary_jsonb,
                           created_at,
                           updated_at
                    FROM eval_runs
                    ORDER BY created_at DESC, id ASC
                    """,
                runRowMapper(false)
            );
        } catch (DataAccessException exception) {
            throw storageFailure("eval_run.storage_read_failed", "Unable to load eval runs", exception);
        }
    }

    @Override
    public Optional<EvalRun> findRun(String id) {
        try {
            return jdbcTemplate.query(
                """
                    SELECT id,
                           dataset_id,
                           snapshot_id,
                           run_kind,
                           status,
                           execution_config_jsonb,
                           config_hash,
                           started_at,
                           completed_at,
                           summary_jsonb,
                           created_at,
                           updated_at
                    FROM eval_runs
                    WHERE id = ?
                    LIMIT 1
                    """,
                runRowMapper(true),
                uuid(id)
            ).stream().findFirst();
        } catch (DataAccessException exception) {
            throw storageFailure("eval_run.storage_read_failed", "Unable to load eval run", exception);
        }
    }

    @Override
    public EvalRun saveRun(EvalRun run) {
        Instant now = Instant.now();
        Instant createdAt = run.createdAt() == null ? now : run.createdAt();
        Instant updatedAt = run.updatedAt() == null ? createdAt : run.updatedAt();
        try {
            jdbcTemplate.update(
                """
                    INSERT INTO eval_runs (
                        id,
                        dataset_id,
                        snapshot_id,
                        run_kind,
                        dataset_version,
                        corpus_snapshot_id,
                        status,
                        execution_config_jsonb,
                        config_hash,
                        execution_config_hash,
                        started_at,
                        completed_at,
                        summary_jsonb,
                        created_at,
                        updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?::jsonb, ?, ?)
                    ON CONFLICT (id) DO UPDATE
                    SET dataset_id = EXCLUDED.dataset_id,
                        snapshot_id = EXCLUDED.snapshot_id,
                        run_kind = EXCLUDED.run_kind,
                        dataset_version = EXCLUDED.dataset_version,
                        corpus_snapshot_id = EXCLUDED.corpus_snapshot_id,
                        status = EXCLUDED.status,
                        execution_config_jsonb = EXCLUDED.execution_config_jsonb,
                        config_hash = EXCLUDED.config_hash,
                        execution_config_hash = EXCLUDED.execution_config_hash,
                        started_at = EXCLUDED.started_at,
                        completed_at = EXCLUDED.completed_at,
                        summary_jsonb = EXCLUDED.summary_jsonb,
                        updated_at = EXCLUDED.updated_at
                    """,
                uuid(run.id()),
                nullableUuid(run.datasetId()),
                nullableUuid(run.snapshotId()),
                run.runKind().name(),
                run.executionConfig().datasetVersion(),
                nullableUuid(run.executionConfig().corpusSnapshotId() == null ? run.snapshotId() : run.executionConfig().corpusSnapshotId()),
                run.status().name(),
                writeJson(run.executionConfig()),
                run.configHash(),
                run.executionConfig().configHash() == null ? run.configHash() : run.executionConfig().configHash(),
                timestampOrNull(run.startedAt()),
                timestampOrNull(run.completedAt()),
                writeJson(run.summary()),
                Timestamp.from(createdAt),
                Timestamp.from(updatedAt)
            );
            return findRun(run.id()).orElse(run);
        } catch (DataAccessException exception) {
            throw storageFailure("eval_run.storage_write_failed", "Unable to persist eval run", exception);
        }
    }

    @Override
    public EvalRunItem saveItem(EvalRunItem item) {
        Instant now = Instant.now();
        Instant createdAt = item.createdAt() == null ? now : item.createdAt();
        Instant updatedAt = item.updatedAt() == null ? createdAt : item.updatedAt();
        try {
            jdbcTemplate.update(
                """
                    INSERT INTO eval_run_items (
                        id,
                        run_id,
                        case_id,
                        case_revision,
                        chat_run_id,
                        context_assembly_id,
                        status,
                        failure_code,
                        failure_message,
                        artifact_jsonb,
                        scorer_jsonb,
                        score_summary_jsonb,
                        created_at,
                        updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?)
                    ON CONFLICT (id) DO UPDATE
                    SET case_id = EXCLUDED.case_id,
                        case_revision = EXCLUDED.case_revision,
                        chat_run_id = EXCLUDED.chat_run_id,
                        context_assembly_id = EXCLUDED.context_assembly_id,
                        status = EXCLUDED.status,
                        failure_code = EXCLUDED.failure_code,
                        failure_message = EXCLUDED.failure_message,
                        artifact_jsonb = EXCLUDED.artifact_jsonb,
                        scorer_jsonb = EXCLUDED.scorer_jsonb,
                        score_summary_jsonb = EXCLUDED.score_summary_jsonb,
                        updated_at = EXCLUDED.updated_at
                    """,
                uuid(item.id()),
                uuid(item.runId()),
                nullableUuid(item.caseId()),
                item.caseRevision(),
                nullableUuid(item.chatRunId()),
                nullableUuid(item.contextAssemblyId()),
                item.status().name(),
                item.failureCode() == null ? null : item.failureCode().name(),
                item.failureMessage(),
                writeJson(item.artifact()),
                writeJson(item.scorer()),
                writeJson(item.scoreSummary()),
                Timestamp.from(createdAt),
                Timestamp.from(updatedAt)
            );
            return findItem(item.id()).orElse(item);
        } catch (DataAccessException exception) {
            throw storageFailure("eval_run_item.storage_write_failed", "Unable to persist eval run item", exception);
        }
    }

    @Override
    public List<EvalRunItem> findItemsByRunId(String runId) {
        try {
            return jdbcTemplate.query(
                """
                    SELECT id,
                           run_id,
                           case_id,
                           case_revision,
                           chat_run_id,
                           context_assembly_id,
                           status,
                           failure_code,
                           failure_message,
                           artifact_jsonb,
                           scorer_jsonb,
                           score_summary_jsonb,
                           created_at,
                           updated_at
                    FROM eval_run_items
                    WHERE run_id = ?
                    ORDER BY created_at ASC, id ASC
                    """,
                itemRowMapper(),
                uuid(runId)
            );
        } catch (DataAccessException exception) {
            throw storageFailure("eval_run_item.storage_read_failed", "Unable to load eval run items", exception);
        }
    }

    @Override
    public List<EvalRunItem> findOpenE2EItems() {
        try {
            return jdbcTemplate.query(
                """
                    SELECT i.id,
                           i.run_id,
                           i.case_id,
                           i.case_revision,
                           i.chat_run_id,
                           i.context_assembly_id,
                           i.status,
                           i.failure_code,
                           i.failure_message,
                           i.artifact_jsonb,
                           i.scorer_jsonb,
                           i.score_summary_jsonb,
                           i.created_at,
                           i.updated_at
                    FROM eval_run_items i
                    JOIN eval_runs r ON r.id = i.run_id
                    WHERE r.run_kind = 'E2E'
                      AND i.chat_run_id IS NOT NULL
                      AND i.status IN ('PENDING', 'RUNNING')
                    ORDER BY i.created_at ASC, i.id ASC
                    """,
                itemRowMapper()
            );
        } catch (DataAccessException exception) {
            throw storageFailure("eval_run_item.storage_read_failed", "Unable to load open E2E eval items", exception);
        }
    }

    @Override
    public List<EvalRunItem> findOpenE2EItemsByRunId(String runId) {
        try {
            return jdbcTemplate.query(
                """
                    SELECT i.id,
                           i.run_id,
                           i.case_id,
                           i.case_revision,
                           i.chat_run_id,
                           i.context_assembly_id,
                           i.status,
                           i.failure_code,
                           i.failure_message,
                           i.artifact_jsonb,
                           i.scorer_jsonb,
                           i.score_summary_jsonb,
                           i.created_at,
                           i.updated_at
                    FROM eval_run_items i
                    JOIN eval_runs r ON r.id = i.run_id
                    WHERE r.run_kind = 'E2E'
                      AND i.run_id = ?
                      AND i.chat_run_id IS NOT NULL
                      AND i.status IN ('PENDING', 'RUNNING')
                    ORDER BY i.created_at ASC, i.id ASC
                    """,
                itemRowMapper(),
                uuid(runId)
            );
        } catch (DataAccessException exception) {
            throw storageFailure("eval_run_item.storage_read_failed", "Unable to load open E2E eval items", exception);
        }
    }

    @Override
    public EvalRunItemArtifact saveArtifact(EvalRunItemArtifact artifact) {
        Instant now = Instant.now();
        Instant createdAt = artifact.createdAt() == null ? now : artifact.createdAt();
        Instant updatedAt = artifact.updatedAt() == null ? createdAt : artifact.updatedAt();
        try {
            jdbcTemplate.update(
                """
                    INSERT INTO eval_run_item_artifacts (
                        run_item_id,
                        artifact_type,
                        payload_jsonb,
                        created_at,
                        updated_at
                    ) VALUES (?, ?, ?::jsonb, ?, ?)
                    ON CONFLICT (run_item_id, artifact_type) DO UPDATE
                    SET payload_jsonb = EXCLUDED.payload_jsonb,
                        updated_at = EXCLUDED.updated_at
                    """,
                uuid(artifact.runItemId()),
                artifact.artifactType().name(),
                writeJson(artifact.payload()),
                Timestamp.from(createdAt),
                Timestamp.from(updatedAt)
            );
            return findArtifact(artifact.runItemId(), artifact.artifactType()).orElse(artifact);
        } catch (DataAccessException exception) {
            throw storageFailure("eval_run_item_artifact.storage_write_failed", "Unable to persist eval artifact", exception);
        }
    }

    @Override
    public List<EvalRunItemArtifact> findArtifactsByItemId(String itemId) {
        try {
            return jdbcTemplate.query(
                """
                    SELECT run_item_id,
                           artifact_type,
                           payload_jsonb,
                           created_at,
                           updated_at
                    FROM eval_run_item_artifacts
                    WHERE run_item_id = ?
                    ORDER BY artifact_type ASC
                    """,
                artifactRowMapper(),
                uuid(itemId)
            );
        } catch (DataAccessException exception) {
            throw storageFailure("eval_run_item_artifact.storage_read_failed", "Unable to load eval artifacts", exception);
        }
    }

    @Override
    public Optional<EvalRunItemArtifact> findArtifact(String itemId, EvalRunItemArtifactType artifactType) {
        try {
            return jdbcTemplate.query(
                """
                    SELECT run_item_id,
                           artifact_type,
                           payload_jsonb,
                           created_at,
                           updated_at
                    FROM eval_run_item_artifacts
                    WHERE run_item_id = ?
                      AND artifact_type = ?
                    LIMIT 1
                    """,
                artifactRowMapper(),
                uuid(itemId),
                artifactType.name()
            ).stream().findFirst();
        } catch (DataAccessException exception) {
            throw storageFailure("eval_run_item_artifact.storage_read_failed", "Unable to load eval artifact", exception);
        }
    }

    @Override
    public boolean isStorageReady() {
        try {
            Boolean ready = jdbcTemplate.queryForObject(
                """
                    SELECT to_regclass('public.eval_runs') IS NOT NULL
                       AND to_regclass('public.eval_run_items') IS NOT NULL
                       AND to_regclass('public.eval_run_item_artifacts') IS NOT NULL
                    """,
                Boolean.class
            );
            return Boolean.TRUE.equals(ready);
        } catch (DataAccessException exception) {
            throw storageFailure("eval_run.storage_read_failed", "Unable to check eval run storage readiness", exception);
        }
    }

    @Override
    public Optional<EvalRunItem> findItem(String id) {
        return jdbcTemplate.query(
            """
                SELECT id,
                       run_id,
                       case_id,
                       case_revision,
                       chat_run_id,
                       context_assembly_id,
                       status,
                       failure_code,
                       failure_message,
                       artifact_jsonb,
                       scorer_jsonb,
                       score_summary_jsonb,
                       created_at,
                       updated_at
                FROM eval_run_items
                WHERE id = ?
                LIMIT 1
                """,
            itemRowMapper(),
            uuid(id)
        ).stream().findFirst();
    }

    private RowMapper<EvalRun> runRowMapper(boolean includeItems) {
        return (resultSet, rowNum) -> {
            String id = resultSet.getObject("id").toString();
            return new EvalRun(
                id,
                toStringOrNull(resultSet.getObject("dataset_id")),
                toStringOrNull(resultSet.getObject("snapshot_id")),
                EvalRunKind.valueOf(resultSet.getString("run_kind")),
                EvalRunStatus.valueOf(resultSet.getString("status")),
                jsonSupport.read(resultSet.getString("execution_config_jsonb"), EvalExecutionConfig.class, "eval_run.json_read_failed", "Unable to read eval run config"),
                resultSet.getString("config_hash"),
                toInstant(resultSet.getTimestamp("started_at")),
                toInstant(resultSet.getTimestamp("completed_at")),
                jsonSupport.readMap(resultSet.getString("summary_jsonb"), "eval_run.json_read_failed", "Unable to read eval run summary"),
                includeItems ? findItemsByRunId(id) : List.of(),
                toInstant(resultSet.getTimestamp("created_at")),
                toInstant(resultSet.getTimestamp("updated_at"))
            );
        };
    }

    private RowMapper<EvalRunItem> itemRowMapper() {
        return (resultSet, rowNum) -> new EvalRunItem(
            resultSet.getObject("id").toString(),
            resultSet.getObject("run_id").toString(),
            toStringOrNull(resultSet.getObject("case_id")),
            nullableInteger(resultSet.getObject("case_revision")),
            toStringOrNull(resultSet.getObject("chat_run_id")),
            toStringOrNull(resultSet.getObject("context_assembly_id")),
            EvalRunItemStatus.valueOf(resultSet.getString("status")),
            nullableFailureCode(resultSet.getString("failure_code")),
            resultSet.getString("failure_message"),
            jsonSupport.readMap(resultSet.getString("artifact_jsonb"), "eval_run_item.json_read_failed", "Unable to read eval run item artifact"),
            jsonSupport.readMap(resultSet.getString("scorer_jsonb"), "eval_run_item.json_read_failed", "Unable to read eval run item scorer"),
            jsonSupport.readMap(resultSet.getString("score_summary_jsonb"), "eval_run_item.json_read_failed", "Unable to read eval run item score summary"),
            toInstant(resultSet.getTimestamp("created_at")),
            toInstant(resultSet.getTimestamp("updated_at"))
        );
    }

    private RowMapper<EvalRunItemArtifact> artifactRowMapper() {
        return (resultSet, rowNum) -> new EvalRunItemArtifact(
            resultSet.getObject("run_item_id").toString(),
            EvalRunItemArtifactType.valueOf(resultSet.getString("artifact_type")),
            jsonSupport.readMap(resultSet.getString("payload_jsonb"), "eval_run_item_artifact.json_read_failed", "Unable to read eval artifact"),
            toInstant(resultSet.getTimestamp("created_at")),
            toInstant(resultSet.getTimestamp("updated_at"))
        );
    }

    private EvalFailureCode nullableFailureCode(String value) {
        return value == null || value.isBlank() ? null : EvalFailureCode.valueOf(value);
    }

    private String writeJson(Object value) {
        return jsonSupport.write(value, "eval_run.json_write_failed", "Unable to serialize eval run payload");
    }

    private UUID uuid(String id) {
        return UUID.fromString(id);
    }

    private UUID nullableUuid(String id) {
        return id == null || id.isBlank() ? null : UUID.fromString(id);
    }

    private Integer nullableInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.valueOf(String.valueOf(value));
    }

    private Timestamp timestampOrNull(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private String toStringOrNull(Object value) {
        return value == null ? null : value.toString();
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private StorageException storageFailure(String code, String message, DataAccessException exception) {
        return new StorageException(ErrorType.STORAGE_FAILURE, code, message, exception);
    }
}
