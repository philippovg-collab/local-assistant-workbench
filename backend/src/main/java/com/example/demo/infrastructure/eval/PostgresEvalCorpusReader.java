package com.example.demo.infrastructure.eval;

import com.example.demo.error.ErrorType;
import com.example.demo.error.StorageException;
import com.example.demo.service.eval.EvalCorpusChunk;
import com.example.demo.service.eval.EvalCorpusMaterial;
import com.example.demo.service.eval.port.EvalCorpusReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresEvalCorpusReader implements EvalCorpusReader {

    private final JdbcTemplate jdbcTemplate;
    private final EvalJsonSupport jsonSupport;

    public PostgresEvalCorpusReader(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.jsonSupport = new EvalJsonSupport(objectMapper);
    }

    @Override
    public List<EvalCorpusMaterial> readSnapshotMaterials(boolean includeSuperseded) {
        try {
            return jdbcTemplate.query(
                """
                    SELECT m.id,
                           m.title,
                           m.source_key,
                           m.version_state,
                           m.lineage_version,
                           m.version_label,
                           m.indexing_status,
                           m.document_number,
                           m.document_date,
                           m.document_type,
                           m.document_status,
                           m.workspace_key,
                           m.project_key,
                           m.language_code,
                           m.content_hash,
                           m.chunk_profile,
                           m.metadata_jsonb,
                           m.created_at,
                           m.updated_at,
                           c.chunk_index,
                           c.chunk_text,
                           c.page,
                           c.chunk_type,
                           c.section_path,
                           c.heading_trail,
                           c.table_id,
                           c.slide_id,
                           c.parser_confidence
                    FROM materials m
                    LEFT JOIN material_chunks c ON c.material_id = m.id
                    WHERE m.indexing_status IN ('READY', 'PARTIAL_READY')
                      AND m.version_state IN ('ACTIVE', 'SUPERSEDED')
                      AND (? = TRUE OR m.version_state = 'ACTIVE')
                    ORDER BY m.source_key ASC, m.lineage_version ASC, m.id ASC, c.chunk_index ASC
                    """,
                preparedStatement -> preparedStatement.setBoolean(1, includeSuperseded),
                (org.springframework.jdbc.core.ResultSetExtractor<List<EvalCorpusMaterial>>) this::readMaterials
            );
        } catch (DataAccessException exception) {
            throw storageFailure("eval_corpus.storage_read_failed", "Unable to read corpus materials for eval snapshot", exception);
        }
    }

    @Override
    public Map<String, Object> readRevisionPins() {
        Map<String, Object> pins = new LinkedHashMap<>();
        pins.put("knowledgeFilters", jdbcTemplate.query(
            """
                SELECT id::text AS id, filter_kind, revision
                FROM knowledge_presets
                WHERE is_active = TRUE
                ORDER BY filter_kind ASC, id ASC
                """,
            (resultSet, rowNum) -> Map.<String, Object>of(
                "id", resultSet.getString("id"),
                "kind", resultSet.getString("filter_kind"),
                "revision", resultSet.getInt("revision")
            )
        ));
        pins.put("instructions", jdbcTemplate.query(
            """
                SELECT id::text AS id, scope_level, scope_target_id, revision
                FROM instructions
                WHERE is_active = TRUE
                ORDER BY scope_level ASC, scope_target_id ASC NULLS FIRST, id ASC
                """,
            (resultSet, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", resultSet.getString("id"));
                row.put("scopeLevel", resultSet.getString("scope_level"));
                row.put("scopeTargetId", resultSet.getString("scope_target_id"));
                row.put("revision", resultSet.getInt("revision"));
                return row;
            }
        ));
        return pins;
    }

    @Override
    public List<String> findMissingRevisionPins(Map<String, Object> revisionPins) {
        if (revisionPins == null || revisionPins.isEmpty()) {
            return List.of();
        }
        List<String> missing = new ArrayList<>();
        for (Map<String, Object> pin : mapItems(revisionPins.get("knowledgeFilters"))) {
            if (!exists(
                "SELECT EXISTS (SELECT 1 FROM knowledge_preset_revisions WHERE preset_id = ?::uuid AND revision = ?)",
                text(pin.get("id")),
                integer(pin.get("revision"))
            )) {
                missing.add("knowledgeFilter:" + text(pin.get("id")) + "@" + integer(pin.get("revision")));
            }
        }
        for (Map<String, Object> pin : mapItems(revisionPins.get("instructions"))) {
            if (!exists(
                "SELECT EXISTS (SELECT 1 FROM instruction_revisions WHERE instruction_id = ?::uuid AND revision = ?)",
                text(pin.get("id")),
                integer(pin.get("revision"))
            )) {
                missing.add("instruction:" + text(pin.get("id")) + "@" + integer(pin.get("revision")));
            }
        }
        return missing;
    }

    private List<EvalCorpusMaterial> readMaterials(ResultSet resultSet) throws SQLException {
        List<EvalCorpusMaterial> materials = new ArrayList<>();
        MaterialBuilder current = null;
        while (resultSet.next()) {
            String materialId = resultSet.getObject("id").toString();
            if (current == null || !current.materialId.equals(materialId)) {
                if (current != null) {
                    materials.add(current.build());
                }
                current = MaterialBuilder.from(resultSet, jsonSupport);
            }
            if (resultSet.getObject("chunk_index") != null) {
                current.chunks.add(new EvalCorpusChunk(
                    resultSet.getInt("chunk_index"),
                    resultSet.getString("chunk_text"),
                    resultSet.getObject("page", Integer.class),
                    resultSet.getString("chunk_type"),
                    textArrayOf(resultSet.getArray("section_path")),
                    textArrayOf(resultSet.getArray("heading_trail")),
                    resultSet.getString("table_id"),
                    resultSet.getString("slide_id"),
                    resultSet.getString("parser_confidence")
                ));
            }
        }
        if (current != null) {
            materials.add(current.build());
        }
        return materials;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mapItems(Object rawValue) {
        if (!(rawValue instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
            .filter(Map.class::isInstance)
            .map(item -> (Map<String, Object>) item)
            .toList();
    }

    private boolean exists(String sql, String id, Integer revision) {
        if (id == null || revision == null) {
            return false;
        }
        Boolean exists = jdbcTemplate.queryForObject(sql, Boolean.class, id, revision);
        return Boolean.TRUE.equals(exists);
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        return Integer.valueOf(String.valueOf(value));
    }

    private List<String> textArrayOf(Array sqlArray) throws SQLException {
        if (sqlArray == null) {
            return List.of();
        }
        Object value = sqlArray.getArray();
        if (value instanceof String[] strings) {
            return List.of(strings);
        }
        if (value instanceof Object[] objects) {
            return java.util.Arrays.stream(objects).map(String::valueOf).toList();
        }
        return List.of();
    }

    private StorageException storageFailure(String code, String message, DataAccessException exception) {
        return new StorageException(ErrorType.STORAGE_FAILURE, code, message, exception);
    }

    private static final class MaterialBuilder {
        private final String materialId;
        private final String title;
        private final String sourceKey;
        private final String versionState;
        private final int lineageVersion;
        private final String versionLabel;
        private final String indexingStatus;
        private final String documentNumber;
        private final LocalDate documentDate;
        private final String documentType;
        private final String documentStatus;
        private final String workspaceKey;
        private final String projectKey;
        private final String languageCode;
        private final String contentHash;
        private final String chunkProfile;
        private final Map<String, Object> metadata;
        private final Instant createdAt;
        private final Instant updatedAt;
        private final List<EvalCorpusChunk> chunks = new ArrayList<>();

        private MaterialBuilder(
            String materialId,
            String title,
            String sourceKey,
            String versionState,
            int lineageVersion,
            String versionLabel,
            String indexingStatus,
            String documentNumber,
            LocalDate documentDate,
            String documentType,
            String documentStatus,
            String workspaceKey,
            String projectKey,
            String languageCode,
            String contentHash,
            String chunkProfile,
            Map<String, Object> metadata,
            Instant createdAt,
            Instant updatedAt
        ) {
            this.materialId = materialId;
            this.title = title;
            this.sourceKey = sourceKey;
            this.versionState = versionState;
            this.lineageVersion = lineageVersion;
            this.versionLabel = versionLabel;
            this.indexingStatus = indexingStatus;
            this.documentNumber = documentNumber;
            this.documentDate = documentDate;
            this.documentType = documentType;
            this.documentStatus = documentStatus;
            this.workspaceKey = workspaceKey;
            this.projectKey = projectKey;
            this.languageCode = languageCode;
            this.contentHash = contentHash;
            this.chunkProfile = chunkProfile;
            this.metadata = metadata;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        private static MaterialBuilder from(ResultSet resultSet, EvalJsonSupport jsonSupport) throws SQLException {
            return new MaterialBuilder(
                resultSet.getObject("id").toString(),
                resultSet.getString("title"),
                resultSet.getString("source_key"),
                resultSet.getString("version_state"),
                resultSet.getInt("lineage_version"),
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
                resultSet.getString("chunk_profile"),
                jsonSupport.readMap(resultSet.getString("metadata_jsonb"), "eval_corpus.json_read_failed", "Unable to read material metadata"),
                toInstant(resultSet, "created_at"),
                toInstant(resultSet, "updated_at")
            );
        }

        private EvalCorpusMaterial build() {
            return new EvalCorpusMaterial(
                materialId,
                title,
                sourceKey,
                versionState,
                lineageVersion,
                versionLabel,
                indexingStatus,
                documentNumber,
                documentDate,
                documentType,
                documentStatus,
                workspaceKey,
                projectKey,
                languageCode,
                contentHash,
                chunkProfile,
                metadata,
                createdAt,
                updatedAt,
                chunks
            );
        }

        private static Instant toInstant(ResultSet resultSet, String column) throws SQLException {
            java.sql.Timestamp timestamp = resultSet.getTimestamp(column);
            return timestamp == null ? null : timestamp.toInstant();
        }
    }
}
