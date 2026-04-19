package com.example.demo.infrastructure.material;

import com.example.demo.model.MaterialIndexingStatus;
import com.example.demo.model.MaterialSummary;
import com.example.demo.model.MaterialVersionState;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;

final class MaterialJdbcRowMappers {

    static final RowMapper<StoredMaterialRecord> MATERIAL_ROW_MAPPER = (resultSet, rowNum) -> new StoredMaterialRecord(
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
        MaterialMetadataJdbcMapper.fromResultSet(resultSet)
    );

    static final RowMapper<MaterialSummary> MATERIAL_SUMMARY_ROW_MAPPER = (resultSet, rowNum) -> new MaterialSummary(
        resultSet.getObject("id").toString(),
        resultSet.getString("title"),
        resultSet.getString("source_type"),
        resultSet.getString("original_file_name"),
        MaterialIndexingStatus.valueOf(resultSet.getString("indexing_status")),
        MaterialVersionState.valueOf(resultSet.getString("version_state")),
        resultSet.getString("status_reason_code"),
        resultSet.getString("status_reason_message"),
        toInstant(resultSet.getTimestamp("created_at")),
        toInstant(resultSet.getTimestamp("updated_at")),
        resultSet.getInt("indexing_attempts"),
        toInstantOrNull(resultSet.getTimestamp("next_retry_at")),
        resultSet.getInt("content_length"),
        resultSet.getString("preview"),
        MaterialMetadataJdbcMapper.fromResultSet(resultSet)
    );

    static final RowMapper<MaterialChunkSearchMatch> CHUNK_SEARCH_ROW_MAPPER = (resultSet, rowNum) -> new MaterialChunkSearchMatch(
        resultSet.getObject("material_id").toString(),
        resultSet.getInt("chunk_index"),
        resultSet.getString("title"),
        resultSet.getString("chunk_text"),
        resultSet.getObject("page", Integer.class),
        resultSet.getString("extractor"),
        resultSet.getBoolean("ocr_used"),
        chunkTypeOf(resultSet.getString("chunk_type")),
        nullableDouble(resultSet, "semantic_distance"),
        nullableDouble(resultSet, "lexical_score")
    );

    static final RowMapper<StoredMaterialChunk> RAW_CHUNK_ROW_MAPPER = (resultSet, rowNum) -> new StoredMaterialChunk(
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

    static final RowMapper<StoredMaterialSegment> SEGMENT_ROW_MAPPER = (resultSet, rowNum) -> new StoredMaterialSegment(
        resultSet.getInt("segment_index"),
        resultSet.getString("segment_text"),
        resultSet.getObject("page", Integer.class),
        resultSet.getString("extractor"),
        resultSet.getBoolean("ocr_used")
    );

    static final RowMapper<SearchableMaterialChunkSnapshot> SEARCHABLE_CHUNK_ROW_MAPPER = (resultSet, rowNum) ->
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

    static final RowMapper<MaterialSearchSyncQueueEntry> SEARCH_SYNC_QUEUE_ROW_MAPPER = (resultSet, rowNum) ->
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

    private MaterialJdbcRowMappers() {
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

    private static List<String> textArrayOf(Array sqlArray) throws SQLException {
        if (sqlArray == null) {
            return List.of();
        }
        Object array = sqlArray.getArray();
        if (array instanceof String[] strings) {
            return List.of(strings);
        }
        if (array instanceof Object[] objects) {
            return java.util.Arrays.stream(objects)
                .map(String::valueOf)
                .toList();
        }
        return List.of();
    }

    private static Double nullableDouble(ResultSet resultSet, String columnLabel) throws SQLException {
        double value = resultSet.getDouble(columnLabel);
        return resultSet.wasNull() ? null : value;
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? Instant.now() : timestamp.toInstant();
    }

    private static Instant toInstantOrNull(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
