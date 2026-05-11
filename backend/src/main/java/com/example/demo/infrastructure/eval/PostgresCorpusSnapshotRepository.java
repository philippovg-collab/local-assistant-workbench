package com.example.demo.infrastructure.eval;

import com.example.demo.error.ErrorType;
import com.example.demo.error.StorageException;
import com.example.demo.model.eval.CorpusSnapshot;
import com.example.demo.model.eval.CorpusSnapshotItem;
import com.example.demo.model.eval.EvalLifecycleStatus;
import com.example.demo.service.eval.port.CorpusSnapshotRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresCorpusSnapshotRepository implements CorpusSnapshotRepository {

    private final JdbcTemplate jdbcTemplate;
    private final EvalJsonSupport jsonSupport;

    public PostgresCorpusSnapshotRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.jsonSupport = new EvalJsonSupport(objectMapper);
    }

    @Override
    public List<CorpusSnapshot> findSnapshots() {
        try {
            return jdbcTemplate.query(snapshotSelectSql() + " ORDER BY created_at DESC, snapshot_key ASC", snapshotRowMapper(false));
        } catch (DataAccessException exception) {
            throw storageFailure("corpus_snapshot.storage_read_failed", "Unable to load corpus snapshots", exception);
        }
    }

    @Override
    public Optional<CorpusSnapshot> findSnapshot(String id) {
        try {
            return jdbcTemplate.query(
                snapshotSelectSql() + " WHERE id = ? LIMIT 1",
                snapshotRowMapper(true),
                uuid(id)
            ).stream().findFirst();
        } catch (DataAccessException exception) {
            throw storageFailure("corpus_snapshot.storage_read_failed", "Unable to load corpus snapshot", exception);
        }
    }

    @Override
    public CorpusSnapshot saveSnapshot(CorpusSnapshot snapshot) {
        Instant now = Instant.now();
        Instant createdAt = snapshot.createdAt() == null ? now : snapshot.createdAt();
        Instant updatedAt = snapshot.updatedAt() == null ? createdAt : snapshot.updatedAt();
        try {
            jdbcTemplate.update(
                """
                    INSERT INTO corpus_snapshots (
                        id,
                        snapshot_key,
                        status,
                        reference_instant,
                        material_set_hash,
                        search_state_hash,
                        config_hash,
                        manifest_jsonb,
                        total_item_count,
                        active_item_count,
                        superseded_item_count,
                        ready_item_count,
                        metadata_jsonb,
                        created_at,
                        updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?::jsonb, ?, ?)
                    ON CONFLICT (id) DO UPDATE
                    SET snapshot_key = EXCLUDED.snapshot_key,
                        status = EXCLUDED.status,
                        reference_instant = EXCLUDED.reference_instant,
                        material_set_hash = EXCLUDED.material_set_hash,
                        search_state_hash = EXCLUDED.search_state_hash,
                        config_hash = EXCLUDED.config_hash,
                        manifest_jsonb = EXCLUDED.manifest_jsonb,
                        total_item_count = EXCLUDED.total_item_count,
                        active_item_count = EXCLUDED.active_item_count,
                        superseded_item_count = EXCLUDED.superseded_item_count,
                        ready_item_count = EXCLUDED.ready_item_count,
                        metadata_jsonb = EXCLUDED.metadata_jsonb,
                        updated_at = EXCLUDED.updated_at
                    """,
                uuid(snapshot.id()),
                snapshot.snapshotKey(),
                snapshot.status().name(),
                Timestamp.from(snapshot.referenceInstant()),
                snapshot.materialSetHash(),
                snapshot.searchStateHash(),
                snapshot.configHash(),
                writeJson(snapshot.manifest()),
                snapshot.totalItemCount(),
                snapshot.activeItemCount(),
                snapshot.supersededItemCount(),
                snapshot.readyItemCount(),
                writeJson(snapshot.metadata()),
                Timestamp.from(createdAt),
                Timestamp.from(updatedAt)
            );
            return findSnapshot(snapshot.id()).orElse(snapshot);
        } catch (DataAccessException exception) {
            throw storageFailure("corpus_snapshot.storage_write_failed", "Unable to persist corpus snapshot", exception);
        }
    }

    @Override
    public CorpusSnapshotItem saveItem(CorpusSnapshotItem item) {
        Instant createdAt = item.createdAt() == null ? Instant.now() : item.createdAt();
        try {
            jdbcTemplate.update(
                """
                    INSERT INTO corpus_snapshot_items (
                        id,
                        snapshot_id,
                        material_id,
                        material_version_id,
                        title,
                        source_key,
                        version_state,
                        lineage_version,
                        version_label,
                        indexing_status,
                        document_number,
                        document_date,
                        document_type,
                        document_status,
                        workspace_key,
                        project_key,
                        language_code,
                        content_hash,
                        metadata_hash,
                        chunk_profile,
                        chunk_count,
                        chunk_set_hash,
                        metadata_jsonb,
                        material_created_at,
                        material_updated_at,
                        created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE
                    SET material_id = EXCLUDED.material_id,
                        material_version_id = EXCLUDED.material_version_id,
                        title = EXCLUDED.title,
                        source_key = EXCLUDED.source_key,
                        version_state = EXCLUDED.version_state,
                        lineage_version = EXCLUDED.lineage_version,
                        version_label = EXCLUDED.version_label,
                        indexing_status = EXCLUDED.indexing_status,
                        document_number = EXCLUDED.document_number,
                        document_date = EXCLUDED.document_date,
                        document_type = EXCLUDED.document_type,
                        document_status = EXCLUDED.document_status,
                        workspace_key = EXCLUDED.workspace_key,
                        project_key = EXCLUDED.project_key,
                        language_code = EXCLUDED.language_code,
                        content_hash = EXCLUDED.content_hash,
                        metadata_hash = EXCLUDED.metadata_hash,
                        chunk_profile = EXCLUDED.chunk_profile,
                        chunk_count = EXCLUDED.chunk_count,
                        chunk_set_hash = EXCLUDED.chunk_set_hash,
                        metadata_jsonb = EXCLUDED.metadata_jsonb,
                        material_created_at = EXCLUDED.material_created_at,
                        material_updated_at = EXCLUDED.material_updated_at
                    """,
                uuid(item.id()),
                uuid(item.snapshotId()),
                uuid(item.materialId()),
                nullableUuid(item.materialVersionId()),
                item.title(),
                item.sourceKey(),
                item.versionState(),
                item.lineageVersion(),
                item.versionLabel(),
                item.indexingStatus(),
                item.documentNumber(),
                item.documentDate(),
                item.documentType(),
                item.documentStatus(),
                item.workspaceKey(),
                item.projectKey(),
                item.languageCode(),
                item.contentHash(),
                item.metadataHash(),
                item.chunkProfile(),
                item.chunkCount(),
                item.chunkSetHash(),
                writeJson(item.metadata()),
                timestampOrNull(item.materialCreatedAt()),
                timestampOrNull(item.materialUpdatedAt()),
                Timestamp.from(createdAt)
            );
            return findItemById(item.id()).orElse(item);
        } catch (DataAccessException exception) {
            throw storageFailure("corpus_snapshot_item.storage_write_failed", "Unable to persist corpus snapshot item", exception);
        }
    }

    @Override
    public List<CorpusSnapshotItem> findItemsBySnapshotId(String snapshotId) {
        try {
            return jdbcTemplate.query(
                itemSelectSql() + " WHERE snapshot_id = ? ORDER BY source_key ASC NULLS LAST, lineage_version ASC NULLS LAST, material_id ASC",
                itemRowMapper(),
                uuid(snapshotId)
            );
        } catch (DataAccessException exception) {
            throw storageFailure("corpus_snapshot_item.storage_read_failed", "Unable to load corpus snapshot items", exception);
        }
    }

    @Override
    public List<CorpusSnapshotItem> findItemsBySnapshotId(String snapshotId, int offset, int limit) {
        try {
            return jdbcTemplate.query(
                itemSelectSql()
                    + " WHERE snapshot_id = ? ORDER BY source_key ASC NULLS LAST, lineage_version ASC NULLS LAST, material_id ASC LIMIT ? OFFSET ?",
                itemRowMapper(),
                uuid(snapshotId),
                Math.max(0, limit),
                Math.max(0, offset)
            );
        } catch (DataAccessException exception) {
            throw storageFailure("corpus_snapshot_item.storage_read_failed", "Unable to load corpus snapshot items", exception);
        }
    }

    @Override
    public boolean isStorageReady() {
        try {
            Boolean ready = jdbcTemplate.queryForObject(
                """
                    SELECT to_regclass('public.corpus_snapshots') IS NOT NULL
                       AND to_regclass('public.corpus_snapshot_items') IS NOT NULL
                    """,
                Boolean.class
            );
            return Boolean.TRUE.equals(ready);
        } catch (DataAccessException exception) {
            throw storageFailure("corpus_snapshot.storage_read_failed", "Unable to check corpus snapshot storage readiness", exception);
        }
    }

    private Optional<CorpusSnapshotItem> findItemById(String id) {
        return jdbcTemplate.query(
            itemSelectSql() + " WHERE id = ? LIMIT 1",
            itemRowMapper(),
            uuid(id)
        ).stream().findFirst();
    }

    private String snapshotSelectSql() {
        return """
            SELECT id,
                   snapshot_key,
                   status,
                   reference_instant,
                   material_set_hash,
                   search_state_hash,
                   config_hash,
                   manifest_jsonb,
                   total_item_count,
                   active_item_count,
                   superseded_item_count,
                   ready_item_count,
                   metadata_jsonb,
                   created_at,
                   updated_at
            FROM corpus_snapshots
            """;
    }

    private String itemSelectSql() {
        return """
            SELECT id,
                   snapshot_id,
                   material_id,
                   material_version_id,
                   title,
                   source_key,
                   version_state,
                   lineage_version,
                   version_label,
                   indexing_status,
                   document_number,
                   document_date,
                   document_type,
                   document_status,
                   workspace_key,
                   project_key,
                   language_code,
                   content_hash,
                   metadata_hash,
                   chunk_profile,
                   chunk_count,
                   chunk_set_hash,
                   metadata_jsonb,
                   material_created_at,
                   material_updated_at,
                   created_at
            FROM corpus_snapshot_items
            """;
    }

    private RowMapper<CorpusSnapshot> snapshotRowMapper(boolean includeItems) {
        return (resultSet, rowNum) -> {
            String id = resultSet.getObject("id").toString();
            return new CorpusSnapshot(
                id,
                resultSet.getString("snapshot_key"),
                EvalLifecycleStatus.valueOf(resultSet.getString("status")),
                toInstant(resultSet.getTimestamp("reference_instant")),
                resultSet.getString("material_set_hash"),
                resultSet.getString("search_state_hash"),
                resultSet.getString("config_hash"),
                jsonSupport.readMap(resultSet.getString("manifest_jsonb"), "corpus_snapshot.json_read_failed", "Unable to read corpus snapshot manifest"),
                resultSet.getInt("total_item_count"),
                resultSet.getInt("active_item_count"),
                resultSet.getInt("superseded_item_count"),
                resultSet.getInt("ready_item_count"),
                jsonSupport.readMap(resultSet.getString("metadata_jsonb"), "corpus_snapshot.json_read_failed", "Unable to read corpus snapshot metadata"),
                includeItems ? findItemsBySnapshotId(id) : List.of(),
                toInstant(resultSet.getTimestamp("created_at")),
                toInstant(resultSet.getTimestamp("updated_at"))
            );
        };
    }

    private RowMapper<CorpusSnapshotItem> itemRowMapper() {
        return (resultSet, rowNum) -> new CorpusSnapshotItem(
            resultSet.getObject("id").toString(),
            resultSet.getObject("snapshot_id").toString(),
            resultSet.getObject("material_id").toString(),
            toStringOrNull(resultSet.getObject("material_version_id")),
            resultSet.getString("title"),
            resultSet.getString("source_key"),
            resultSet.getString("version_state"),
            resultSet.getObject("lineage_version", Integer.class),
            resultSet.getString("version_label"),
            resultSet.getString("indexing_status"),
            resultSet.getString("document_number"),
            resultSet.getObject("document_date", LocalDate.class),
            resultSet.getString("document_type"),
            resultSet.getString("document_status"),
            resultSet.getString("workspace_key"),
            resultSet.getString("project_key"),
            resultSet.getString("language_code"),
            resultSet.getString("content_hash"),
            resultSet.getString("metadata_hash"),
            resultSet.getString("chunk_profile"),
            resultSet.getObject("chunk_count", Integer.class),
            resultSet.getString("chunk_set_hash"),
            jsonSupport.readMap(resultSet.getString("metadata_jsonb"), "corpus_snapshot_item.json_read_failed", "Unable to read corpus snapshot item metadata"),
            toInstant(resultSet.getTimestamp("material_created_at")),
            toInstant(resultSet.getTimestamp("material_updated_at")),
            toInstant(resultSet.getTimestamp("created_at"))
        );
    }

    private String writeJson(Object value) {
        return jsonSupport.write(value, "corpus_snapshot.json_write_failed", "Unable to serialize corpus snapshot payload");
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

    private Timestamp timestampOrNull(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private StorageException storageFailure(String code, String message, DataAccessException exception) {
        return new StorageException(ErrorType.STORAGE_FAILURE, code, message, exception);
    }
}
