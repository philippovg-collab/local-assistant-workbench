package com.example.demo.infrastructure.material;

import com.example.demo.model.MaterialMetadataSnapshot;
import com.example.demo.model.MaterialSummary;
import com.example.demo.service.material.StoredMaterialRecord;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

final class PostgresMaterialTagDao {

    private final JdbcTemplate jdbcTemplate;

    PostgresMaterialTagDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    List<StoredMaterialRecord> enrichMetadata(List<StoredMaterialRecord> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }

        Map<String, StoredTagLayers> tagsByMaterialId = loadTagsByMaterialIds(records.stream().map(StoredMaterialRecord::id).toList());
        return records.stream()
            .map(record -> {
                StoredTagLayers tagLayers = tagsByMaterialId.getOrDefault(record.id(), StoredTagLayers.empty());
                return record.withMetadata(record.metadata().withTagLayers(tagLayers.manualTags(), tagLayers.autoTags()));
            })
            .toList();
    }

    List<MaterialSummary> enrichSummaryMetadata(List<MaterialSummary> summaries) {
        if (summaries == null || summaries.isEmpty()) {
            return List.of();
        }

        Map<String, StoredTagLayers> tagsByMaterialId = loadTagsByMaterialIds(summaries.stream().map(MaterialSummary::id).toList());
        return summaries.stream()
            .map(summary -> {
                StoredTagLayers tagLayers = tagsByMaterialId.getOrDefault(summary.id(), StoredTagLayers.empty());
                return new MaterialSummary(
                    summary.id(),
                    summary.title(),
                    summary.sourceType(),
                    summary.originalFileName(),
                    summary.status(),
                    summary.versionState(),
                    summary.statusReasonCode(),
                    summary.statusReasonMessage(),
                    summary.createdAt(),
                    summary.updatedAt(),
                    summary.indexingAttempts(),
                    summary.nextRetryAt(),
                    summary.contentLength(),
                    summary.preview(),
                    summary.metadata().withTagLayers(tagLayers.manualTags(), tagLayers.autoTags())
                );
            })
            .toList();
    }

    void insertTags(String materialId, MaterialMetadataSnapshot metadata) {
        if (metadata == null) {
            return;
        }

        List<StoredTagRow> tags = new ArrayList<>();
        int tagOrder = 0;
        for (String tag : metadata.manualTags()) {
            tags.add(new StoredTagRow(tagOrder++, tag, "MANUAL"));
        }
        for (String tag : metadata.autoTags()) {
            tags.add(new StoredTagRow(tagOrder++, tag, "AUTO"));
        }
        if (tags.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate(
            """
                INSERT INTO material_tags (
                    material_id,
                    tag_order,
                    tag_value,
                    tag_source
                ) VALUES (?, ?, ?, ?)
                """,
            new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement preparedStatement, int index) throws SQLException {
                    StoredTagRow tag = tags.get(index);
                    preparedStatement.setObject(1, UUID.fromString(materialId));
                    preparedStatement.setInt(2, tag.order());
                    preparedStatement.setString(3, tag.value());
                    preparedStatement.setString(4, tag.source());
                }

                @Override
                public int getBatchSize() {
                    return tags.size();
                }
            }
        );
    }

    private Map<String, StoredTagLayers> loadTagsByMaterialIds(List<String> materialIds) {
        if (materialIds == null || materialIds.isEmpty()) {
            return Map.of();
        }

        String placeholders = String.join(", ", Collections.nCopies(materialIds.size(), "?"));
        Map<String, MutableTagLayers> tagsByMaterialId = new LinkedHashMap<>();
        jdbcTemplate.query(
            """
                SELECT material_id, tag_value, COALESCE(tag_source, 'MANUAL') AS tag_source
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
                MutableTagLayers tagLayers = tagsByMaterialId.computeIfAbsent(materialId, ignored -> new MutableTagLayers());
                String tagSource = resultSet.getString("tag_source");
                if ("AUTO".equals(tagSource)) {
                    tagLayers.autoTags().add(resultSet.getString("tag_value"));
                } else {
                    tagLayers.manualTags().add(resultSet.getString("tag_value"));
                }
            },
            materialIds.stream().map(UUID::fromString).toArray()
        );
        Map<String, StoredTagLayers> immutableTagsByMaterialId = new LinkedHashMap<>();
        tagsByMaterialId.forEach((materialId, tagLayers) -> immutableTagsByMaterialId.put(
            materialId,
            new StoredTagLayers(List.copyOf(tagLayers.manualTags()), List.copyOf(tagLayers.autoTags()))
        ));
        return Map.copyOf(immutableTagsByMaterialId);
    }

    private record MutableTagLayers(
        List<String> manualTags,
        List<String> autoTags
    ) {
        private MutableTagLayers() {
            this(new ArrayList<>(), new ArrayList<>());
        }
    }

    private record StoredTagLayers(
        List<String> manualTags,
        List<String> autoTags
    ) {
        private static StoredTagLayers empty() {
            return new StoredTagLayers(List.of(), List.of());
        }
    }

    private record StoredTagRow(
        int order,
        String value,
        String source
    ) {
    }
}
