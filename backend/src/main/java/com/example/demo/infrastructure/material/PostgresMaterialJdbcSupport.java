package com.example.demo.infrastructure.material;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.example.demo.api.ApiException;
import com.example.demo.model.DocumentType;
import com.example.demo.model.KnowledgeDocumentClass;
import com.example.demo.model.KnowledgeScope;
import com.example.demo.model.MaterialIndexingStatus;
import com.example.demo.model.MaterialMetadataProvenance;
import com.example.demo.model.MaterialMetadataSnapshot;
import com.example.demo.model.MaterialVersionState;
import com.example.demo.model.RetrievalFilters;
import com.example.demo.model.SourceTrustLevel;
import com.pgvector.PGvector;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class PostgresMaterialJdbcSupport {

    private static final ObjectMapper JSON_MAPPER = JsonMapper.builder()
        .findAndAddModules()
        .build();

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
        document_type,
        document_date,
        document_number,
        author_name,
        department,
        version_label,
        language_code,
        source_trust,
        project_name,
        counterparty,
        business_status,
        period_start,
        period_end,
        metadata_jsonb,
        lineage_version,
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
        resultSet.getString("supersede_reason"),
        resultSet.getInt("lineage_version"),
        materialMetadataOf(resultSet)
    );

    private static final RowMapper<MaterialChunkSearchMatch> CHUNK_SEARCH_ROW_MAPPER = (resultSet, rowNum) -> new MaterialChunkSearchMatch(
        resultSet.getObject("material_id").toString(),
        resultSet.getInt("chunk_index"),
        resultSet.getString("title"),
        resultSet.getString("chunk_text"),
        resultSet.getObject("page", Integer.class),
        resultSet.getString("extractor"),
        resultSet.getBoolean("ocr_used"),
        chunkTypeOf(resultSet.getString("chunk_type")),
        resultSet.getObject("semantic_distance", Double.class),
        resultSet.getObject("lexical_score", Double.class)
    );

    private static final RowMapper<StoredMaterialChunk> RAW_CHUNK_ROW_MAPPER = (resultSet, rowNum) -> new StoredMaterialChunk(
        resultSet.getInt("chunk_index"),
        resultSet.getString("chunk_text"),
        List.of(),
        resultSet.getObject("page", Integer.class),
        resultSet.getString("extractor"),
        resultSet.getBoolean("ocr_used"),
        chunkTypeOf(resultSet.getString("chunk_type")),
        textArrayOf(resultSet.getArray("section_path")),
        textArrayOf(resultSet.getArray("heading_trail")),
        resultSet.getString("table_id"),
        resultSet.getString("slide_id"),
        parserConfidenceOf(resultSet.getString("parser_confidence"), resultSet.getBoolean("ocr_used"))
    );
    private static final RowMapper<StoredMaterialSegment> SEGMENT_ROW_MAPPER = (resultSet, rowNum) -> new StoredMaterialSegment(
        resultSet.getInt("segment_index"),
        resultSet.getString("segment_text"),
        resultSet.getObject("page", Integer.class),
        resultSet.getString("extractor"),
        resultSet.getBoolean("ocr_used")
    );
    private static final RowMapper<SearchableMaterialChunkSnapshot> SEARCHABLE_CHUNK_ROW_MAPPER = (resultSet, rowNum) ->
        new SearchableMaterialChunkSnapshot(
            resultSet.getInt("chunk_index"),
            resultSet.getString("chunk_text"),
            resultSet.getObject("page", Integer.class),
            resultSet.getString("extractor"),
            resultSet.getBoolean("ocr_used"),
            chunkTypeOf(resultSet.getString("chunk_type")),
            textArrayOf(resultSet.getArray("section_path")),
            textArrayOf(resultSet.getArray("heading_trail")),
            resultSet.getString("table_id"),
            resultSet.getString("slide_id"),
            parserConfidenceOf(resultSet.getString("parser_confidence"), resultSet.getBoolean("ocr_used"))
        );
    private static final RowMapper<MaterialSearchSyncQueueEntry> SEARCH_SYNC_QUEUE_ROW_MAPPER = (resultSet, rowNum) ->
        new MaterialSearchSyncQueueEntry(
            resultSet.getObject("material_id").toString(),
            SearchSyncDeliveryState.valueOf(resultSet.getString("delivery_state")),
            resultSet.getInt("attempt_count"),
            toInstantOrNull(resultSet.getTimestamp("next_attempt_at")),
            toInstantOrNull(resultSet.getTimestamp("claimed_at")),
            resultSet.getString("last_error_code"),
            resultSet.getString("last_error_message"),
            toInstant(resultSet.getTimestamp("requested_at")),
            toInstant(resultSet.getTimestamp("created_at")),
            toInstant(resultSet.getTimestamp("updated_at"))
        );

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    PostgresMaterialJdbcSupport(
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    private List<StoredMaterialRecord> enrichMetadata(List<StoredMaterialRecord> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }

        Map<String, List<String>> tagsByMaterialId = loadTagsByMaterialIds(records.stream().map(StoredMaterialRecord::id).toList());
        return records.stream()
            .map(record -> record.withMetadata(record.metadata().withTags(tagsByMaterialId.getOrDefault(record.id(), List.of()))))
            .toList();
    }

    private Map<String, List<String>> loadTagsByMaterialIds(List<String> materialIds) {
        if (materialIds == null || materialIds.isEmpty()) {
            return Map.of();
        }

        String placeholders = String.join(", ", Collections.nCopies(materialIds.size(), "?"));
        Map<String, List<String>> tagsByMaterialId = new LinkedHashMap<>();
        jdbcTemplate.query(
            """
                SELECT material_id, tag_value
                FROM material_tags
                WHERE material_id IN (
                """
                + placeholders
                + """
                )
                ORDER BY material_id ASC, tag_order ASC
                """,
            resultSet -> {
                String materialId = resultSet.getObject("material_id").toString();
                tagsByMaterialId.computeIfAbsent(materialId, ignored -> new ArrayList<>())
                    .add(resultSet.getString("tag_value"));
            },
            materialIds.stream().map(UUID::fromString).toArray()
        );
        return Map.copyOf(tagsByMaterialId);
    }

    public List<StoredMaterialRecord> findAll() {
        return enrichMetadata(jdbcTemplate.query(
            "SELECT " + MATERIAL_COLUMNS + " FROM materials ORDER BY created_at DESC",
            MATERIAL_ROW_MAPPER
        ));
    }

    public List<StoredMaterialRecord> findActivePageAfter(Instant createdAt, String id, int limit) {
        if (limit <= 0) {
            return List.of();
        }

        try {
            if (createdAt == null || id == null) {
                return enrichMetadata(jdbcTemplate.query(
                    """
                        SELECT
                            """
                        + MATERIAL_COLUMNS
                        + """
                        FROM materials
                        WHERE version_state = 'ACTIVE'
                        ORDER BY created_at ASC, id ASC
                        LIMIT ?
                        """,
                    MATERIAL_ROW_MAPPER,
                    limit
                ));
            }

            return enrichMetadata(jdbcTemplate.query(
                """
                    SELECT
                        """
                    + MATERIAL_COLUMNS
                    + """
                    FROM materials
                    WHERE version_state = 'ACTIVE'
                      AND (
                        created_at > ?
                        OR (created_at = ? AND id > ?)
                      )
                    ORDER BY created_at ASC, id ASC
                    LIMIT ?
                    """,
                MATERIAL_ROW_MAPPER,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt),
                UUID.fromString(id),
                limit
            ));
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "material.storage_read_failed",
                "Unable to load active material batch from PostgreSQL",
                exception
            );
        }
    }

    public Optional<StoredMaterialRecord> findById(String id) {
        List<StoredMaterialRecord> records = enrichMetadata(jdbcTemplate.query(
            "SELECT " + MATERIAL_COLUMNS + " FROM materials WHERE id = ? LIMIT 1",
            MATERIAL_ROW_MAPPER,
            UUID.fromString(id)
        ));
        return records.stream().findFirst();
    }

    public Optional<String> findSourceKeyById(String id) {
        return jdbcTemplate.query(
            "SELECT source_key FROM materials WHERE id = ? LIMIT 1",
            (resultSet, rowNum) -> resultSet.getString("source_key"),
            UUID.fromString(id)
        ).stream().findFirst();
    }

    public Optional<StoredMaterialRecord> findBySourceKeyAndContentHash(String sourceKey, String contentHash) {
        List<StoredMaterialRecord> records = enrichMetadata(jdbcTemplate.query(
            "SELECT " + MATERIAL_COLUMNS + " FROM materials WHERE source_key = ? AND content_hash = ? LIMIT 1",
            MATERIAL_ROW_MAPPER,
            sourceKey,
            contentHash
        ));
        return records.stream().findFirst();
    }

    public String resolveSourceKey(MaterialLineageIdentity identity) {
        try {
            return jdbcTemplate.queryForObject(
                """
                    INSERT INTO material_lineage_identities (
                        source_key,
                        source_type,
                        identity_kind,
                        identity_key,
                        explicit_title_norm,
                        original_file_name_norm,
                        file_stem_norm,
                        content_anchor,
                        created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (source_type, identity_kind, identity_key)
                    DO UPDATE
                    SET explicit_title_norm = EXCLUDED.explicit_title_norm,
                        original_file_name_norm = EXCLUDED.original_file_name_norm,
                        file_stem_norm = EXCLUDED.file_stem_norm,
                        content_anchor = EXCLUDED.content_anchor
                    RETURNING source_key
                    """,
                String.class,
                identity.sourceKey(),
                identity.sourceType(),
                identity.identityKind().name(),
                identity.identityKey(),
                identity.explicitTitleNorm(),
                identity.originalFileNameNorm(),
                identity.fileStemNorm(),
                identity.contentAnchor(),
                Timestamp.from(Instant.now())
            );
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "material.storage_write_failed",
                "Unable to resolve material lineage identity in PostgreSQL",
                exception
            );
        }
    }

    public StoredMaterialRecord save(StoredMaterialRecord record, List<StoredMaterialChunk> chunks) {
        return save(record, ChunkProfile.FIXED_V1.propertyValue(), chunks, List.of());
    }

    public StoredMaterialRecord save(
        StoredMaterialRecord record,
        String chunkProfile,
        List<StoredMaterialChunk> chunks,
        List<StoredMaterialSegment> segments
    ) {
        try {
            return transactionTemplate.execute(status -> {
                StoredMaterialRecord persistedRecord = insertMaterial(record, chunkProfile);
                insertTags(persistedRecord.id(), persistedRecord.metadata().tags());
                insertRawChunks(persistedRecord.id(), chunks);
                insertSegments(persistedRecord.id(), segments);
                return persistedRecord;
            });
        } catch (DuplicateKeyException exception) {
            return findBySourceKeyAndContentHash(record.sourceKey(), record.contentHash())
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

    public List<StoredMaterialChunk> findChunks(String materialId) {
        try {
            return jdbcTemplate.query(
                """
                    SELECT
                        chunk_index,
                        chunk_text,
                        page,
                        extractor,
                        ocr_used,
                        chunk_type,
                        section_path,
                        heading_trail,
                        table_id,
                        slide_id,
                        parser_confidence
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

    public List<StoredMaterialSegment> findSegments(String materialId) {
        try {
            return jdbcTemplate.query(
                """
                    SELECT
                        segment_index,
                        segment_text,
                        page,
                        extractor,
                        ocr_used
                    FROM material_segments
                    WHERE material_id = ?
                    ORDER BY segment_index ASC
                    """,
                SEGMENT_ROW_MAPPER,
                UUID.fromString(materialId)
            );
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "material.storage_read_failed",
                "Unable to load material segments from PostgreSQL",
                exception
            );
        }
    }

    public String findChunkProfile(String materialId) {
        try {
            String chunkProfile = jdbcTemplate.query(
                "SELECT chunk_profile FROM materials WHERE id = ? LIMIT 1",
                (resultSet, rowNum) -> resultSet.getString("chunk_profile"),
                UUID.fromString(materialId)
            ).stream().findFirst().orElse(null);
            return chunkProfile == null ? ChunkProfile.FIXED_V1.propertyValue() : chunkProfile;
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "material.storage_read_failed",
                "Unable to load material chunk profile from PostgreSQL",
                exception
            );
        }
    }

    public void replaceChunking(
        String materialId,
        String chunkProfile,
        List<StoredMaterialChunk> chunks,
        List<StoredMaterialSegment> segments,
        Instant updatedAt
    ) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                if (!lockMaterialIfPresent(materialId)) {
                    return;
                }
                jdbcTemplate.update("DELETE FROM material_segments WHERE material_id = ?", UUID.fromString(materialId));
                jdbcTemplate.update("DELETE FROM material_chunks WHERE material_id = ?", UUID.fromString(materialId));
                insertRawChunks(materialId, chunks);
                insertSegments(materialId, segments);
                jdbcTemplate.update(
                    "UPDATE materials SET chunk_profile = ?, updated_at = ? WHERE id = ?",
                    chunkProfile,
                    Timestamp.from(updatedAt),
                    UUID.fromString(materialId)
                );
            });
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "material.storage_write_failed",
                "Unable to replace material chunking in PostgreSQL",
                exception
            );
        }
    }

    public List<StoredMaterialRecord> findAllBySourceKey(String sourceKey) {
        try {
            return enrichMetadata(jdbcTemplate.query(
                """
                    SELECT
                        """
                    + MATERIAL_COLUMNS
                    + """
                    FROM materials
                    WHERE source_key = ?
                    ORDER BY lineage_version DESC, created_at DESC, id DESC
                    """,
                MATERIAL_ROW_MAPPER,
                sourceKey
            ));
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "material.storage_read_failed",
                "Unable to load material lineage from PostgreSQL",
                exception
            );
        }
    }

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

    public int countMaterials() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM materials", Integer.class);
        return count == null ? 0 : count;
    }

    public int countActiveMaterials() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM materials WHERE version_state = 'ACTIVE'",
            Integer.class
        );
        return count == null ? 0 : count;
    }

    public int countReadyMaterials() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM materials WHERE indexing_status IN ('READY', 'PARTIAL_READY') AND version_state = 'ACTIVE'",
            Integer.class
        );
        return count == null ? 0 : count;
    }

    public QualityLayerCoverageSnapshot qualityLayerCoverageSnapshot() {
        try {
            return jdbcTemplate.queryForObject(
                """
                    SELECT
                        COUNT(*) FILTER (WHERE m.version_state = 'ACTIVE') AS active_total,
                        COUNT(*) FILTER (
                            WHERE m.version_state = 'ACTIVE'
                              AND (
                                  m.document_type <> 'OTHER'
                                  OR m.source_trust <> 'UNKNOWN'
                                  OR NULLIF(BTRIM(m.author_name), '') IS NOT NULL
                                  OR NULLIF(BTRIM(m.department), '') IS NOT NULL
                                  OR m.document_date IS NOT NULL
                                  OR NULLIF(BTRIM(m.document_number), '') IS NOT NULL
                                  OR NULLIF(BTRIM(m.version_label), '') IS NOT NULL
                                  OR NULLIF(BTRIM(m.language_code), '') IS NOT NULL
                                  OR NULLIF(BTRIM(m.project_name), '') IS NOT NULL
                                  OR NULLIF(BTRIM(m.counterparty), '') IS NOT NULL
                                  OR NULLIF(BTRIM(m.business_status), '') IS NOT NULL
                                  OR m.period_start IS NOT NULL
                                  OR m.period_end IS NOT NULL
                                  OR EXISTS (
                                      SELECT 1
                                      FROM material_tags mt
                                      WHERE mt.material_id = m.id
                                  )
                              )
                        ) AS active_with_effective_metadata,
                        COUNT(*) FILTER (
                            WHERE m.version_state = 'ACTIVE'
                              AND m.document_type <> 'OTHER'
                        ) AS document_type_covered,
                        COUNT(*) FILTER (
                            WHERE m.version_state = 'ACTIVE'
                              AND m.source_trust <> 'UNKNOWN'
                        ) AS source_trust_covered,
                        COUNT(*) FILTER (
                            WHERE m.version_state = 'ACTIVE'
                              AND (
                                  NULLIF(BTRIM(m.author_name), '') IS NOT NULL
                                  OR NULLIF(BTRIM(m.department), '') IS NOT NULL
                              )
                        ) AS author_or_department_covered,
                        COUNT(*) FILTER (
                            WHERE m.version_state = 'ACTIVE'
                              AND m.chunk_profile = 'structured-v1'
                        ) AS structured_profile_active,
                        COUNT(*) FILTER (
                            WHERE m.version_state = 'ACTIVE'
                              AND m.indexing_status = 'PARTIAL_READY'
                        ) AS partial_ready_active
                    FROM materials m
                    """,
                (resultSet, rowNum) -> new QualityLayerCoverageSnapshot(
                    resultSet.getInt("active_total"),
                    resultSet.getInt("active_with_effective_metadata"),
                    resultSet.getInt("document_type_covered"),
                    resultSet.getInt("source_trust_covered"),
                    resultSet.getInt("author_or_department_covered"),
                    resultSet.getInt("structured_profile_active"),
                    resultSet.getInt("partial_ready_active")
                )
            );
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "material.storage_read_failed",
                "Unable to load quality-layer coverage metrics from PostgreSQL",
                exception
            );
        }
    }

    public MaterialRetrievalScopeSnapshot describeRetrievalScope(
        KnowledgeScope knowledgeScope,
        RetrievalFilters retrievalFilters,
        Instant uploadedAfterInclusive,
        Instant uploadedBeforeExclusive
    ) {
        RetrievalScopeSql scopeSql = buildRetrievalScopeSql(
            knowledgeScope,
            uploadedAfterInclusive,
            uploadedBeforeExclusive
        );
        SearchFilterSql filterSql = buildSearchFilterSql(retrievalFilters);
        try {
            MaterialRetrievalScopeSnapshot countsOnly = jdbcTemplate.query(
                """
                    WITH scoped AS (
                        SELECT
                            m.id,
                            m.version_state,
                            m.indexing_status
                        FROM materials m
                        WHERE 1 = 1
                    """
                    + scopeSql.sql()
                    + filterSql.sql()
                    + """
                    )
                    SELECT
                        (SELECT COUNT(*) FROM materials) AS material_count,
                        (SELECT COUNT(*) FROM materials WHERE version_state = 'ACTIVE') AS active_material_count,
                        (SELECT COUNT(*) FROM materials
                            WHERE version_state = 'ACTIVE'
                              AND indexing_status IN ('READY', 'PARTIAL_READY')) AS ready_material_count,
                        (SELECT COUNT(*) FROM scoped) AS scoped_material_count,
                        (SELECT COUNT(*) FROM scoped WHERE version_state = 'ACTIVE') AS scoped_active_material_count,
                        (SELECT COUNT(*) FROM scoped
                            WHERE version_state = 'ACTIVE'
                              AND indexing_status IN ('READY', 'PARTIAL_READY')) AS scoped_ready_material_count
                    """,
                preparedStatement -> {
                    int parameterIndex = bindRetrievalScope(preparedStatement, 1, scopeSql);
                    bindSearchFilters(preparedStatement, parameterIndex, filterSql);
                },
                resultSet -> {
                    if (!resultSet.next()) {
                        return new MaterialRetrievalScopeSnapshot(0, 0, 0, 0, 0, 0, Set.of());
                    }
                    return new MaterialRetrievalScopeSnapshot(
                        resultSet.getInt("material_count"),
                        resultSet.getInt("active_material_count"),
                        resultSet.getInt("ready_material_count"),
                        resultSet.getInt("scoped_material_count"),
                        resultSet.getInt("scoped_active_material_count"),
                        resultSet.getInt("scoped_ready_material_count"),
                        Set.of()
                    );
                }
            );
            Set<String> scopedReadyMaterialIds = new java.util.LinkedHashSet<>(jdbcTemplate.query(
                """
                    SELECT m.id::text
                    FROM materials m
                    WHERE m.version_state = 'ACTIVE'
                      AND m.indexing_status IN ('READY', 'PARTIAL_READY')
                    """
                    + scopeSql.sql()
                    + filterSql.sql()
                    + """
                    ORDER BY m.created_at ASC, m.id ASC
                    """,
                preparedStatement -> {
                    int parameterIndex = bindRetrievalScope(preparedStatement, 1, scopeSql);
                    bindSearchFilters(preparedStatement, parameterIndex, filterSql);
                },
                (resultSet, rowNum) -> resultSet.getString(1)
            ));
            return new MaterialRetrievalScopeSnapshot(
                countsOnly.materialCount(),
                countsOnly.activeMaterialCount(),
                countsOnly.readyMaterialCount(),
                countsOnly.scopedMaterialCount(),
                countsOnly.scopedActiveMaterialCount(),
                countsOnly.scopedReadyMaterialCount(),
                scopedReadyMaterialIds
            );
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "material.storage_read_failed",
                "Unable to describe retrieval scope from PostgreSQL",
                exception
            );
        }
    }

    public void lockLineage(String sourceKey) {
        try {
            jdbcTemplate.query(
                "SELECT pg_advisory_xact_lock(?, hashtext(?))",
                resultSet -> {
                },
                71_201,
                sourceKey
            );
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "material.storage_write_failed",
                "Unable to lock material lineage in PostgreSQL",
                exception
            );
        }
    }

    public List<StoredMaterialRecord> supersedeActiveVersions(
        String sourceKey,
        String supersededByMaterialId,
        String excludeMaterialId,
        String supersedeReason,
        Instant updatedAt
    ) {
        try {
            return transactionTemplate.execute(status -> {
                List<StoredMaterialRecord> affectedRecords = jdbcTemplate.query(
                    """
                        SELECT
                            """
                        + MATERIAL_COLUMNS
                        + """
                        FROM materials
                        WHERE source_key = ?
                          AND version_state = 'ACTIVE'
                          AND id <> ?
                        ORDER BY lineage_version DESC, created_at DESC, id DESC
                        FOR UPDATE
                        """,
                    MATERIAL_ROW_MAPPER,
                    sourceKey,
                    UUID.fromString(excludeMaterialId)
                );
                if (affectedRecords.isEmpty()) {
                    return List.of();
                }

                jdbcTemplate.update(
                    """
                        UPDATE materials
                        SET version_state = 'SUPERSEDED',
                            superseded_by_material_id = ?,
                            supersede_reason = ?,
                            updated_at = ?
                        WHERE source_key = ?
                          AND version_state = 'ACTIVE'
                          AND id <> ?
                        """,
                    UUID.fromString(supersededByMaterialId),
                    supersedeReason,
                    Timestamp.from(updatedAt),
                    sourceKey,
                    UUID.fromString(excludeMaterialId)
                );
                return affectedRecords;
            });
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "material.storage_write_failed",
                "Unable to supersede older material versions in PostgreSQL",
                exception
            );
        }
    }

    public Optional<StoredMaterialRecord> findLatestBySourceKeyAndVersionState(String sourceKey, MaterialVersionState versionState) {
        try {
            List<StoredMaterialRecord> records = enrichMetadata(jdbcTemplate.query(
                """
                    SELECT
                        """
                    + MATERIAL_COLUMNS
                    + """
                     FROM materials
                     WHERE source_key = ?
                       AND version_state = ?
                     ORDER BY lineage_version DESC, created_at DESC, id DESC
                     LIMIT 1
                    """,
                MATERIAL_ROW_MAPPER,
                sourceKey,
                versionState.name()
            ));
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

    public List<MaterialChunkSearchMatch> searchSemantic(float[] queryEmbedding, int limit) {
        return searchSemantic(queryEmbedding, limit, null, RetrievalFilters.empty());
    }

    public List<MaterialChunkSearchMatch> searchSemantic(float[] queryEmbedding, int limit, Set<String> allowedMaterialIds) {
        return searchSemantic(queryEmbedding, limit, allowedMaterialIds, RetrievalFilters.empty());
    }

    public List<MaterialChunkSearchMatch> searchSemantic(
        float[] queryEmbedding,
        int limit,
        Set<String> allowedMaterialIds,
        RetrievalFilters filters
    ) {
        if (queryEmbedding == null || queryEmbedding.length == 0 || limit <= 0) {
            return List.of();
        }

        UUID[] scopedMaterialIds = toUuidArray(allowedMaterialIds);
        SearchFilterSql filterSql = buildSearchFilterSql(filters);
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
                        c.chunk_type,
                        c.embedding <=> ? AS semantic_distance,
                        NULL::DOUBLE PRECISION AS lexical_score
                    FROM material_chunks c
                    JOIN materials m ON m.id = c.material_id
                    WHERE m.indexing_status IN ('READY', 'PARTIAL_READY')
                      AND m.version_state = 'ACTIVE'
                      AND c.embedding IS NOT NULL
                      AND (? IS NULL OR m.id = ANY (?))
                    """
                    + filterSql.sql()
                    + """
                    ORDER BY semantic_distance ASC, c.page NULLS LAST, c.chunk_index ASC
                    LIMIT ?
                    """,
                preparedStatement -> {
                    preparedStatement.setObject(1, new PGvector(queryEmbedding));
                    int parameterIndex = 2;
                    bindUuidArray(preparedStatement, parameterIndex++, scopedMaterialIds);
                    bindUuidArray(preparedStatement, parameterIndex++, scopedMaterialIds);
                    parameterIndex = bindSearchFilters(preparedStatement, parameterIndex, filterSql);
                    preparedStatement.setInt(parameterIndex, limit);
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

    public LexicalProviderType type() {
        return LexicalProviderType.POSTGRES;
    }

    public List<MaterialChunkSearchMatch> search(String query, int limit) {
        return search(query, limit, null, RetrievalFilters.empty());
    }

    public List<MaterialChunkSearchMatch> search(String query, int limit, Set<String> allowedMaterialIds) {
        return search(query, limit, allowedMaterialIds, RetrievalFilters.empty());
    }

    public List<MaterialChunkSearchMatch> search(
        String query,
        int limit,
        Set<String> allowedMaterialIds,
        RetrievalFilters filters
    ) {
        if (query == null || query.isBlank() || limit <= 0) {
            return List.of();
        }

        UUID[] scopedMaterialIds = toUuidArray(allowedMaterialIds);
        SearchFilterSql filterSql = buildSearchFilterSql(filters);
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
                        c.chunk_type,
                        NULL::DOUBLE PRECISION AS semantic_distance,
                        ts_rank_cd(c.search_vector, qt.q) AS lexical_score
                    FROM material_chunks c
                    JOIN materials m ON m.id = c.material_id
                    JOIN query_term qt ON TRUE
                    WHERE m.indexing_status IN ('READY', 'PARTIAL_READY')
                      AND m.version_state = 'ACTIVE'
                      AND (? IS NULL OR m.id = ANY (?))
                      AND c.search_vector @@ qt.q
                    """
                    + filterSql.sql()
                    + """
                    ORDER BY lexical_score DESC, c.page NULLS LAST, c.chunk_index ASC
                    LIMIT ?
                    """,
                preparedStatement -> {
                    int parameterIndex = 1;
                    preparedStatement.setString(parameterIndex++, query);
                    bindUuidArray(preparedStatement, parameterIndex++, scopedMaterialIds);
                    bindUuidArray(preparedStatement, parameterIndex++, scopedMaterialIds);
                    parameterIndex = bindSearchFilters(preparedStatement, parameterIndex, filterSql);
                    preparedStatement.setInt(parameterIndex, limit);
                },
                CHUNK_SEARCH_ROW_MAPPER
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
                if (!lockMaterialIfPresent(materialId)) {
                    return;
                }
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

    public void markIndexingFailed(String materialId, String code, String message, Instant updatedAt) {
        updateIndexingState(materialId, MaterialIndexingStatus.FAILED, code, message, updatedAt, null, null);
    }

    public void rescheduleIndexing(String materialId, String code, String message, Instant updatedAt, Instant nextRetryAt) {
        updateIndexingState(materialId, MaterialIndexingStatus.PENDING, code, message, updatedAt, nextRetryAt, null);
    }

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

    public MaterialIndexingQueueRepository.IndexingQueueSnapshot getIndexingQueueSnapshot() {
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
                (resultSet, rowNum) -> new MaterialIndexingQueueRepository.IndexingQueueSnapshot(
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

    public void enqueueMaterialsForSync(Collection<String> materialIds, Instant requestedAt) {
        if (materialIds == null || materialIds.isEmpty()) {
            return;
        }

        Instant effectiveRequestedAt = requestedAt == null ? Instant.now() : requestedAt;
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
                        delivery_state,
                        attempt_count,
                        next_attempt_at,
                        claimed_at,
                        last_error_code,
                        last_error_message,
                        requested_at,
                        created_at,
                        updated_at
                    ) VALUES (?, 'PENDING', 0, NULL, NULL, NULL, NULL, ?, ?, ?)
                    ON CONFLICT (material_id) DO UPDATE
                    SET delivery_state = CASE
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
                        requested_at = GREATEST(material_search_sync_queue.requested_at, EXCLUDED.requested_at),
                        updated_at = EXCLUDED.updated_at
                    """,
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement preparedStatement, int index) throws SQLException {
                        Timestamp timestamp = Timestamp.from(effectiveRequestedAt);
                        preparedStatement.setObject(1, queueMaterialIds.get(index));
                        preparedStatement.setTimestamp(2, timestamp);
                        preparedStatement.setTimestamp(3, timestamp);
                        preparedStatement.setTimestamp(4, timestamp);
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

    public List<MaterialSearchSyncQueueEntry> findAllSearchSyncEntries() {
        try {
            return jdbcTemplate.query(
                """
                    SELECT
                        material_id,
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

    public int requeueFailedSearchSyncEntries(Instant now) {
        try {
            return jdbcTemplate.update(
                """
                    UPDATE material_search_sync_queue
                    SET delivery_state = 'PENDING',
                        attempt_count = 0,
                        next_attempt_at = NULL,
                        claimed_at = NULL,
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

    public void resetExpiredSearchSyncClaims(Instant staleBefore, Instant now) {
        try {
            jdbcTemplate.update(
                """
                    UPDATE material_search_sync_queue
                    SET delivery_state = 'PENDING',
                        next_attempt_at = NULL,
                        claimed_at = NULL,
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
                        last_error_code = NULL,
                        last_error_message = NULL,
                        updated_at = ?
                    WHERE material_id = ?
                      AND delivery_state = 'IN_PROGRESS'
                      AND claimed_at = ?
                      AND requested_at > ?
                    """,
                Timestamp.from(now),
                UUID.fromString(materialId),
                Timestamp.from(claimedAt),
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
                    toInstantOrNull(resultSet.getTimestamp("next_retry_at")),
                    toInstantOrNull(resultSet.getTimestamp("oldest_outstanding_at"))
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

    public SearchableMaterialSnapshot resolveSearchableSnapshot(String materialId) {
        Optional<StoredMaterialRecord> record = findById(materialId);
        if (record.isEmpty() || !isSearchable(record.get())) {
            return SearchableMaterialSnapshot.notSearchable(materialId);
        }

        try {
            List<SearchableMaterialChunkSnapshot> chunks = jdbcTemplate.query(
                """
                    SELECT
                        chunk_index,
                        chunk_text,
                        page,
                        extractor,
                        ocr_used,
                        chunk_type,
                        section_path,
                        heading_trail,
                        table_id,
                        slide_id,
                        parser_confidence
                    FROM material_chunks
                    WHERE material_id = ?
                    ORDER BY chunk_index ASC
                    """,
                SEARCHABLE_CHUNK_ROW_MAPPER,
                UUID.fromString(materialId)
            );
            StoredMaterialRecord material = record.get();
            return new SearchableMaterialSnapshot(
                material.id(),
                true,
                material.sourceKey(),
                material.title(),
                material.sourceType(),
                material.originalFileName(),
                material.mediaType(),
                material.updatedAt(),
                material.metadata(),
                chunks
            );
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "material.storage_read_failed",
                "Unable to resolve searchable material snapshot from PostgreSQL",
                exception
            );
        }
    }

    public List<String> findAllSearchableMaterialIds() {
        try {
            return jdbcTemplate.query(
                """
                    SELECT id
                    FROM materials
                    WHERE version_state = 'ACTIVE'
                      AND indexing_status IN ('READY', 'PARTIAL_READY')
                    ORDER BY updated_at ASC, id ASC
                    """,
                (resultSet, rowNum) -> resultSet.getObject("id").toString()
            );
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "material.storage_read_failed",
                "Unable to enumerate searchable material ids from PostgreSQL",
                exception
            );
        }
    }

    private StoredMaterialRecord insertMaterial(StoredMaterialRecord record, String chunkProfile) {
        int nextLineageVersion = nextLineageVersion(record.sourceKey());
        StoredMaterialRecord persistedRecord = record.withLineageVersion(nextLineageVersion);
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
                    document_type,
                    document_date,
                    document_number,
                    author_name,
                    department,
                    version_label,
                    language_code,
                    source_trust,
                    project_name,
                    counterparty,
                    business_status,
                    period_start,
                    period_end,
                    metadata_jsonb,
                    lineage_version,
                    chunk_profile,
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
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, ?, ?)
                """,
            UUID.fromString(persistedRecord.id()),
            persistedRecord.title(),
            persistedRecord.sourceType(),
            persistedRecord.originalFileName(),
            persistedRecord.mediaType(),
            persistedRecord.content(),
            persistedRecord.normalizedContent(),
            persistedRecord.contentHash(),
            persistedRecord.sourceKey(),
            persistedRecord.extractor(),
            persistedRecord.ocrUsed(),
            persistedRecord.pageCount(),
            persistedRecord.metadata().documentType().name(),
            persistedRecord.metadata().documentDate(),
            persistedRecord.metadata().documentNumber(),
            persistedRecord.metadata().author(),
            persistedRecord.metadata().department(),
            persistedRecord.metadata().versionLabel(),
            persistedRecord.metadata().language(),
            persistedRecord.metadata().sourceTrust().name(),
            persistedRecord.metadata().project(),
            persistedRecord.metadata().counterparty(),
            persistedRecord.metadata().businessStatus(),
            persistedRecord.metadata().periodStart(),
            persistedRecord.metadata().periodEnd(),
            serializeMetadata(persistedRecord.metadata()),
            persistedRecord.lineageVersion(),
            chunkProfile,
            persistedRecord.status().name(),
            persistedRecord.versionState().name(),
            persistedRecord.statusReasonCode(),
            persistedRecord.statusReasonMessage(),
            persistedRecord.indexingAttempts(),
            persistedRecord.nextRetryAt() == null ? null : Timestamp.from(persistedRecord.nextRetryAt()),
            persistedRecord.supersededByMaterialId() == null ? null : UUID.fromString(persistedRecord.supersededByMaterialId()),
            persistedRecord.supersedeReason(),
            Timestamp.from(persistedRecord.createdAt()),
            Timestamp.from(persistedRecord.updatedAt())
        );
        return persistedRecord;
    }

    private int nextLineageVersion(String sourceKey) {
        Integer currentMax = jdbcTemplate.queryForObject(
            "SELECT COALESCE(MAX(lineage_version), 0) FROM materials WHERE source_key = ?",
            Integer.class,
            sourceKey
        );
        return (currentMax == null ? 0 : currentMax) + 1;
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
                    SET delivery_state = ?,
                        next_attempt_at = ?,
                        claimed_at = NULL,
                        last_error_code = ?,
                        last_error_message = ?,
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

    private boolean lockMaterialIfPresent(String materialId) {
        List<Integer> matches = jdbcTemplate.query(
            "SELECT 1 FROM materials WHERE id = ? FOR UPDATE",
            (resultSet, rowNum) -> resultSet.getInt(1),
            UUID.fromString(materialId)
        );
        return !matches.isEmpty();
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
                    chunk_type,
                    section_path,
                    heading_trail,
                    table_id,
                    slide_id,
                    parser_confidence,
                    search_vector,
                    embedding
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, to_tsvector('simple', ?), NULL)
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
                    preparedStatement.setString(7, chunk.chunkType().name());
                    bindTextArray(preparedStatement, 8, chunk.sectionPath());
                    bindTextArray(preparedStatement, 9, chunk.headingTrail());
                    preparedStatement.setString(10, chunk.tableId());
                    preparedStatement.setString(11, chunk.slideId());
                    preparedStatement.setString(12, chunk.parserConfidence().name());
                    preparedStatement.setString(13, chunk.text());
                }

                @Override
                public int getBatchSize() {
                    return chunks.size();
                }
            }
        );
    }

    private void insertSegments(String materialId, List<StoredMaterialSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate(
            """
                INSERT INTO material_segments (
                    material_id,
                    segment_index,
                    segment_text,
                    page,
                    extractor,
                    ocr_used
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
            new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement preparedStatement, int index) throws SQLException {
                    StoredMaterialSegment segment = segments.get(index);
                    preparedStatement.setObject(1, UUID.fromString(materialId));
                    preparedStatement.setInt(2, segment.index());
                    preparedStatement.setString(3, segment.text());
                    if (segment.page() == null) {
                        preparedStatement.setObject(4, null);
                    } else {
                        preparedStatement.setInt(4, segment.page());
                    }
                    preparedStatement.setString(5, segment.extractor());
                    preparedStatement.setBoolean(6, Boolean.TRUE.equals(segment.ocrUsed()));
                }

                @Override
                public int getBatchSize() {
                    return segments.size();
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
                    chunk_type,
                    section_path,
                    heading_trail,
                    table_id,
                    slide_id,
                    parser_confidence,
                    search_vector,
                    embedding
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, to_tsvector('simple', ?), ?)
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
                    preparedStatement.setString(7, chunk.chunkType().name());
                    bindTextArray(preparedStatement, 8, chunk.sectionPath());
                    bindTextArray(preparedStatement, 9, chunk.headingTrail());
                    preparedStatement.setString(10, chunk.tableId());
                    preparedStatement.setString(11, chunk.slideId());
                    preparedStatement.setString(12, chunk.parserConfidence().name());
                    preparedStatement.setString(13, chunk.text());
                    preparedStatement.setObject(14, new PGvector(chunk.embedding()));
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

    private void insertTags(String materialId, List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate(
            """
                INSERT INTO material_tags (
                    material_id,
                    tag_order,
                    tag_value
                ) VALUES (?, ?, ?)
                """,
            new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement preparedStatement, int index) throws SQLException {
                    preparedStatement.setObject(1, UUID.fromString(materialId));
                    preparedStatement.setInt(2, index);
                    preparedStatement.setString(3, tags.get(index));
                }

                @Override
                public int getBatchSize() {
                    return tags.size();
                }
            }
        );
    }

    private static MaterialMetadataSnapshot materialMetadataOf(java.sql.ResultSet resultSet) throws SQLException {
        MaterialMetadataPersistencePayload payload = deserializeMetadataPayload(resultSet.getString("metadata_jsonb"));
        return new MaterialMetadataSnapshot(
            DocumentType.valueOf(resultSet.getString("document_type")),
            payload.knowledgeDocumentClass(),
            toLocalDateOrNull(resultSet.getDate("document_date")),
            resultSet.getString("document_number"),
            resultSet.getString("author_name"),
            resultSet.getString("department"),
            resultSet.getString("version_label"),
            resultSet.getString("language_code"),
            List.of(),
            SourceTrustLevel.valueOf(resultSet.getString("source_trust")),
            resultSet.getString("project_name"),
            payload.workspaceKey(),
            resultSet.getString("counterparty"),
            resultSet.getString("business_status"),
            toLocalDateOrNull(resultSet.getDate("period_start")),
            toLocalDateOrNull(resultSet.getDate("period_end")),
            payload.provenance()
        );
    }

    private static MaterialMetadataPersistencePayload deserializeMetadataPayload(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return MaterialMetadataPersistencePayload.empty();
        }

        try {
            JsonNode root = JSON_MAPPER.readTree(rawJson);
            if (root == null || root.isNull() || root.isEmpty()) {
                return MaterialMetadataPersistencePayload.empty();
            }
            if (root.has("provenance") || root.has("knowledgeDocumentClass") || root.has("workspaceKey")) {
                return JSON_MAPPER.treeToValue(root, MaterialMetadataPersistencePayload.class);
            }
            return new MaterialMetadataPersistencePayload(
                JSON_MAPPER.treeToValue(root, MaterialMetadataProvenance.class),
                null,
                null
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to deserialize material metadata provenance", exception);
        }
    }

    private static String serializeMetadata(MaterialMetadataSnapshot metadata) {
        try {
            MaterialMetadataSnapshot safeMetadata = metadata == null ? MaterialMetadataSnapshot.empty() : metadata;
            return JSON_MAPPER.writeValueAsString(new MaterialMetadataPersistencePayload(
                safeMetadata.provenance(),
                safeMetadata.knowledgeDocumentClass(),
                safeMetadata.workspaceKey()
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize material metadata provenance", exception);
        }
    }

    private static DocumentBlockType chunkTypeOf(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return DocumentBlockType.NARRATIVE;
        }
        return DocumentBlockType.valueOf(rawValue);
    }

    private static DocumentBlockConfidence parserConfidenceOf(String rawValue, boolean ocrUsed) {
        if (rawValue == null || rawValue.isBlank()) {
            return ocrUsed ? DocumentBlockConfidence.LOW : DocumentBlockConfidence.HIGH;
        }
        return DocumentBlockConfidence.valueOf(rawValue);
    }

    private SearchFilterSql buildSearchFilterSql(RetrievalFilters filters) {
        RetrievalFilters safeFilters = filters == null ? RetrievalFilters.empty() : filters;
        if (safeFilters.isEmpty()) {
            return SearchFilterSql.empty();
        }

        StringBuilder sql = new StringBuilder();
        sql.append("""
              AND (? IS NULL OR LOWER(m.document_number) = ?)
              AND (? IS NULL OR m.document_date >= ?)
              AND (? IS NULL OR m.document_date <= ?)
              AND (? IS NULL OR LOWER(m.department) = ?)
              AND (? IS NULL OR LOWER(m.project_name) = ?)
              AND (? IS NULL OR LOWER(m.counterparty) = ?)
              AND (? IS NULL OR LOWER(m.business_status) = ?)
              AND (? IS NULL OR LOWER(m.language_code) = ?)
              AND (? = FALSE OR EXISTS (
                    SELECT 1
                    FROM material_tags mt
                    WHERE mt.material_id = m.id
                      AND LOWER(mt.tag_value) = ANY (?)
              ))
              AND (? IS NULL OR (
                    CASE m.source_trust
                        WHEN 'HIGH' THEN 3
                        WHEN 'MEDIUM' THEN 2
                        WHEN 'LOW' THEN 1
                        ELSE 0
                    END
              ) >= ?)
            """);
        return new SearchFilterSql(
            sql.toString(),
            lowerCase(safeFilters.documentNumber()),
            safeFilters.documentDateFrom(),
            safeFilters.documentDateTo(),
            lowerCase(safeFilters.department()),
            lowerCase(safeFilters.project()),
            lowerCase(safeFilters.counterparty()),
            lowerCase(safeFilters.businessStatus()),
            lowerCase(safeFilters.language()),
            safeFilters.lowerCaseTags(),
            safeFilters.sourceTrustMin()
        );
    }

    private RetrievalScopeSql buildRetrievalScopeSql(
        KnowledgeScope knowledgeScope,
        Instant uploadedAfterInclusive,
        Instant uploadedBeforeExclusive
    ) {
        KnowledgeScope safeScope = knowledgeScope == null ? KnowledgeScope.empty() : knowledgeScope;
        List<String> documentClasses = safeScope.documentClasses().stream().map(Enum::name).toList();
        List<String> tags = safeScope.tags().stream()
            .map(PostgresMaterialJdbcSupport::lowerCase)
            .filter(java.util.Objects::nonNull)
            .toList();
        String workspaceKey = lowerCase(safeScope.workspaceKey());
        if (documentClasses.isEmpty()
            && tags.isEmpty()
            && workspaceKey == null
            && uploadedAfterInclusive == null
            && uploadedBeforeExclusive == null) {
            return RetrievalScopeSql.empty();
        }

        return new RetrievalScopeSql(
            """
                  AND (? = FALSE OR m.knowledge_document_class = ANY (?))
                  AND (? = FALSE OR EXISTS (
                        SELECT 1
                        FROM material_tags mt
                        WHERE mt.material_id = m.id
                          AND LOWER(mt.tag_value) = ANY (?)
                  ))
                  AND (? IS NULL OR LOWER(COALESCE(m.metadata_jsonb ->> 'workspaceKey', '')) = ?)
                  AND (? IS NULL OR m.created_at >= ?)
                  AND (? IS NULL OR m.created_at < ?)
                """,
            documentClasses,
            tags,
            workspaceKey,
            uploadedAfterInclusive,
            uploadedBeforeExclusive
        );
    }

    private int bindSearchFilters(PreparedStatement preparedStatement, int startIndex, SearchFilterSql filterSql) throws SQLException {
        if (filterSql == null || filterSql.sql().isBlank()) {
            return startIndex;
        }
        int parameterIndex = startIndex;
        preparedStatement.setString(parameterIndex++, filterSql.documentNumber());
        preparedStatement.setString(parameterIndex++, filterSql.documentNumber());
        preparedStatement.setObject(parameterIndex++, filterSql.documentDateFrom());
        preparedStatement.setObject(parameterIndex++, filterSql.documentDateFrom());
        preparedStatement.setObject(parameterIndex++, filterSql.documentDateTo());
        preparedStatement.setObject(parameterIndex++, filterSql.documentDateTo());
        preparedStatement.setString(parameterIndex++, filterSql.department());
        preparedStatement.setString(parameterIndex++, filterSql.department());
        preparedStatement.setString(parameterIndex++, filterSql.project());
        preparedStatement.setString(parameterIndex++, filterSql.project());
        preparedStatement.setString(parameterIndex++, filterSql.counterparty());
        preparedStatement.setString(parameterIndex++, filterSql.counterparty());
        preparedStatement.setString(parameterIndex++, filterSql.businessStatus());
        preparedStatement.setString(parameterIndex++, filterSql.businessStatus());
        preparedStatement.setString(parameterIndex++, filterSql.language());
        preparedStatement.setString(parameterIndex++, filterSql.language());
        preparedStatement.setBoolean(parameterIndex++, !filterSql.tags().isEmpty());
        bindTextArray(preparedStatement, parameterIndex++, filterSql.tags());
        String sourceTrustMin = filterSql.sourceTrustMin() == null ? null : filterSql.sourceTrustMin().name();
        preparedStatement.setString(parameterIndex++, sourceTrustMin);
        if (sourceTrustMin == null) {
            preparedStatement.setNull(parameterIndex++, Types.INTEGER);
        } else {
            preparedStatement.setInt(parameterIndex++, RetrievalFilters.trustRank(filterSql.sourceTrustMin()));
        }
        return parameterIndex;
    }

    private int bindRetrievalScope(PreparedStatement preparedStatement, int startIndex, RetrievalScopeSql scopeSql) throws SQLException {
        int parameterIndex = startIndex;
        preparedStatement.setBoolean(parameterIndex++, !scopeSql.documentClasses().isEmpty());
        bindTextArray(preparedStatement, parameterIndex++, scopeSql.documentClasses());
        preparedStatement.setBoolean(parameterIndex++, !scopeSql.tags().isEmpty());
        bindTextArray(preparedStatement, parameterIndex++, scopeSql.tags());
        preparedStatement.setString(parameterIndex++, scopeSql.workspaceKey());
        preparedStatement.setString(parameterIndex++, scopeSql.workspaceKey());
        if (scopeSql.uploadedAfterInclusive() == null) {
            preparedStatement.setNull(parameterIndex++, Types.TIMESTAMP_WITH_TIMEZONE);
            preparedStatement.setNull(parameterIndex++, Types.TIMESTAMP_WITH_TIMEZONE);
        } else {
            Timestamp uploadedAfter = Timestamp.from(scopeSql.uploadedAfterInclusive());
            preparedStatement.setTimestamp(parameterIndex++, uploadedAfter);
            preparedStatement.setTimestamp(parameterIndex++, uploadedAfter);
        }
        if (scopeSql.uploadedBeforeExclusive() == null) {
            preparedStatement.setNull(parameterIndex++, Types.TIMESTAMP_WITH_TIMEZONE);
            preparedStatement.setNull(parameterIndex++, Types.TIMESTAMP_WITH_TIMEZONE);
        } else {
            Timestamp uploadedBefore = Timestamp.from(scopeSql.uploadedBeforeExclusive());
            preparedStatement.setTimestamp(parameterIndex++, uploadedBefore);
            preparedStatement.setTimestamp(parameterIndex++, uploadedBefore);
        }
        return parameterIndex;
    }

    private static String lowerCase(String value) {
        return value == null ? null : value.toLowerCase(java.util.Locale.ROOT);
    }

    private static List<String> textArrayOf(Array sqlArray) throws SQLException {
        if (sqlArray == null) {
            return List.of();
        }
        Object array = sqlArray.getArray();
        if (array == null) {
            return List.of();
        }
        if (array instanceof String[] strings) {
            return List.of(strings);
        }
        if (array instanceof Object[] objects) {
            return java.util.Arrays.stream(objects)
                .filter(java.util.Objects::nonNull)
                .map(String::valueOf)
                .toList();
        }
        return List.of();
    }

    private void bindTextArray(PreparedStatement preparedStatement, int parameterIndex, List<String> values) throws SQLException {
        if (values == null || values.isEmpty()) {
            preparedStatement.setNull(parameterIndex, Types.ARRAY);
            return;
        }

        preparedStatement.setArray(
            parameterIndex,
            preparedStatement.getConnection().createArrayOf("text", values.toArray(String[]::new))
        );
    }

    private void bindUuidArray(PreparedStatement preparedStatement, int parameterIndex, UUID[] values) throws SQLException {
        if (values == null) {
            preparedStatement.setArray(parameterIndex, null);
            return;
        }

        preparedStatement.setArray(parameterIndex, preparedStatement.getConnection().createArrayOf("uuid", values));
    }

    private UUID[] toUuidArray(Set<String> allowedMaterialIds) {
        if (allowedMaterialIds == null) {
            return null;
        }
        if (allowedMaterialIds.isEmpty()) {
            return new UUID[0];
        }
        return allowedMaterialIds.stream().map(UUID::fromString).toArray(UUID[]::new);
    }

    private boolean isSearchable(StoredMaterialRecord record) {
        return record.versionState() == MaterialVersionState.ACTIVE
            && (record.status() == MaterialIndexingStatus.READY || record.status() == MaterialIndexingStatus.PARTIAL_READY);
    }

    private static LocalDate toLocalDateOrNull(java.sql.Date value) {
        return value == null ? null : value.toLocalDate();
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

    private record RetrievalScopeSql(
        String sql,
        List<String> documentClasses,
        List<String> tags,
        String workspaceKey,
        Instant uploadedAfterInclusive,
        Instant uploadedBeforeExclusive
    ) {
        private static RetrievalScopeSql empty() {
            return new RetrievalScopeSql("", List.of(), List.of(), null, null, null);
        }
    }

    private record MaterialMetadataPersistencePayload(
        MaterialMetadataProvenance provenance,
        KnowledgeDocumentClass knowledgeDocumentClass,
        String workspaceKey
    ) {
        private static MaterialMetadataPersistencePayload empty() {
            return new MaterialMetadataPersistencePayload(MaterialMetadataProvenance.empty(), null, null);
        }
    }

    private record SearchFilterSql(
        String sql,
        String documentNumber,
        LocalDate documentDateFrom,
        LocalDate documentDateTo,
        String department,
        String project,
        String counterparty,
        String businessStatus,
        String language,
        List<String> tags,
        SourceTrustLevel sourceTrustMin
    ) {
        private static SearchFilterSql empty() {
            return new SearchFilterSql("", null, null, null, null, null, null, null, null, List.of(), null);
        }
    }
}
