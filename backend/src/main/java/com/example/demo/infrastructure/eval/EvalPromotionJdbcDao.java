package com.example.demo.infrastructure.eval;

import com.example.demo.error.ErrorType;
import com.example.demo.error.StorageException;
import com.example.demo.model.eval.EvalCasePromotion;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

final class EvalPromotionJdbcDao {

    private final JdbcTemplate jdbcTemplate;
    private final EvalJdbcJsonMapper jsonMapper;

    EvalPromotionJdbcDao(JdbcTemplate jdbcTemplate, EvalJdbcJsonMapper jsonMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.jsonMapper = jsonMapper;
    }

    EvalCasePromotion savePromotion(EvalCasePromotion promotion) {
        Instant createdAt = promotion.createdAt() == null ? Instant.now() : promotion.createdAt();
        try {
            jdbcTemplate.update(
                """
                    INSERT INTO eval_case_promotions (
                        id,
                        source_case_id,
                        source_case_revision,
                        target_dataset_id,
                        target_case_id,
                        target_case_revision,
                        target_dataset_version_id,
                        promoted_by,
                        note,
                        metadata_jsonb,
                        created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                    """,
                uuid(promotion.id()),
                uuid(promotion.sourceCaseId()),
                promotion.sourceCaseRevision(),
                uuid(promotion.targetDatasetId()),
                uuid(promotion.targetCaseId()),
                promotion.targetCaseRevision(),
                nullableUuid(promotion.targetDatasetVersionId()),
                promotion.promotedBy(),
                promotion.note(),
                jsonMapper.write(promotion.metadata()),
                Timestamp.from(createdAt)
            );
            return promotion;
        } catch (DataAccessException exception) {
            throw storageFailure("eval_case_promotion.storage_write_failed", "Unable to persist eval case promotion", exception);
        }
    }

    private UUID uuid(String id) {
        return UUID.fromString(id);
    }

    private UUID nullableUuid(String id) {
        return id == null || id.isBlank() ? null : UUID.fromString(id);
    }

    private StorageException storageFailure(String code, String message, DataAccessException exception) {
        return new StorageException(ErrorType.STORAGE_FAILURE, code, message, exception);
    }
}
