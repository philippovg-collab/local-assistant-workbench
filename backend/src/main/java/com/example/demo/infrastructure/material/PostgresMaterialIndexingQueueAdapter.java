package com.example.demo.infrastructure.material;

import static com.example.demo.infrastructure.material.MaterialJdbcRowMappers.MATERIAL_ROW_MAPPER;

import com.example.demo.error.ErrorType;
import com.example.demo.error.StorageException;
import com.example.demo.model.MaterialIndexingStatus;
import com.example.demo.service.material.MaterialIndexingLease;
import com.example.demo.service.material.StoredEmbeddedMaterialChunk;
import com.example.demo.service.material.StoredMaterialRecord;
import com.example.demo.service.material.port.MaterialIndexingQueueRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

final class PostgresMaterialIndexingQueueAdapter extends PostgresMaterialJdbcSupport implements MaterialIndexingQueueRepository {

    PostgresMaterialIndexingQueueAdapter(
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        super(jdbcTemplate, transactionManager);
    }

    @Override
    public StoredMaterialRecord markIndexingPending(
        String materialId,
        String reasonCode,
        String reasonMessage,
        Instant updatedAt
    ) {
        updateIndexingState(materialId, MaterialIndexingStatus.PENDING, reasonCode, reasonMessage, updatedAt, updatedAt, null);
        return recordDao.findById(materialId).orElseThrow(() -> new StorageException(
            ErrorType.NOT_FOUND,
            "material.not_found",
            "Material '" + materialId + "' does not exist"
        ));
    }

