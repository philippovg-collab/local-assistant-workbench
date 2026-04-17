package com.example.demo.infrastructure.material;

import com.example.demo.api.ApiException;
import com.example.demo.model.MaterialIndexingStatus;
import com.example.demo.model.MaterialVersionState;
import com.pgvector.PGvector;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class PostgresMaterialRepository implements MaterialCatalogRepository, MaterialSearchRepository, MaterialIndexingQueueRepository {

    private static final String MATERIAL_COLUMNS = """
        id,
        title,
        source_type,
        original_file_name,
        media_type,
        content,
        normalized_content,
        content_hash,
        source_key,
        extractor,
        ocr_used,
        page_count,
        indexing_status,
        version_state,
        status_reason_code,
        status_reason_message,
        indexing_attempts,
        next_retry_at,
        superseded_by_material_id,
        supersede_reason,
        created_at,
        updated_at
        """;

    private static final RowMapper<StoredMaterialRecord> MATERIAL_ROW_MAPPER = (resultSet, rowNum) -> new StoredMaterialRecord(
        resultSet.getObject("id").toString(),
        resultSet.getString("title"),
        resultSet.getString("source_type"),
        resultSet.getString("original_file_name"),
        resultSet.getString("media_type"),
        resultSet.getString("content"),
        resultSet.getString("normalized_content"),
        resultSet.getString("content_hash"),
        resultSet.getString("source_key"),
        resultSet.getString("extractor"),
        resultSet.getBoolean("ocr_used"),
        resultSet.getObject("page_count", Integer.class),
        List.of(),
        MaterialIndexingStatus.valueOf(resultSet.getString("indexing_status")),
        MaterialVersionState.valueOf(resultSet.getString("version_state")),
        resultSet.getString("status_reason_code"),
        resultSet.getString("status_reason_message"),
        toInstant(resultSet.getTimestamp("created_at")),
        toInstant(resultSet.getTimestamp("updated_at")),
        resultSet.getInt("indexing_attempts"),
        toInstantOrNull(resultSet.getTimestamp("next_retry_at")),
        resultSet.getString("superseded_by_material_id"),
        resultSet.getString("supersede_reason")
    );

    private static final RowMapper<MaterialChunkSearchMatch> CHUNK_SEARCH_ROW_MAPPER = (resultSet, rowNum) -> new MaterialChunkSearchMatch(
        resultSet.getObject("material_id").toString(),
        resultSet.getInt("chunk_index"),
        resultSet.getString("title"),
        resultSet.getString("chunk_text"),
        resultSet.getObject("page", Integer.class),
        resultSet.getString("extractor"),
        resultSet.getBoolean("ocr_used"),
        resultSet.getObject("semantic_distance", Double.class),
        resultSet.getObject("lexical_score", Double.class)
    );

    private static final RowMapper<StoredMaterialChunk> RAW_CHUNK_ROW_MAPPER = (resultSet, rowNum) -> new StoredMaterialChunk(
        resultSet.getInt("chunk_index"),
        resultSet.getString("chunk_text"),
        List.of(),
        resultSet.getObject("page", Integer.class),
        resultSet.getString("extractor"),
        resultSet.getBoolean("ocr_used")
    );

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public PostgresMaterialRepository(
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public List<StoredMaterialRecord> findAll() {
        return jdbcTemplate.query(
            "SELECT " + MATERIAL_COLUMNS + " FROM materials ORDER BY created_at DESC",
            MATERIAL_ROW_MAPPER
        );
    }

    @Override
    public Optional<StoredMaterialRecord> findById(String id) {
        List<StoredMaterialRecord> records = jdbcTemplate.query(
            "SELECT " + MATERIAL_COLUMNS + " FROM materials WHERE id = ? LIMIT 1",
            MATERIAL_ROW_MAPPER,
            UUID.fromString(id)
        );
        return records.stream().findFirst();
    }

    @Override
    public Optional<StoredMaterialRecord> findByContentHash(String contentHash) {
        List<StoredMaterialRecord> records = jdbcTemplate.query(
            "SELECT " + MATERIAL_COLUMNS + " FROM materials WHERE content_hash = ? LIMIT 1",
            MATERIAL_ROW_MAPPER,
            contentHash
        );
        return records.stream().findFirst();
    }

    @Override
    public StoredMaterialRecord save(StoredMaterialRecord record, List<StoredMaterialChunk> chunks) {
        try {
            return transactionTemplate.execute(status -> {
                insertMaterial(record);
                insertRawChunks(record.id(), chunks);
                return record;
            });
        } catch (DuplicateKeyException exception) {
            return findByContentHash(record.contentHash())
                .orElseThrow(() -> new ApiException(
                    HttpStatus.CONFLICT,
                    "material.duplicate_conflict",
                    "Material already exists but could not be reloaded after a duplicate write"
                ));
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "material.storage_write_failed",
                "Unable to persist material in PostgreSQL",
                exception
            );
        }
    }

    @Override
    public List<StoredMaterialChunk> findChunks(String materialId) {
        try {
            return jdbcTemplate.query(
                """
                    SELECT
                        chunk_index,
                        chunk_text,
                        page,
                        extractor,
                        ocr_used
                    FROM material_chunks
                    WHERE material_id = ?
                    ORDER BY chunk_index ASC
                    """,
                RAW_CHUNK_ROW_MAPPER,
                UUID.fromString(materialId)
            );
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "material.storage_read_failed",
                "Unable to load material chunks from PostgreSQL",
                exception
            );
        }
    }

    @Override
    public List<StoredMaterialRecord> findAllBySourceKey(String sourceKey) {
        try {
            return jdbcTemplate.query(
                """
                    SELECT
                        """
                    + MATERIAL_COLUMNS
                    + """
                    FROM materials
                    WHERE source_key = ?
                    ORDER BY updated_at DESC, created_at DESC
                    """,
                MATERIAL_ROW_MAPPER,
                sourceKey
            );
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "material.storage_read_failed",
                "Unable to load material lineage from PostgreSQL",
                exception
            );
        }
    }

    @Override
    public void delete(String id) {
        try {
            jdbcTemplate.update("DELETE FROM materials WHERE id = ?", UUID.fromString(id));
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "material.storage_delete_failed",
                "Unable to delete material from PostgreSQL",
                exception
            );
        }
    }

    @Override
    public int countMaterials() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM materials", Integer.class);
        return count == null ? 0 : count;
    }

    @Override
    public int countActiveMaterials() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM materials WHERE version_state = 'ACTIVE'",
            Integer.class
        );
        return count == null ? 0 : count;
    }

    @Override
    public int countReadyMaterials() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM materials WHERE indexing_status IN ('READY', 'PARTIAL_READY') AND version_state = 'ACTIVE'",
            Integer.class
        );
        return count == null ? 0 : count;
    }

    @Override
    public void supersedeActiveVersions(
        String sourceKey,
        String activeMaterialId,
        String excludeContentHash,
        String supersedeReason,
        Instant updatedAt
    ) {
        try {
            jdbcTemplate.update(
                """
                    UPDATE materials
                    SET version_state = 'SUPERSEDED',
                        superseded_by_material_id = ?,
                        supersede_reason = ?,
                        updated_at = ?
                    WHERE source_key = ?
                      AND version_state = 'ACTIVE'
                      AND content_hash <> ?
                    """,
                UUID.fromString(activeMaterialId),
                supersedeReason,
                Timestamp.from(updatedAt),
                sourceKey,
                excludeContentHash
            );
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "material.storage_write_failed",
                "Unable to supersede older material versions in PostgreSQL",
                exception
            );
        }
    }

    @Override
    public Optional<StoredMaterialRecord> findLatestBySourceKeyAndVersionState(String sourceKey, MaterialVersionState versionState) {
        try {
            List<StoredMaterialRecord> records = jdbcTemplate.query(
                """
                    SELECT
                        """
                    + MATERIAL_COLUMNS
                    + """
                     FROM materials
                     WHERE source_key = ?
                       AND version_state = ?
                     ORDER BY updated_at DESC, created_at DESC
                     LIMIT 1
                    """,
                MATERIAL_ROW_MAPPER,
                sourceKey,
                versionState.name()
            );
            return records.stream().findFirst();
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "material.storage_read_failed",
                "Unable to load material lineage from PostgreSQL",
                exception
            );
        }
    }

    @Override
    public StoredMaterialRecord updateVersionState(
        String materialId,
        MaterialVersionState versionState,
        String supersededByMaterialId,
        String supersedeReason,
        Instant updatedAt
    ) {
        try {
            jdbcTemplate.update(
                """
                    UPDATE materials
                    SET version_state = ?,
                        superseded_by_material_id = ?,
                        supersede_reason = ?,
                        updated_at = ?
                    WHERE id = ?
                    """,
                versionState.name(),
                supersededByMaterialId == null ? null : UUID.fromString(supersededByMaterialId),
                supersedeReason,
                Timestamp.from(updatedAt),
                UUID.fromString(materialId)
            );
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "material.storage_write_failed",
                "Unable to update material version state in PostgreSQL",
                exception
            );
        }

        return findById(materialId).orElseThrow(() -> new ApiException(
            HttpStatus.NOT_FOUND,
            "material.not_found",
            "Material '" + materialId + "' does not exist"
        ));
    }

    @Override
    public List<MaterialChunkSearchMatch> searchSemantic(float[] queryEmbedding, int limit) {
        if (queryEmbedding == null || queryEmbedding.length == 0 || limit <= 0) {
            return List.of();
        }

        try {
            return jdbcTemplate.query(
                """
                    SELECT
                        m.id AS material_id,
                        c.chunk_index,
                        m.title,
                        c.chunk_text,
                        c.page,
                        c.extractor,
                        c.ocr_used,
                        c.embedding <=> ? AS semantic_distance,
                        NULL::DOUBLE PRECISION AS lexical_score
                    FROM material_chunks c
                    JOIN materials m ON m.id = c.material_id
                    WHERE m.indexing_status IN ('READY', 'PARTIAL_READY')
                      AND m.version_state = 'ACTIVE'
                      AND c.embedding IS NOT NULL
                    ORDER BY semantic_distance ASC, c.page NULLS LAST, c.chunk_index ASC
                    LIMIT ?
                    """,
                preparedStatement -> {
                    preparedStatement.setObject(1, new PGvector(queryEmbedding));
                    preparedStatement.setInt(2, limit);
                },
                CHUNK_SEARCH_ROW_MAPPER
            );
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "material.vector_query_failed",
                "Unable to execute semantic retrieval",
                exception
            );
        }
    }

    @Override
    public List<MaterialChunkSearchMatch> searchLexical(String query, int limit) {
        if (query == null || query.isBlank() || limit <= 0) {
            return List.of();
        }

        try {
            return jdbcTemplate.query(
                """
                    WITH query_term AS (
                        SELECT plainto_tsquery('simple', ?) AS q
                    )
                    SELECT
                        m.id AS material_id,
                        c.chunk_index,
                        m.title,
                        c.chunk_text,
                        c.page,
                        c.extractor,
                        c.ocr_used,
                        NULL::DOUBLE PRECISION AS semantic_distance,
                        ts_rank_cd(c.search_vector, qt.q) AS lexical_score
                    FROM material_chunks c
                    JOIN materials m ON m.id = c.material_id
                    JOIN query_term qt ON TRUE
                    WHERE m.indexing_status IN ('READY', 'PARTIAL_READY')
                      AND m.version_state = 'ACTIVE'
                      AND c.search_vector @@ qt.q
                    ORDER BY lexical_score DESC, c.page NULLS LAST, c.chunk_index ASC
                    LIMIT ?
                    """,
                CHUNK_SEARCH_ROW_MAPPER,
                query,
                limit
            );
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "material.lexical_query_failed",
                "Unable to execute lexical retrieval",
                exception
            );
        }
    }

    @Override
    public StoredMaterialRecord markIndexingPending(
        String materialId,
        String reasonCode,
        String reasonMessage,
        Instant updatedAt
    ) {
        updateIndexingState(materialId, MaterialIndexingStatus.PENDING, reasonCode, reasonMessage, updatedAt, updatedAt, null);
        return findById(materialId).orElseThrow(() -> new ApiException(
            HttpStatus.NOT_FOUND,
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
                jdbcTemplate.update("DELETE FROM material_chunks WHERE material_id = ?", UUID.fromString(materialId));
                insertEmbeddedChunks(materialId, chunks);
                updateIndexingState(materialId, finalStatus, reasonCode, reasonMessage, updatedAt, null, null);
            });
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
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
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
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
                            """ + MATERIAL_COLUMNS + """
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
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
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
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "material.storage_read_failed",
                "Unable to inspect pending indexing jobs in PostgreSQL",
                exception
            );
        }
    }

    @Override
    public IndexingQueueSnapshot getIndexingQueueSnapshot() {
        try {
            return jdbcTemplate.queryForObject(
                """
                    SELECT
                        COUNT(*) FILTER (WHERE version_state = 'ACTIVE' AND indexing_status = 'PENDING') AS pending_count,
                        COUNT(*) FILTER (WHERE version_state = 'ACTIVE' AND indexing_status = 'IN_PROGRESS') AS in_progress_count,
                        COUNT(*) FILTER (WHERE version_state = 'ACTIVE' AND indexing_status = 'FAILED') AS failed_count,
                        MIN(next_retry_at) FILTER (WHERE version_state = 'ACTIVE' AND indexing_status = 'PENDING' AND next_retry_at IS NOT NULL) AS next_retry_at
                    FROM materials
                    """,
                (resultSet, rowNum) -> new IndexingQueueSnapshot(
                    resultSet.getInt("pending_count"),
                    resultSet.getInt("in_progress_count"),
                    resultSet.getInt("failed_count"),
                    toInstantOrNull(resultSet.getTimestamp("next_retry_at"))
                )
            );
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "material.storage_read_failed",
                "Unable to inspect indexing queue state in PostgreSQL",
                exception
            );
        }
    }

    private void insertMaterial(StoredMaterialRecord record) {
        jdbcTemplate.update(
            """
                INSERT INTO materials (
                    id,
                    title,
                    source_type,
                    original_file_name,
                    media_type,
                    content,
                    normalized_content,
                    content_hash,
                    source_key,
                    extractor,
                    ocr_used,
                    page_count,
                    indexing_status,
                    version_state,
                    status_reason_code,
                    status_reason_message,
                    indexing_attempts,
                    next_retry_at,
                    claimed_at,
                    superseded_by_material_id,
                    supersede_reason,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, ?)
                """,
            UUID.fromString(record.id()),
            record.title(),
            record.sourceType(),
            record.originalFileName(),
            record.mediaType(),
            record.content(),
            record.normalizedContent(),
            record.contentHash(),
            record.sourceKey(),
            record.extractor(),
            record.ocrUsed(),
            record.pageCount(),
            record.status().name(),
            record.versionState().name(),
            record.statusReasonCode(),
            record.statusReasonMessage(),
            record.indexingAttempts(),
            record.nextRetryAt() == null ? null : Timestamp.from(record.nextRetryAt()),
            record.supersededByMaterialId() == null ? null : UUID.fromString(record.supersededByMaterialId()),
            record.supersedeReason(),
            Timestamp.from(record.createdAt()),
            Timestamp.from(record.updatedAt())
        );
    }

    private void insertRawChunks(String materialId, List<StoredMaterialChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate(
            """
                INSERT INTO material_chunks (
                    material_id,
                    chunk_index,
                    chunk_text,
                    page,
                    extractor,
                    ocr_used,
                    search_vector,
                    embedding
                ) VALUES (?, ?, ?, ?, ?, ?, to_tsvector('simple', ?), NULL)
                """,
            new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement preparedStatement, int index) throws SQLException {
                    StoredMaterialChunk chunk = chunks.get(index);
                    preparedStatement.setObject(1, UUID.fromString(materialId));
                    preparedStatement.setInt(2, chunk.index());
                    preparedStatement.setString(3, chunk.text());
                    if (chunk.page() == null) {
                        preparedStatement.setObject(4, null);
                    } else {
                        preparedStatement.setInt(4, chunk.page());
                    }
                    preparedStatement.setString(5, chunk.extractor());
                    preparedStatement.setBoolean(6, Boolean.TRUE.equals(chunk.ocrUsed()));
                    preparedStatement.setString(7, chunk.text());
                }

                @Override
                public int getBatchSize() {
                    return chunks.size();
                }
            }
        );
    }

    private void insertEmbeddedChunks(String materialId, List<StoredEmbeddedMaterialChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate(
            """
                INSERT INTO material_chunks (
                    material_id,
                    chunk_index,
                    chunk_text,
                    page,
                    extractor,
                    ocr_used,
                    search_vector,
                    embedding
                ) VALUES (?, ?, ?, ?, ?, ?, to_tsvector('simple', ?), ?)
                """,
            new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement preparedStatement, int index) throws SQLException {
                    StoredEmbeddedMaterialChunk chunk = chunks.get(index);
                    preparedStatement.setObject(1, UUID.fromString(materialId));
                    preparedStatement.setInt(2, chunk.index());
                    preparedStatement.setString(3, chunk.text());
                    if (chunk.page() == null) {
                        preparedStatement.setObject(4, null);
                    } else {
                        preparedStatement.setInt(4, chunk.page());
                    }
                    preparedStatement.setString(5, chunk.extractor());
                    preparedStatement.setBoolean(6, chunk.ocrUsed());
                    preparedStatement.setString(7, chunk.text());
                    preparedStatement.setObject(8, new PGvector(chunk.embedding()));
                }

                @Override
                public int getBatchSize() {
                    return chunks.size();
                }
            }
        );
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
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "material.storage_write_failed",
                "Unable to update material indexing status in PostgreSQL",
                exception
            );
        }
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? Instant.now() : timestamp.toInstant();
    }

    private static Instant toInstantOrNull(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record ClaimCandidate(
        StoredMaterialRecord record,
        int attempts
    ) {
    }
}
