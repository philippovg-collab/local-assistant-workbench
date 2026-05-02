package com.example.demo.infrastructure.material;

import static com.example.demo.infrastructure.material.MaterialJdbcRowMappers.MATERIAL_ROW_MAPPER;
import static com.example.demo.infrastructure.material.MaterialJdbcRowMappers.MATERIAL_SUMMARY_ROW_MAPPER;

import com.example.demo.api.ApiException;
import com.example.demo.model.KnowledgeScope;
import com.example.demo.model.MaterialMetadataSnapshot;
import com.example.demo.model.MaterialSummary;
import com.example.demo.model.MaterialVersionState;
import com.example.demo.model.RetrievalFilters;
import com.example.demo.service.material.ChunkProfile;
import com.example.demo.service.material.MaterialRetrievalScopeSnapshot;
import com.example.demo.service.material.StoredMaterialChunk;
import com.example.demo.service.material.StoredMaterialRecord;
import com.example.demo.service.material.StoredMaterialSegment;
import com.example.demo.service.material.port.MaterialCatalogRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

final class PostgresMaterialCatalogAdapter extends PostgresMaterialJdbcSupport implements MaterialCatalogRepository {

    PostgresMaterialCatalogAdapter(
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        super(jdbcTemplate, transactionManager);
    }

    @Override
    public List<StoredMaterialRecord> findAll() {
        return recordDao.findAll();
    }

