package com.example.demo.infrastructure.eval;

import com.example.demo.error.ErrorType;
import com.example.demo.error.StorageException;
import com.example.demo.model.eval.EvalCompareStatus;
import com.example.demo.model.eval.EvalCompatibilityReason;
import com.example.demo.model.eval.EvalCompatibilityStatus;
import com.example.demo.model.eval.EvalRunCompare;
import com.example.demo.service.eval.port.EvalCompareRepository;
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
public class PostgresEvalCompareRepository implements EvalCompareRepository {

    private final JdbcTemplate jdbcTemplate;
    private final EvalJsonSupport jsonSupport;

    public PostgresEvalCompareRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.jsonSupport = new EvalJsonSupport(objectMapper);
    }

    @Override
    public List<EvalRunCompare> findCompares() {
        try {
            return jdbcTemplate.query(selectSql() + " ORDER BY created_at DESC, id ASC", rowMapper());
        } catch (DataAccessException exception) {
            throw storageFailure("eval_compare.storage_read_failed", "Unable to load eval compares", exception);
        }
    }

    @Override
    public Optional<EvalRunCompare> findCompare(String id) {
        try {
            return jdbcTemplate.query(
                selectSql() + " WHERE id = ? LIMIT 1",
                rowMapper(),
                uuid(id)
            ).stream().findFirst();
        } catch (DataAccessException exception) {
            throw storageFailure("eval_compare.storage_read_failed", "Unable to load eval compare", exception);
        }
    }

    @Override
    public EvalRunCompare saveCompare(EvalRunCompare compare) {
        Instant now = Instant.now();
        Instant createdAt = compare.createdAt() == null ? now : compare.createdAt();
        Instant updatedAt = compare.updatedAt() == null ? createdAt : compare.updatedAt();
        try {
            jdbcTemplate.update(
                """
                    INSERT INTO eval_compares (
                        id,
                        baseline_run_id,
                        candidate_run_id,
                        status,
                        compatibility_status,
                        compatibility_reason,
                        compatibility_reasons_jsonb,
                        baseline_dataset_version,
                        candidate_dataset_version,
                        baseline_snapshot_id,
                        candidate_snapshot_id,
                        baseline_material_set_hash,
                        candidate_material_set_hash,
                        baseline_search_state_hash,
                        candidate_search_state_hash,
                        baseline_config_hash,
                        candidate_config_hash,
                        summary_jsonb,
                        created_at,
                        updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
                    ON CONFLICT (id) DO UPDATE
                    SET baseline_run_id = EXCLUDED.baseline_run_id,
                        candidate_run_id = EXCLUDED.candidate_run_id,
                        status = EXCLUDED.status,
                        compatibility_status = EXCLUDED.compatibility_status,
                        compatibility_reason = EXCLUDED.compatibility_reason,
                        compatibility_reasons_jsonb = EXCLUDED.compatibility_reasons_jsonb,
                        baseline_dataset_version = EXCLUDED.baseline_dataset_version,
                        candidate_dataset_version = EXCLUDED.candidate_dataset_version,
                        baseline_snapshot_id = EXCLUDED.baseline_snapshot_id,
                        candidate_snapshot_id = EXCLUDED.candidate_snapshot_id,
                        baseline_material_set_hash = EXCLUDED.baseline_material_set_hash,
                        candidate_material_set_hash = EXCLUDED.candidate_material_set_hash,
                        baseline_search_state_hash = EXCLUDED.baseline_search_state_hash,
                        candidate_search_state_hash = EXCLUDED.candidate_search_state_hash,
                        baseline_config_hash = EXCLUDED.baseline_config_hash,
                        candidate_config_hash = EXCLUDED.candidate_config_hash,
                        summary_jsonb = EXCLUDED.summary_jsonb,
                        updated_at = EXCLUDED.updated_at
                    """,
                uuid(compare.id()),
                uuid(compare.baselineRunId()),
                uuid(compare.candidateRunId()),
                compare.status().name(),
                compare.compatibilityStatus().name(),
                compare.compatibilityReason(),
                writeJson(compare.compatibilityReasons()),
                compare.baselineDatasetVersion(),
                compare.candidateDatasetVersion(),
                nullableUuid(compare.baselineSnapshotId()),
                nullableUuid(compare.candidateSnapshotId()),
                compare.baselineMaterialSetHash(),
                compare.candidateMaterialSetHash(),
                compare.baselineSearchStateHash(),
                compare.candidateSearchStateHash(),
                compare.baselineConfigHash(),
                compare.candidateConfigHash(),
                writeJson(compare.summary()),
                Timestamp.from(createdAt),
                Timestamp.from(updatedAt)
            );
            return findCompare(compare.id()).orElse(compare);
        } catch (DataAccessException exception) {
            throw storageFailure("eval_compare.storage_write_failed", "Unable to persist eval compare", exception);
        }
    }

    @Override
    public boolean isStorageReady() {
        try {
            Boolean ready = jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.eval_compares') IS NOT NULL",
                Boolean.class
            );
            return Boolean.TRUE.equals(ready);
        } catch (DataAccessException exception) {
            throw storageFailure("eval_compare.storage_read_failed", "Unable to check eval compare storage readiness", exception);
        }
    }

    private String selectSql() {
        return """
            SELECT id,
                   baseline_run_id,
                   candidate_run_id,
                   status,
                   compatibility_status,
                   compatibility_reason,
                   compatibility_reasons_jsonb,
                   baseline_dataset_version,
                   candidate_dataset_version,
                   baseline_snapshot_id,
                   candidate_snapshot_id,
                   baseline_material_set_hash,
                   candidate_material_set_hash,
                   baseline_search_state_hash,
                   candidate_search_state_hash,
                   baseline_config_hash,
                   candidate_config_hash,
                   summary_jsonb,
                   created_at,
                   updated_at
            FROM eval_compares
            """;
    }

    private RowMapper<EvalRunCompare> rowMapper() {
        return (resultSet, rowNum) -> new EvalRunCompare(
            resultSet.getObject("id").toString(),
            resultSet.getObject("baseline_run_id").toString(),
            resultSet.getObject("candidate_run_id").toString(),
            EvalCompareStatus.valueOf(resultSet.getString("status")),
            EvalCompatibilityStatus.valueOf(resultSet.getString("compatibility_status")),
            readReasons(resultSet.getString("compatibility_reasons_jsonb")),
            resultSet.getString("compatibility_reason"),
            resultSet.getString("baseline_dataset_version"),
            resultSet.getString("candidate_dataset_version"),
            toStringOrNull(resultSet.getObject("baseline_snapshot_id")),
            toStringOrNull(resultSet.getObject("candidate_snapshot_id")),
            resultSet.getString("baseline_material_set_hash"),
            resultSet.getString("candidate_material_set_hash"),
            resultSet.getString("baseline_search_state_hash"),
            resultSet.getString("candidate_search_state_hash"),
            resultSet.getString("baseline_config_hash"),
            resultSet.getString("candidate_config_hash"),
            jsonSupport.readMap(resultSet.getString("summary_jsonb"), "eval_compare.json_read_failed", "Unable to read eval compare summary"),
            toInstant(resultSet.getTimestamp("created_at")),
            toInstant(resultSet.getTimestamp("updated_at"))
        );
    }

    private List<EvalCompatibilityReason> readReasons(String rawJson) {
        return jsonSupport.readList(
            rawJson,
            EvalCompatibilityReason.class,
            "eval_compare.json_read_failed",
            "Unable to read eval compare compatibility reasons"
        );
    }

    private String writeJson(Object value) {
        return jsonSupport.write(value, "eval_compare.json_write_failed", "Unable to serialize eval compare payload");
    }

    private UUID uuid(String id) {
        return UUID.fromString(id);
    }

    private UUID nullableUuid(String id) {
        return id == null || id.isBlank() ? null : UUID.fromString(id);
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
