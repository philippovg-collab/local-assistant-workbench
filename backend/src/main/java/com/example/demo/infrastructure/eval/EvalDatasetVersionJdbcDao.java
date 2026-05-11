package com.example.demo.infrastructure.eval;

import com.example.demo.error.ErrorType;
import com.example.demo.error.StorageException;
import com.example.demo.model.eval.EvalDatasetVersion;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

final class EvalDatasetVersionJdbcDao {

    private final JdbcTemplate jdbcTemplate;
    private final EvalJdbcJsonMapper jsonMapper;
    private final EvalJdbcRowMappers rowMappers;

    EvalDatasetVersionJdbcDao(JdbcTemplate jdbcTemplate, EvalJdbcJsonMapper jsonMapper, EvalJdbcRowMappers rowMappers) {
        this.jdbcTemplate = jdbcTemplate;
        this.jsonMapper = jsonMapper;
        this.rowMappers = rowMappers;
    }

    EvalDatasetVersion saveDatasetVersion(EvalDatasetVersion version) {
        Instant createdAt = version.createdAt() == null ? Instant.now() : version.createdAt();
        try {
            jdbcTemplate.update(
                """
                    INSERT INTO eval_dataset_versions (
                        id,
                        dataset_id,
                        version,
                        dataset_hash,
                        case_count,
                        case_revision_refs_jsonb,
                        created_by,
                        note,
                        created_at
                    ) VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)
                    ON CONFLICT (dataset_id, version) DO UPDATE
                    SET dataset_hash = EXCLUDED.dataset_hash,
                        case_count = EXCLUDED.case_count,
                        case_revision_refs_jsonb = EXCLUDED.case_revision_refs_jsonb,
                        note = EXCLUDED.note
                    """,
                uuid(version.id()),
                uuid(version.datasetId()),
                version.version(),
                version.datasetHash(),
                version.caseCount(),
                jsonMapper.write(version.caseRevisionRefs()),
                version.createdBy(),
                version.note(),
                Timestamp.from(createdAt)
            );
            return findDatasetVersions(version.datasetId()).stream()
                .filter(candidate -> candidate.version().equals(version.version()))
                .findFirst()
                .orElse(version);
        } catch (DataAccessException exception) {
            throw storageFailure("eval_dataset_version.storage_write_failed", "Unable to persist eval dataset version", exception);
        }
    }

    List<EvalDatasetVersion> findDatasetVersions(String datasetId) {
        try {
            return jdbcTemplate.query(
                """
                    SELECT id,
                           dataset_id,
                           version,
                           dataset_hash,
                           case_count,
                           case_revision_refs_jsonb,
                           created_by,
                           note,
                           created_at
                    FROM eval_dataset_versions
                    WHERE dataset_id = ?
                    ORDER BY created_at DESC, version DESC
                    """,
                rowMappers.datasetVersion(),
                uuid(datasetId)
            );
        } catch (DataAccessException exception) {
            throw storageFailure("eval_dataset_version.storage_read_failed", "Unable to load eval dataset versions", exception);
        }
    }

    Optional<EvalDatasetVersion> findDatasetVersion(String datasetId, String version) {
        try {
            return jdbcTemplate.query(
                """
                    SELECT id,
                           dataset_id,
                           version,
                           dataset_hash,
                           case_count,
                           case_revision_refs_jsonb,
                           created_by,
                           note,
                           created_at
                    FROM eval_dataset_versions
                    WHERE dataset_id = ?
                      AND version = ?
                    LIMIT 1
                    """,
                rowMappers.datasetVersion(),
                uuid(datasetId),
                version
            ).stream().findFirst();
        } catch (DataAccessException exception) {
            throw storageFailure("eval_dataset_version.storage_read_failed", "Unable to load eval dataset version", exception);
        }
    }

    private UUID uuid(String id) {
        return UUID.fromString(id);
    }

    private StorageException storageFailure(String code, String message, DataAccessException exception) {
        return new StorageException(ErrorType.STORAGE_FAILURE, code, message, exception);
    }
}