    @Override
    public void markIndexingReady(
        String materialId,
        List<StoredEmbeddedMaterialChunk> chunks,
        MaterialIndexingStatus finalStatus,
        String reasonCode,
        String reasonMessage,
        Instant updatedAt
    ) {
        try {
            transactionTemplate.executeWithoutResult(transactionStatus -> {
                if (!recordDao.lockMaterialIfPresent(materialId)) {
                    return;
                }
                jdbcTemplate.update("DELETE FROM material_chunks WHERE material_id = ?", UUID.fromString(materialId));
                chunkDao.insertEmbeddedChunks(materialId, chunks);
                updateIndexingState(materialId, finalStatus, reasonCode, reasonMessage, updatedAt, null, null);
            });
        } catch (DataAccessException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "material.storage_write_failed",
                "Unable to finalize material indexing in PostgreSQL",
                exception
            );
        }
    }

    @Override
    public void markIndexingFailed(String materialId, String code, String message, Instant updatedAt) {
        updateIndexingState(materialId, MaterialIndexingStatus.FAILED, code, message, updatedAt, null, null);
    }

    @Override
    public void rescheduleIndexing(String materialId, String code, String message, Instant updatedAt, Instant nextRetryAt) {
        updateIndexingState(materialId, MaterialIndexingStatus.PENDING, code, message, updatedAt, nextRetryAt, null);
    }

    @Override
    public int markActiveMaterialsIndexingPending(String reasonCode, String reasonMessage, Instant updatedAt) {
        try {
            return jdbcTemplate.update(
                """
                    UPDATE materials
                    SET indexing_status = 'PENDING',
                        status_reason_code = ?,
                        status_reason_message = ?,
                        next_retry_at = NULL,
                        claimed_at = NULL,
                        updated_at = ?
                    WHERE version_state = 'ACTIVE'
                    """,
                reasonCode,
                reasonMessage,
                Timestamp.from(updatedAt)
            );
        } catch (DataAccessException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "material.storage_write_failed",
                "Unable to enqueue active materials for embedding reindex in PostgreSQL",
                exception
            );
        }
    }

    @Override
    public void resetExpiredIndexingClaims(Instant staleBefore, Instant now) {
        try {
            jdbcTemplate.update(
                """
                    UPDATE materials
                    SET indexing_status = 'PENDING',
                        claimed_at = NULL,
                        updated_at = ?
                    WHERE indexing_status = 'IN_PROGRESS'
                      AND version_state = 'ACTIVE'
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
                "Unable to recover stale indexing claims in PostgreSQL",
                exception
            );
        }
    }

    @Override
    public Optional<MaterialIndexingLease> claimNextIndexing(Instant now) {
        try {
            return transactionTemplate.execute(transactionStatus -> {
                List<ClaimCandidate> candidates = jdbcTemplate.query(
                    """
                        SELECT
                            """ + PostgresMaterialSql.MATERIAL_COLUMNS + """
                        FROM materials
                        WHERE indexing_status = 'PENDING'
                          AND version_state = 'ACTIVE'
                          AND (next_retry_at IS NULL OR next_retry_at <= ?)
                        ORDER BY COALESCE(next_retry_at, created_at) ASC, created_at ASC
                        LIMIT 1
                        FOR UPDATE SKIP LOCKED
                    """,
                    (resultSet, rowNum) -> new ClaimCandidate(
                        MATERIAL_ROW_MAPPER.mapRow(resultSet, rowNum),
                        resultSet.getInt("indexing_attempts")
                    ),
                    Timestamp.from(now)
                );

                if (candidates.isEmpty()) {
                    return Optional.empty();
                }

                ClaimCandidate candidate = candidates.getFirst();
                jdbcTemplate.update(
                    """
                        UPDATE materials
                        SET indexing_status = 'IN_PROGRESS',
                            indexing_attempts = indexing_attempts + 1,
                            claimed_at = ?,
                            updated_at = ?
                        WHERE id = ?
                        """,
                    Timestamp.from(now),
                    Timestamp.from(now),
                    UUID.fromString(candidate.record().id())
                );

                return Optional.of(new MaterialIndexingLease(candidate.record(), candidate.attempts() + 1));
            });
        } catch (DataAccessException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "material.storage_write_failed",
                "Unable to claim the next indexing job from PostgreSQL",
                exception
            );
        }
    }

    @Override
    public boolean hasPendingIndexing(Instant now) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                """
                    SELECT COUNT(*)
                    FROM materials
                    WHERE indexing_status = 'PENDING'
                      AND version_state = 'ACTIVE'
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
                "Unable to inspect pending indexing jobs in PostgreSQL",
                exception
            );
        }
    }

    @Override
    public MaterialIndexingQueueRepository.IndexingQueueSnapshot getIndexingQueueSnapshot() {
        try {
            return jdbcTemplate.queryForObject(
                """
                    SELECT
                        COUNT(*) FILTER (WHERE version_state = 'ACTIVE' AND indexing_status = 'PENDING') AS pending_count,
                        COUNT(*) FILTER (WHERE version_state = 'ACTIVE' AND indexing_status = 'IN_PROGRESS') AS in_progress_count,
                        COUNT(*) FILTER (WHERE version_state = 'ACTIVE' AND indexing_status = 'FAILED') AS failed_count,
                        MIN(next_retry_at) FILTER (WHERE version_state = 'ACTIVE' AND indexing_status = 'PENDING' AND next_retry_at IS NOT NULL) AS next_retry_at,
                        MIN(updated_at) FILTER (WHERE version_state = 'ACTIVE' AND indexing_status = 'PENDING') AS oldest_pending_at,
                        MIN(COALESCE(claimed_at, updated_at)) FILTER (WHERE version_state = 'ACTIVE' AND indexing_status = 'IN_PROGRESS') AS oldest_in_progress_at
                    FROM materials
                    """,
                (resultSet, rowNum) -> new MaterialIndexingQueueRepository.IndexingQueueSnapshot(
                    resultSet.getInt("pending_count"),
                    resultSet.getInt("in_progress_count"),
                    resultSet.getInt("failed_count"),
                    PostgresMaterialJdbcSupport.toInstantOrNull(resultSet.getTimestamp("next_retry_at")),
                    PostgresMaterialJdbcSupport.toInstantOrNull(resultSet.getTimestamp("oldest_pending_at")),
                    PostgresMaterialJdbcSupport.toInstantOrNull(resultSet.getTimestamp("oldest_in_progress_at"))
                )
            );
        } catch (DataAccessException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "material.storage_read_failed",
                "Unable to inspect indexing queue state in PostgreSQL",
                exception
            );
        }
    }

    private void updateIndexingState(
        String materialId,
        MaterialIndexingStatus status,
        String reasonCode,
        String reasonMessage,
        Instant updatedAt,
        Instant nextRetryAt,
        Instant claimedAt
    ) {
        try {
            jdbcTemplate.update(
                """
                    UPDATE materials
                    SET indexing_status = ?,
                        status_reason_code = ?,
                        status_reason_message = ?,
                        next_retry_at = ?,
                        claimed_at = ?,
                        updated_at = ?
                    WHERE id = ?
                    """,
                status.name(),
                reasonCode,
                reasonMessage,
                nextRetryAt == null ? null : Timestamp.from(nextRetryAt),
                claimedAt == null ? null : Timestamp.from(claimedAt),
                Timestamp.from(updatedAt),
                UUID.fromString(materialId)
            );
        } catch (DataAccessException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "material.storage_write_failed",
                "Unable to update material indexing status in PostgreSQL",
                exception
            );
        }
    }

    private record ClaimCandidate(
        StoredMaterialRecord record,
        int attempts
    ) {
    }
}
