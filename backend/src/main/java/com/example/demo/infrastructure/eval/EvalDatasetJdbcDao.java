package com.example.demo.infrastructure.eval;

import com.example.demo.error.ErrorType;
import com.example.demo.error.StorageException;
import com.example.demo.model.eval.EvalCase;
import com.example.demo.model.eval.EvalCaseReview;
import com.example.demo.model.eval.EvalDataset;
import com.example.demo.model.eval.EvalDatasetDetail;
import com.example.demo.model.eval.EvalDatasetSummary;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

final class EvalDatasetJdbcDao {

    private final JdbcTemplate jdbcTemplate;
    private final EvalJdbcJsonMapper jsonMapper;
    private final EvalJdbcRowMappers rowMappers;
    private final EvalCaseJdbcDao caseDao;

    EvalDatasetJdbcDao(
        JdbcTemplate jdbcTemplate,
        EvalJdbcJsonMapper jsonMapper,
        EvalJdbcRowMappers rowMappers,
        EvalCaseJdbcDao caseDao
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.jsonMapper = jsonMapper;
        this.rowMappers = rowMappers;
        this.caseDao = caseDao;
    }

    List<EvalDatasetSummary> findDatasetSummaries() {
        try {
            return jdbcTemplate.query(
                """
                    SELECT d.id,
                           d.dataset_key,
                           d.kind,
                           d.version,
                           d.status,
                           d.name,
                           d.description,
                           d.tags_jsonb,
                           COUNT(c.id) FILTER (WHERE c.active)::int AS case_count,
                           d.created_at,
                           d.updated_at
                    FROM eval_datasets d
                    LEFT JOIN eval_cases c ON c.dataset_id = d.id
                    GROUP BY d.id
                    ORDER BY d.created_at DESC, d.dataset_key ASC, d.version ASC
                    """,
                rowMappers.summary()
            );
        } catch (DataAccessException exception) {
            throw storageFailure("eval_dataset.storage_read_failed", "Unable to load eval datasets", exception);
        }
    }

    Optional<EvalDatasetDetail> findDatasetDetail(String id) {
        try {
            Optional<EvalDataset> dataset = findDatasetById(id);
            if (dataset.isEmpty()) {
                return Optional.empty();
            }
            List<EvalCase> cases = caseDao.findCasesByDatasetId(id);
            List<EvalCaseReview> reviews = caseDao.findReviewsByDatasetId(id);
            return Optional.of(new EvalDatasetDetail(dataset.get(), cases, reviews));
        } catch (DataAccessException exception) {
            throw storageFailure("eval_dataset.storage_read_failed", "Unable to load eval dataset detail", exception);
        }
    }

    EvalDataset saveDataset(EvalDataset dataset) {
        Instant now = Instant.now();
        Instant createdAt = dataset.createdAt() == null ? now : dataset.createdAt();
        Instant updatedAt = dataset.updatedAt() == null ? createdAt : dataset.updatedAt();
        try {
            jdbcTemplate.update(
                """
                    INSERT INTO eval_datasets (
                        id,
                        dataset_key,
                        kind,
                        version,
                        status,
                        name,
                        description,
                        tags_jsonb,
                        created_at,
                        updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
                    ON CONFLICT (id) DO UPDATE
                    SET dataset_key = EXCLUDED.dataset_key,
                        kind = EXCLUDED.kind,
                        version = EXCLUDED.version,
                        status = EXCLUDED.status,
                        name = EXCLUDED.name,
                        description = EXCLUDED.description,
                        tags_jsonb = EXCLUDED.tags_jsonb,
                        updated_at = EXCLUDED.updated_at
                    """,
                uuid(dataset.id()),
                dataset.datasetKey(),
                dataset.kind().name(),
                dataset.version(),
                dataset.status().name(),
                dataset.name(),
                dataset.description(),
                jsonMapper.write(dataset.tags()),
                Timestamp.from(createdAt),
                Timestamp.from(updatedAt)
            );
            return findDatasetById(dataset.id()).orElse(dataset);
        } catch (DataAccessException exception) {
            throw storageFailure("eval_dataset.storage_write_failed", "Unable to persist eval dataset", exception);
        }
    }

    Optional<EvalDataset> findDatasetById(String id) {
        return jdbcTemplate.query(
            """
                SELECT id, dataset_key, kind, version, status, name, description, tags_jsonb, created_at, updated_at
                FROM eval_datasets
                WHERE id = ?
                LIMIT 1
                """,
            rowMappers.dataset(),
            uuid(id)
        ).stream().findFirst();
    }

    boolean isStorageReady() {
        try {
            Boolean ready = jdbcTemplate.queryForObject(
                """
                    SELECT to_regclass('public.eval_datasets') IS NOT NULL
                       AND to_regclass('public.eval_cases') IS NOT NULL
                       AND to_regclass('public.eval_case_reviews') IS NOT NULL
                       AND to_regclass('public.eval_case_revisions') IS NOT NULL
                       AND to_regclass('public.eval_dataset_versions') IS NOT NULL
                       AND to_regclass('public.eval_case_promotions') IS NOT NULL
                    """,
                Boolean.class
            );
            return Boolean.TRUE.equals(ready);
        } catch (DataAccessException exception) {
            throw storageFailure("eval_dataset.storage_read_failed", "Unable to check eval dataset storage readiness", exception);
        }
    }

    private UUID uuid(String id) {
        return UUID.fromString(id);
    }

    private StorageException storageFailure(String code, String message, DataAccessException exception) {
        return new StorageException(ErrorType.STORAGE_FAILURE, code, message, exception);
    }
}