    @Override
    public List<MaterialSummary> findSummaries(int offset, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        int safeOffset = Math.max(0, offset);
        try {
            return tagDao.enrichSummaryMetadata(jdbcTemplate.query(
                """
                    SELECT
                        """
                    + PostgresMaterialSql.MATERIAL_SUMMARY_COLUMNS
                    + """
                    FROM materials
                    ORDER BY created_at DESC, id DESC
                    LIMIT ? OFFSET ?
                    """,
                MATERIAL_SUMMARY_ROW_MAPPER,
                limit,
                safeOffset
            ));
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "material.storage_read_failed",
                "Unable to load material summaries from PostgreSQL",
                exception
            );
        }
    }

    @Override
    public List<MaterialSummary> findSummariesByWorkspace(String workspaceKey, int offset, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        int safeOffset = Math.max(0, offset);
        String normalizedWorkspaceKey = lowerCase(workspaceKey);
        try {
            return tagDao.enrichSummaryMetadata(jdbcTemplate.query(
                """
                    SELECT
                        """
                    + PostgresMaterialSql.MATERIAL_SUMMARY_COLUMNS
                    + """
                    FROM materials
                    WHERE LOWER(COALESCE(workspace_key, '')) = ?
                    ORDER BY created_at DESC, id DESC
                    LIMIT ? OFFSET ?
                    """,
                MATERIAL_SUMMARY_ROW_MAPPER,
                normalizedWorkspaceKey,
                limit,
                safeOffset
            ));
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "material.storage_read_failed",
                "Unable to load material summaries from PostgreSQL",
                exception
            );
        }
    }

    @Override
    public List<StoredMaterialRecord> findActivePageAfter(Instant createdAt, String id, int limit) {
        try {
            return recordDao.findActivePageAfter(createdAt, id, limit);
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "material.storage_read_failed",
                "Unable to load active material batch from PostgreSQL",
                exception
            );
        }
    }

    @Override
    public Optional<StoredMaterialRecord> findById(String id) {
        return recordDao.findById(id);
    }

    @Override
    public List<StoredMaterialRecord> findByIds(Collection<String> ids) {
        return recordDao.findByIds(ids);
    }

    @Override
    public Optional<String> findSourceKeyById(String id) {
        return recordDao.findSourceKeyById(id);
    }

    @Override
    public Optional<StoredMaterialRecord> findBySourceKeyAndContentHash(String sourceKey, String contentHash) {
        return recordDao.findBySourceKeyAndContentHash(sourceKey, contentHash);
    }

    @Override
    public StoredMaterialRecord save(StoredMaterialRecord record, List<StoredMaterialChunk> chunks) {
        return save(record, ChunkProfile.FIXED_V1.propertyValue(), chunks, List.of());
    }

    @Override
    public StoredMaterialRecord save(
        StoredMaterialRecord record,
        String chunkProfile,
        List<StoredMaterialChunk> chunks,
        List<StoredMaterialSegment> segments
    ) {
        try {
            return transactionTemplate.execute(status -> {
                StoredMaterialRecord persistedRecord = recordDao.insertMaterial(record, chunkProfile);
                tagDao.insertTags(persistedRecord.id(), persistedRecord.metadata());
                chunkDao.insertRawChunks(persistedRecord.id(), chunks);
                chunkDao.insertSegments(persistedRecord.id(), segments);
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

    @Override
    public List<StoredMaterialRecord> findAllBySourceKey(String sourceKey) {
        try {
            return recordDao.findAllBySourceKey(sourceKey);
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
    public StoredMaterialRecord updateMetadata(String materialId, MaterialMetadataSnapshot metadata, Instant updatedAt) {
        try {
            return transactionTemplate.execute(status -> {
                if (!recordDao.lockMaterialIfPresent(materialId)) {
                    return null;
                }
                MaterialMetadataSnapshot safeMetadata = metadata == null ? MaterialMetadataSnapshot.empty() : metadata;
                jdbcTemplate.update(
                    """
                        UPDATE materials
                        SET document_type = ?,
                            document_date = ?,
                            document_number = ?,
                            author_name = ?,
                            department = ?,
                            version_label = ?,
                            language_code = ?,
                            source_trust = ?,
                            project_name = ?,
                            project_key = ?,
                            counterparty = ?,
                            business_status = ?,
                            document_status = ?,
                            period_start = ?,
                            period_end = ?,
                            knowledge_document_class = ?,
                            workspace_key = ?,
                            metadata_jsonb = ?::jsonb,
                            updated_at = ?
                        WHERE id = ?
                        """,
                    safeMetadata.documentType().name(),
                    safeMetadata.documentDate(),
                    safeMetadata.documentNumber(),
                    safeMetadata.author(),
                    safeMetadata.department(),
                    safeMetadata.versionLabel(),
                    safeMetadata.languageCode() == null ? null : safeMetadata.languageCode().name(),
                    safeMetadata.sourceTrust().name(),
                    safeMetadata.project(),
                    safeMetadata.projectKey(),
                    safeMetadata.counterparty(),
                    safeMetadata.businessStatus(),
                    safeMetadata.documentStatus().name(),
                    safeMetadata.periodStart(),
                    safeMetadata.periodEnd(),
                    safeMetadata.knowledgeDocumentClass().name(),
                    safeMetadata.workspaceKey() == null || safeMetadata.workspaceKey().isBlank()
                        ? PostgresMaterialSql.DEFAULT_WORKSPACE_KEY
                        : safeMetadata.workspaceKey(),
                    MaterialMetadataJdbcMapper.serialize(safeMetadata),
                    Timestamp.from(updatedAt),
                    UUID.fromString(materialId)
                );
                jdbcTemplate.update("DELETE FROM material_tags WHERE material_id = ?", UUID.fromString(materialId));
                tagDao.insertTags(materialId, safeMetadata);
                return findById(materialId).orElse(null);
            });
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "material.storage_write_failed",
                "Unable to update material metadata in PostgreSQL",
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
    public int countMaterialsByWorkspace(String workspaceKey) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM materials WHERE LOWER(COALESCE(workspace_key, '')) = ?",
            Integer.class,
            lowerCase(workspaceKey)
        );
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
            "SELECT COUNT(*) FROM materials m WHERE " + filterSqlBuilder.retrievalReadyPredicate("m"),
            Integer.class
        );
        return count == null ? 0 : count;
    }

    @Override
    public MaterialRetrievalScopeSnapshot describeRetrievalScope(
        KnowledgeScope knowledgeScope,
        RetrievalFilters retrievalFilters,
        Instant uploadedAfterInclusive,
        Instant uploadedBeforeExclusive
    ) {
        PostgresMaterialFilterSqlBuilder.RetrievalScopeSql scopeSql = filterSqlBuilder.buildRetrievalScopeSql(
            knowledgeScope,
            uploadedAfterInclusive,
            uploadedBeforeExclusive
        );
        PostgresMaterialFilterSqlBuilder.SearchFilterSql filterSql = filterSqlBuilder.buildSearchFilterSql(retrievalFilters);
        String readyPredicate = filterSqlBuilder.retrievalReadyPredicate("m", knowledgeScope, retrievalFilters);
        String scopedReadyPredicate = filterSqlBuilder.retrievalReadyPredicate(null, knowledgeScope, retrievalFilters);
        try {
            return jdbcTemplate.query(
                """
                    WITH scoped AS (
                        SELECT
                            m.id,
                            m.version_state,
                            m.indexing_status,
                            m.document_status,
                            m.period_start,
                            m.period_end
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
                        (SELECT COUNT(*) FROM materials m
                            WHERE """
                    + readyPredicate
                    + """
                        ) AS ready_material_count,
                        (SELECT COUNT(*) FROM scoped) AS scoped_material_count,
                        (SELECT COUNT(*) FROM scoped WHERE version_state = 'ACTIVE') AS scoped_active_material_count,
                        (SELECT COUNT(*) FROM scoped
                            WHERE """
                    + scopedReadyPredicate
                    + """
                        ) AS scoped_ready_material_count
                    """,
                preparedStatement -> {
                    int parameterIndex = filterSqlBuilder.bindRetrievalScope(preparedStatement, 1, scopeSql);
                    filterSqlBuilder.bindSearchFilters(preparedStatement, parameterIndex, filterSql);
                },
                resultSet -> {
                    if (!resultSet.next()) {
                        return new MaterialRetrievalScopeSnapshot(0, 0, 0, 0, 0, 0);
                    }
                    return new MaterialRetrievalScopeSnapshot(
                        resultSet.getInt("material_count"),
                        resultSet.getInt("active_material_count"),
                        resultSet.getInt("ready_material_count"),
                        resultSet.getInt("scoped_material_count"),
                        resultSet.getInt("scoped_active_material_count"),
                        resultSet.getInt("scoped_ready_material_count")
                    );
                }
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

    @Override
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
                        + PostgresMaterialSql.MATERIAL_COLUMNS
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

    @Override
    public Optional<StoredMaterialRecord> findLatestBySourceKeyAndVersionState(
        String sourceKey,
        MaterialVersionState versionState
    ) {
        try {
            List<StoredMaterialRecord> records = tagDao.enrichMetadata(jdbcTemplate.query(
                """
                    SELECT
                        """
                    + PostgresMaterialSql.MATERIAL_COLUMNS
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
}
