package com.example.demo.infrastructure.material;

import static com.example.demo.infrastructure.material.MaterialJdbcRowMappers.MATERIAL_ROW_MAPPER;

import com.example.demo.model.DocumentStatus;
import com.example.demo.model.MaterialIndexingStatus;
import com.example.demo.model.MaterialMetadataSnapshot;
import com.example.demo.model.MaterialVersionState;
import com.example.demo.service.material.StoredMaterialRecord;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

final class PostgresMaterialRecordDao {

    private final JdbcTemplate jdbcTemplate;
    private final PostgresMaterialTagDao tagDao;

    PostgresMaterialRecordDao(JdbcTemplate jdbcTemplate, PostgresMaterialTagDao tagDao) {
        this.jdbcTemplate = jdbcTemplate;
        this.tagDao = tagDao;
    }

    List<StoredMaterialRecord> findAll() {
        return tagDao.enrichMetadata(jdbcTemplate.query(
            "SELECT " + PostgresMaterialSql.MATERIAL_COLUMNS + " FROM materials ORDER BY created_at DESC",
            MATERIAL_ROW_MAPPER
        ));
    }

    List<StoredMaterialRecord> findActivePageAfter(Instant createdAt, String id, int limit) {
        if (limit <= 0) {
            return List.of();
        }

        if (createdAt == null || id == null) {
            return tagDao.enrichMetadata(jdbcTemplate.query(
                """
                    SELECT
                        """
                    + PostgresMaterialSql.MATERIAL_COLUMNS
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

        return tagDao.enrichMetadata(jdbcTemplate.query(
            """
                SELECT
                    """
                + PostgresMaterialSql.MATERIAL_COLUMNS
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
    }

    Optional<StoredMaterialRecord> findById(String id) {
        List<StoredMaterialRecord> records = tagDao.enrichMetadata(jdbcTemplate.query(
            "SELECT " + PostgresMaterialSql.MATERIAL_COLUMNS + " FROM materials WHERE id = ? LIMIT 1",
            MATERIAL_ROW_MAPPER,
            UUID.fromString(id)
        ));
        return records.stream().findFirst();
    }

    List<StoredMaterialRecord> findByIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<String> orderedIds = ids.stream()
            .filter(id -> id != null && !id.isBlank())
            .distinct()
            .toList();
        if (orderedIds.isEmpty()) {
            return List.of();
        }

        String placeholders = String.join(", ", Collections.nCopies(orderedIds.size(), "?"));
        List<StoredMaterialRecord> records = tagDao.enrichMetadata(jdbcTemplate.query(
            "SELECT " + PostgresMaterialSql.MATERIAL_COLUMNS + " FROM materials WHERE id IN (" + placeholders + ")",
            MATERIAL_ROW_MAPPER,
            orderedIds.stream().map(UUID::fromString).toArray()
        ));
        java.util.Map<String, StoredMaterialRecord> recordsById = records.stream()
            .collect(java.util.stream.Collectors.toMap(
                StoredMaterialRecord::id,
                record -> record,
                (left, right) -> left,
                LinkedHashMap::new
            ));
        return orderedIds.stream()
            .map(recordsById::get)
            .filter(java.util.Objects::nonNull)
            .toList();
    }

    Optional<String> findSourceKeyById(String id) {
        return jdbcTemplate.query(
            "SELECT source_key FROM materials WHERE id = ? LIMIT 1",
            (resultSet, rowNum) -> resultSet.getString("source_key"),
            UUID.fromString(id)
        ).stream().findFirst();
    }

    Optional<StoredMaterialRecord> findBySourceKeyAndContentHash(String sourceKey, String contentHash) {
        List<StoredMaterialRecord> records = tagDao.enrichMetadata(jdbcTemplate.query(
            """
                SELECT
                    """
                + PostgresMaterialSql.MATERIAL_COLUMNS
                + """
                FROM materials
                WHERE source_key = ? AND content_hash = ?
                ORDER BY
                    CASE WHEN version_state = 'ACTIVE' THEN 0 ELSE 1 END,
                    lineage_version DESC,
                    created_at DESC,
                    id DESC
                LIMIT 1
                """,
            MATERIAL_ROW_MAPPER,
            sourceKey,
            contentHash
        ));
        return records.stream().findFirst();
    }

    List<StoredMaterialRecord> findAllBySourceKey(String sourceKey) {
        return tagDao.enrichMetadata(jdbcTemplate.query(
            """
                SELECT
                    """
                + PostgresMaterialSql.MATERIAL_COLUMNS
                + """
                FROM materials
                WHERE source_key = ?
                ORDER BY lineage_version DESC, created_at DESC, id DESC
                """,
            MATERIAL_ROW_MAPPER,
            sourceKey
        ));
    }

    StoredMaterialRecord insertMaterial(StoredMaterialRecord record, String chunkProfile) {
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
                    project_key,
                    counterparty,
                    business_status,
                    document_status,
                    period_start,
                    period_end,
                    knowledge_document_class,
                    workspace_key,
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
                ) VALUES (
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?::jsonb,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    ?,
                    NULL,
                    ?,
                    ?,
                    ?,
                    ?
                )
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
            persistedRecord.metadata().languageCode() == null ? null : persistedRecord.metadata().languageCode().name(),
            persistedRecord.metadata().sourceTrust().name(),
            persistedRecord.metadata().project(),
            persistedRecord.metadata().projectKey(),
            persistedRecord.metadata().counterparty(),
            persistedRecord.metadata().businessStatus(),
            persistedRecord.metadata().documentStatus().name(),
            persistedRecord.metadata().periodStart(),
            persistedRecord.metadata().periodEnd(),
            persistedRecord.metadata().knowledgeDocumentClass().name(),
            workspaceKeyOrDefault(persistedRecord.metadata().workspaceKey()),
            MaterialMetadataJdbcMapper.serialize(persistedRecord.metadata()),
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

    boolean lockMaterialIfPresent(String materialId) {
        List<Integer> matches = jdbcTemplate.query(
            "SELECT 1 FROM materials WHERE id = ? FOR UPDATE",
            (resultSet, rowNum) -> resultSet.getInt(1),
            UUID.fromString(materialId)
        );
        return !matches.isEmpty();
    }

    boolean isSearchable(StoredMaterialRecord record) {
        LocalDate today = LocalDate.now();
        MaterialMetadataSnapshot metadata = record.metadata() == null ? MaterialMetadataSnapshot.empty() : record.metadata();
        return record.versionState() == MaterialVersionState.ACTIVE
            && (record.status() == MaterialIndexingStatus.READY || record.status() == MaterialIndexingStatus.PARTIAL_READY)
            && metadata.documentStatus() == DocumentStatus.ACTIVE
            && (metadata.periodStart() == null || !metadata.periodStart().isAfter(today))
            && (metadata.periodEnd() == null || !metadata.periodEnd().isBefore(today));
    }

    private String workspaceKeyOrDefault(String workspaceKey) {
        return workspaceKey == null || workspaceKey.isBlank() ? PostgresMaterialSql.DEFAULT_WORKSPACE_KEY : workspaceKey;
    }

    private int nextLineageVersion(String sourceKey) {
        Integer currentMax = jdbcTemplate.queryForObject(
            "SELECT COALESCE(MAX(lineage_version), 0) FROM materials WHERE source_key = ?",
            Integer.class,
            sourceKey
        );
        return (currentMax == null ? 0 : currentMax) + 1;
    }
}
