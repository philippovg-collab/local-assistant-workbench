package com.example.demo.infrastructure.material;

import com.example.demo.model.DocumentType;
import com.example.demo.model.KnowledgeDocumentClass;
import com.example.demo.model.MaterialMetadataProvenance;
import com.example.demo.model.MaterialMetadataSnapshot;
import com.example.demo.model.SourceTrustLevel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

final class MaterialMetadataJdbcMapper {

    private static final ObjectMapper JSON_MAPPER = JsonMapper.builder()
        .findAndAddModules()
        .build();

    private MaterialMetadataJdbcMapper() {
    }

    static MaterialMetadataSnapshot fromResultSet(ResultSet resultSet) throws SQLException {
        MaterialMetadataPersistencePayload payload = deserializePayload(resultSet.getString("metadata_jsonb"));
        return new MaterialMetadataSnapshot(
            DocumentType.valueOf(resultSet.getString("document_type")),
            knowledgeDocumentClassOf(resultSet.getString("knowledge_document_class"), payload.knowledgeDocumentClass()),
            toLocalDateOrNull(resultSet.getDate("document_date")),
            resultSet.getString("document_number"),
            resultSet.getString("author_name"),
            resultSet.getString("department"),
            resultSet.getString("version_label"),
            resultSet.getString("language_code"),
            List.of(),
            SourceTrustLevel.valueOf(resultSet.getString("source_trust")),
            resultSet.getString("project_name"),
            firstNonBlank(resultSet.getString("workspace_key"), payload.workspaceKey()),
            resultSet.getString("counterparty"),
            resultSet.getString("business_status"),
            toLocalDateOrNull(resultSet.getDate("period_start")),
            toLocalDateOrNull(resultSet.getDate("period_end")),
            payload.provenance()
        );
    }

    static String serialize(MaterialMetadataSnapshot metadata) {
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

    private static MaterialMetadataPersistencePayload deserializePayload(String rawJson) {
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

    private static String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback;
    }

    private static KnowledgeDocumentClass knowledgeDocumentClassOf(
        String storedValue,
        KnowledgeDocumentClass fallback
    ) {
        if (storedValue == null || storedValue.isBlank()) {
            return fallback;
        }
        return KnowledgeDocumentClass.valueOf(storedValue);
    }

    private static LocalDate toLocalDateOrNull(java.sql.Date value) {
        return value == null ? null : value.toLocalDate();
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
}
