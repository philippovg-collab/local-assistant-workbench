package com.example.demo.infrastructure.material;

import static com.example.demo.infrastructure.material.MaterialJdbcRowMappers.SEARCH_SYNC_QUEUE_ROW_MAPPER;

import com.example.demo.api.ApiException;
import com.example.demo.service.material.MaterialSearchSyncQueueEntry;
import com.example.demo.service.material.SearchSyncDeliveryState;
import com.example.demo.service.material.SearchSyncOperationType;
import com.example.demo.service.material.port.MaterialSearchSyncQueueRepository;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

final class PostgresMaterialSearchSyncQueueAdapter
    extends PostgresMaterialJdbcSupport
    implements MaterialSearchSyncQueueRepository {

    PostgresMaterialSearchSyncQueueAdapter(
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        super(jdbcTemplate, transactionManager);
    }

    @Override
    public void enqueueMaterialsForSync(Collection<String> materialIds, Instant requestedAt) {
        enqueueMaterialsForSync(materialIds, SearchSyncOperationType.UPSERT, requestedAt);
    }

    @Override
    public void enqueueMaterialsForSync(
        Collection<String> materialIds,
        SearchSyncOperationType operationType,
        Instant requestedAt
    ) {
        if (materialIds == null || materialIds.isEmpty()) {
            return;
        }

        Instant effectiveRequestedAt = requestedAt == null ? Instant.now() : requestedAt;
        SearchSyncOperationType effectiveOperationType = operationType == null
            ? SearchSyncOperationType.UPSERT
            : operationType;
        List<UUID> queueMaterialIds = materialIds.stream()
            .filter(materialId -> materialId != null && !materialId.isBlank())
            .distinct()
            .map(UUID::fromString)
            .toList();
        if (queueMaterialIds.isEmpty()) {
            return;
        }

        try {
            jdbcTemplate.batchUpdate(
                """
                    INSERT INTO material_search_sync_queue (
                        material_id,
                        operation_type,
                        delivery_state,
                        attempt_count,
                        next_attempt_at,
                        claimed_at,
                        claimed_intent_version,
                        last_error_code,
                        last_error_message,
                        requested_at,
                        created_at,
                        updated_at
                    ) VALUES (?, ?, 'PENDING', 0, NULL, NULL, NULL, NULL, NULL, ?, ?, ?)
                    ON CONFLICT (material_id) DO UPDATE
                    SET operation_type = EXCLUDED.operation_type,
                        delivery_state = CASE
                            WHEN material_search_sync_queue.delivery_state = 'IN_PROGRESS'
                                THEN material_search_sync_queue.delivery_state
                            ELSE 'PENDING'
                        END,
                        attempt_count = CASE
                            WHEN material_search_sync_queue.delivery_state = 'IN_PROGRESS'
                                THEN material_search_sync_queue.attempt_count
                            ELSE 0
                        END,
                        next_attempt_at = CASE
                            WHEN material_search_sync_queue.delivery_state = 'IN_PROGRESS'
                                THEN material_search_sync_queue.next_attempt_at
                            ELSE NULL
                        END,
                        claimed_at = CASE
                            WHEN material_search_sync_queue.delivery_state = 'IN_PROGRESS'
                                THEN material_search_sync_queue.claimed_at
                            ELSE NULL
                        END,
                        claimed_intent_version = CASE
                            WHEN material_search_sync_queue.delivery_state = 'IN_PROGRESS'
                                THEN material_search_sync_queue.claimed_intent_version
                            ELSE NULL
                        END,
                        last_error_code = CASE
                            WHEN material_search_sync_queue.delivery_state = 'IN_PROGRESS'
                                THEN material_search_sync_queue.last_error_code
                            ELSE NULL
                        END,
                        last_error_message = CASE
                            WHEN material_search_sync_queue.delivery_state = 'IN_PROGRESS'
                                THEN material_search_sync_queue.last_error_message
                            ELSE NULL
                        END,
                        intent_version = material_search_sync_queue.intent_version + 1,
                        requested_at = GREATEST(material_search_sync_queue.requested_at, EXCLUDED.requested_at),
                        updated_at = EXCLUDED.updated_at
                    """,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement preparedStatement, int index) throws SQLException {
                        Timestamp timestamp = Timestamp.from(effectiveRequestedAt);
                        preparedStatement.setObject(1, queueMaterialIds.get(index));
                        preparedStatement.setString(2, effectiveOperationType.name());
                        preparedStatement.setTimestamp(3, timestamp);
                        preparedStatement.setTimestamp(4, timestamp);
                        preparedStatement.setTimestamp(5, timestamp);
                    }

                    @Override
                    public int getBatchSize() {
                        return queueMaterialIds.size();
                    }
                }
            );
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "material.storage_write_failed",
                "Unable to enqueue material search sync queue entries in PostgreSQL",
                exception
            );
        }
    }

    @Override
    public List<MaterialSearchSyncQueueEntry> findAllSearchSyncEntries() {
        try {
            return jdbcTemplate.query(
                """
                    SELECT
                        material_id,
                        operation_type,
                        delivery_state,
                        attempt_count,
                        next_attempt_at,
                        claimed_at,
                        last_error_code,
                        last_error_message,
                        requested_at,
                        created_at,
                        updated_at
                    FROM material_search_sync_queue
                    ORDER BY requested_at ASC, material_id ASC
                    """,
                SEARCH_SYNC_QUEUE_ROW_MAPPER
            );
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "material.storage_read_failed",
                "Unable to load material search sync queue entries from PostgreSQL",
                exception
            );
        }
    }

    @Override
    public int requeueFailedSearchSyncEntries(Instant now) {
        try {
            return jdbcTemplate.update(
                """
                    UPDATE material_search_sync_queue
                    SET delivery_state = 'PENDING',
                        attempt_count = 0,
                        next_attempt_at = NULL,
                        claimed_at = NULL,
                        claimed_intent_version = NULL,
                        last_error_code = NULL,
                        last_error_message = NULL,
                        updated_at = ?
                    WHERE delivery_state = 'FAILED'
                    """,
                Timestamp.from(now)
            );
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "material.storage_write_failed",
                "Unable to requeue failed material search sync queue entries in PostgreSQL",
                exception
            );
        }
    }

    @Override
    public void resetExpiredSearchSyncClaims(Instant staleBefore, Instant now) {
        try {
            jdbcTemplate.update(
                """
                    UPDATE material_search_sync_queue
                    SET delivery_state = 'PENDING',
                        attempt_count = CASE
                            WHEN intent_version > COALESCE(claimed_intent_version, intent_version)
                                THEN 0
                            ELSE attempt_count
                        END,
                        next_attempt_at = NULL,
                        claimed_at = NULL,
                        claimed_intent_version = NULL,
                        last_error_code = CASE
                            WHEN intent_version > COALESCE(claimed_intent_version, intent_version)
                                THEN NULL
                            ELSE last_error_code
                        END,
                        last_error_message = CASE
                            WHEN intent_version > COALESCE(claimed_intent_version, intent_version)
                                THEN NULL
                            ELSE last_error_message
                        END,
                        updated_at = ?
                    WHERE delivery_state = 'IN_PROGRESS'
                      AND claimed_at IS NOT NULL
                      AND claimed_at < ?
                    """,
                Timestamp.from(now),
                Timestamp.from(staleBefore)
            );
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "material.storage_write_failed",
                "Unable to recover stale search sync queue claims in PostgreSQL",
                exception
            );
        }
    }

    @Override
    public List<MaterialSearchSyncQueueEntry> claimNextSearchSyncBatch(Instant now, int limit) {
        if (limit <= 0) {
            return List.of();
        }

        try {
            return transactionTemplate.execute(transactionStatus -> {
                List<UUID> materialIds = jdbcTemplate.query(
                    """
                    SELECT material_id
                    FROM material_search_sync_queue
                        WHERE delivery_state = 'PENDING'
                          AND (next_attempt_at IS NULL OR next_attempt_at <= ?)
                        ORDER BY COALESCE(next_attempt_at, requested_at) ASC, requested_at ASC, material_id ASC
                        LIMIT ?
                        FOR UPDATE SKIP LOCKED
                        """,
                    (resultSet, rowNum) -> resultSet.getObject("material_id", UUID.class),
                    Timestamp.from(now),
                    limit
                );
                if (materialIds.isEmpty()) {
                    return List.of();
                }

                jdbcTemplate.batchUpdate(
                    """
                        UPDATE material_search_sync_queue
                        SET delivery_state = 'IN_PROGRESS',
                            attempt_count = attempt_count + 1,
                            claimed_at = ?,
                            claimed_intent_version = intent_version,
                            updated_at = ?
                        WHERE material_id = ?
                        """,
                    new BatchPreparedStatementSetter() {
                        @Override
                        public void setValues(PreparedStatement preparedStatement, int index) throws SQLException {
                            preparedStatement.setTimestamp(1, Timestamp.from(now));
                            preparedStatement.setTimestamp(2, Timestamp.from(now));
                            preparedStatement.setObject(3, materialIds.get(index));
                        }

                        @Override
                        public int getBatchSize() {
                            return materialIds.size();
                        }
                    }
                );

                return loadSearchSyncQueueEntriesByMaterialIds(materialIds);
            });
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "material.storage_write_failed",
                "Unable to claim pending material search sync queue entries from PostgreSQL",
                exception
            );
        }
    }

    @Override
    public boolean hasPendingSearchSyncEvents(Instant now) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                """
                    SELECT COUNT(*)
                    FROM material_search_sync_queue
                    WHERE delivery_state = 'PENDING'
                      AND (next_attempt_at IS NULL OR next_attempt_at <= ?)
                    """,
                Integer.class,
                Timestamp.from(now)
            );
            return count != null && count > 0;
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "material.storage_read_failed",
                "Unable to inspect pending material search sync queue entries in PostgreSQL",
                exception
            );
        }
    }

    @Override
    public void completeSearchSyncEntry(String materialId, Instant claimedAt, Instant now) {
        if (materialId == null || materialId.isBlank() || claimedAt == null) {
            return;
        }

        try {
            int requeued = jdbcTemplate.update(
                """
                    UPDATE material_search_sync_queue
                    SET delivery_state = 'PENDING',
                        attempt_count = 0,
                        next_attempt_at = NULL,
                        claimed_at = NULL,
                        claimed_intent_version = NULL,
                        last_error_code = NULL,
                        last_error_message = NULL,
                        updated_at = ?
                    WHERE material_id = ?
                      AND delivery_state = 'IN_PROGRESS'
                      AND claimed_at = ?
                      AND intent_version > COALESCE(claimed_intent_version, intent_version)
                    """,
                Timestamp.from(now),
                UUID.fromString(materialId),
                Timestamp.from(claimedAt)
            );
            if (requeued > 0) {
                return;
            }

            jdbcTemplate.update(
                """
                    DELETE FROM material_search_sync_queue
                    WHERE material_id = ?
                      AND delivery_state = 'IN_PROGRESS'
                      AND claimed_at = ?
                    """,
                UUID.fromString(materialId),
                Timestamp.from(claimedAt)
            );
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "material.storage_write_failed",
                "Unable to complete material search sync queue entry in PostgreSQL",
                exception
            );
        }
    }

    @Override
    public void markSearchSyncEntryForRetry(
        String materialId,
        Instant claimedAt,
        String errorCode,
        String errorMessage,
        Instant now,
        Instant nextAttemptAt
    ) {
        updateClaimedSearchSyncQueueEntry(
            materialId,
            claimedAt,
            SearchSyncDeliveryState.PENDING,
            nextAttemptAt,
            now,
            errorCode,
            errorMessage
        );
    }

    @Override
    public void markSearchSyncEntryFailed(
        String materialId,
        Instant claimedAt,
        String errorCode,
        String errorMessage,
        Instant now
    ) {
        updateClaimedSearchSyncQueueEntry(
            materialId,
            claimedAt,
            SearchSyncDeliveryState.FAILED,
            null,
            now,
            errorCode,
            errorMessage
        );
    }

    @Override
    public MaterialSearchSyncQueueRepository.SearchSyncQueueSnapshot getSearchSyncQueueSnapshot() {
        try {
            return jdbcTemplate.queryForObject(
                """
                    SELECT
                        COUNT(*) FILTER (WHERE delivery_state = 'PENDING') AS pending_count,
                        COUNT(*) FILTER (WHERE delivery_state = 'IN_PROGRESS') AS in_progress_count,
                        COUNT(*) FILTER (WHERE delivery_state = 'FAILED') AS failed_count,
                        MIN(next_attempt_at) FILTER (WHERE delivery_state = 'PENDING' AND next_attempt_at IS NOT NULL) AS next_retry_at,
                        MIN(requested_at) FILTER (
                            WHERE delivery_state IN ('PENDING', 'IN_PROGRESS')
                        ) AS oldest_outstanding_at
                    FROM material_search_sync_queue
                    """,
                (resultSet, rowNum) -> new MaterialSearchSyncQueueRepository.SearchSyncQueueSnapshot(
                    resultSet.getInt("pending_count"),
                    resultSet.getInt("in_progress_count"),
                    resultSet.getInt("failed_count"),
                    PostgresMaterialJdbcSupport.toInstantOrNull(resultSet.getTimestamp("next_retry_at")),
                    PostgresMaterialJdbcSupport.toInstantOrNull(resultSet.getTimestamp("oldest_outstanding_at"))
                )
            );
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "material.storage_read_failed",
                "Unable to inspect material search sync reconcile queue state in PostgreSQL",
                exception
            );
        }
    }

    private List<MaterialSearchSyncQueueEntry> loadSearchSyncQueueEntriesByMaterialIds(List<UUID> materialIds) {
        if (materialIds == null || materialIds.isEmpty()) {
            return List.of();
        }

        String placeholders = String.join(", ", Collections.nCopies(materialIds.size(), "?"));
        return jdbcTemplate.query(
            """
                SELECT
                    material_id,
                    operation_type,
                    delivery_state,
                    attempt_count,
                    next_attempt_at,
                    claimed_at,
                    last_error_code,
                    last_error_message,
                    requested_at,
                    created_at,
                    updated_at
                FROM material_search_sync_queue
                WHERE material_id IN (
                """
                + placeholders
                + """
                )
                ORDER BY COALESCE(next_attempt_at, requested_at) ASC, requested_at ASC, material_id ASC
                """,
            SEARCH_SYNC_QUEUE_ROW_MAPPER,
            materialIds.toArray()
        );
    }

    private void updateClaimedSearchSyncQueueEntry(
        String materialId,
        Instant claimedAt,
        SearchSyncDeliveryState deliveryState,
        Instant nextAttemptAt,
        Instant now,
        String errorCode,
        String errorMessage
    ) {
        if (materialId == null || materialId.isBlank() || claimedAt == null) {
            return;
        }

        try {
            jdbcTemplate.update(
                """
                    UPDATE material_search_sync_queue
                    SET delivery_state = CASE
                            WHEN intent_version > COALESCE(claimed_intent_version, intent_version)
                                THEN 'PENDING'
                            ELSE ?
                        END,
                        attempt_count = CASE
                            WHEN intent_version > COALESCE(claimed_intent_version, intent_version)
                                THEN 0
                            ELSE attempt_count
                        END,
                        next_attempt_at = CASE
                            WHEN intent_version > COALESCE(claimed_intent_version, intent_version)
                                THEN NULL
                            ELSE ?::timestamptz
                        END,
                        claimed_at = NULL,
                        claimed_intent_version = NULL,
                        last_error_code = CASE
                            WHEN intent_version > COALESCE(claimed_intent_version, intent_version)
                                THEN NULL
                            ELSE ?
                        END,
                        last_error_message = CASE
                            WHEN intent_version > COALESCE(claimed_intent_version, intent_version)
                                THEN NULL
                            ELSE ?
                        END,
                        updated_at = ?
                    WHERE material_id = ?
                      AND delivery_state = 'IN_PROGRESS'
                      AND claimed_at = ?
                    """,
                deliveryState.name(),
                nextAttemptAt == null ? null : Timestamp.from(nextAttemptAt),
                errorCode,
                errorMessage,
                Timestamp.from(now),
                UUID.fromString(materialId),
                Timestamp.from(claimedAt)
            );
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "material.storage_write_failed",
                "Unable to update material search sync queue entry in PostgreSQL",
                exception
            );
        }
    }
}
