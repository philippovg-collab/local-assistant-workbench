package com.example.demo.infrastructure.material;

import static com.example.demo.infrastructure.material.MaterialJdbcRowMappers.RAW_CHUNK_ROW_MAPPER;
import static com.example.demo.infrastructure.material.MaterialJdbcRowMappers.SEARCHABLE_CHUNK_ROW_MAPPER;
import static com.example.demo.infrastructure.material.MaterialJdbcRowMappers.SEGMENT_ROW_MAPPER;

import com.example.demo.error.ErrorType;
import com.example.demo.error.StorageException;
import com.example.demo.service.material.SearchableMaterialChunkSnapshot;
import com.example.demo.service.material.StoredEmbeddedMaterialChunk;
import com.example.demo.service.material.StoredMaterialChunk;
import com.example.demo.service.material.StoredMaterialSegment;
import com.pgvector.PGvector;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

final class PostgresMaterialChunkDao {

    private final JdbcTemplate jdbcTemplate;

    PostgresMaterialChunkDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    List<StoredMaterialChunk> findChunks(String materialId) {
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
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "material.storage_read_failed",
                "Unable to load material chunks from PostgreSQL",
                exception
            );
        }
    }

    Map<String, List<StoredMaterialChunk>> findChunksByMaterialIds(Collection<String> materialIds) {
        if (materialIds == null || materialIds.isEmpty()) {
            return Map.of();
        }
        List<String> orderedIds = materialIds.stream()
            .filter(id -> id != null && !id.isBlank())
            .distinct()
            .toList();
        if (orderedIds.isEmpty()) {
            return Map.of();
        }

        UUID[] ids = orderedIds.stream().map(UUID::fromString).toArray(UUID[]::new);
        try {
            Map<String, List<StoredMaterialChunk>> chunksByMaterialId = jdbcTemplate.query(
                """
                    SELECT
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
                        parser_confidence
                    FROM material_chunks
                    WHERE material_id = ANY (?)
                    ORDER BY material_id ASC, chunk_index ASC
                    """,
                preparedStatement -> PostgresMaterialJdbcSupport.bindUuidArray(preparedStatement, 1, ids),
                resultSet -> {
                    Map<String, List<StoredMaterialChunk>> result = new LinkedHashMap<>();
                    int rowNumber = 0;
                    while (resultSet.next()) {
                        String materialId = resultSet.getObject("material_id").toString();
                        result.computeIfAbsent(materialId, ignored -> new java.util.ArrayList<>())
                            .add(RAW_CHUNK_ROW_MAPPER.mapRow(resultSet, rowNumber++));
                    }
                    return result;
                }
            );
            return chunksByMaterialId == null ? Map.of() : chunksByMaterialId;
        } catch (DataAccessException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "material.storage_read_failed",
                "Unable to bulk load material chunks from PostgreSQL",
                exception
            );
        }
    }

    List<StoredMaterialSegment> findSegments(String materialId) {
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
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "material.storage_read_failed",
                "Unable to load material segments from PostgreSQL",
                exception
            );
        }
    }

    List<SearchableMaterialChunkSnapshot> findSearchableChunks(String materialId) {
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
                SEARCHABLE_CHUNK_ROW_MAPPER,
                UUID.fromString(materialId)
            );
        } catch (DataAccessException exception) {
            throw new StorageException(
                ErrorType.STORAGE_FAILURE,
                "material.storage_read_failed",
                "Unable to resolve searchable material snapshot from PostgreSQL",
                exception
            );
        }
    }

    void insertRawChunks(String materialId, List<StoredMaterialChunk> chunks) {
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
                    PostgresMaterialJdbcSupport.bindTextArray(preparedStatement, 8, chunk.sectionPath());
                    PostgresMaterialJdbcSupport.bindTextArray(preparedStatement, 9, chunk.headingTrail());
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

    void insertSegments(String materialId, List<StoredMaterialSegment> segments) {
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

    void insertEmbeddedChunks(String materialId, List<StoredEmbeddedMaterialChunk> chunks) {
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
                    PostgresMaterialJdbcSupport.bindTextArray(preparedStatement, 8, chunk.sectionPath());
                    PostgresMaterialJdbcSupport.bindTextArray(preparedStatement, 9, chunk.headingTrail());
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
}
