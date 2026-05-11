package com.example.demo.service;

import com.example.demo.error.ApplicationException;
import com.example.demo.error.ErrorType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class ElasticsearchMappingDefinition {

    private static final String INDEX_DEFINITION_RESOURCE = "elasticsearch/searchable-chunks-index.json";

    private final ObjectMapper objectMapper;
    private final String indexDefinitionJson;
    private final JsonNode indexDefinition;
    private final String canonicalJson;
    private final String mappingHash;

    public ElasticsearchMappingDefinition(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        this.indexDefinitionJson = loadIndexDefinitionJson();
        this.indexDefinition = parseIndexDefinition(indexDefinitionJson);
        this.canonicalJson = canonicalJson(indexDefinition);
        this.mappingHash = sha256(canonicalJson);
    }

    public String indexDefinitionJson() {
        return indexDefinitionJson;
    }

    public String canonicalJson() {
        return canonicalJson;
    }

    public String mappingHash() {
        return mappingHash;
    }

    public String dynamicMode() {
        return indexDefinition.path("mappings").path("dynamic").asText(null);
    }

    public Set<String> mappedFields() {
        TreeSet<String> fields = new TreeSet<>();
        Iterator<String> fieldNames = indexDefinition.path("mappings").path("properties").fieldNames();
        while (fieldNames.hasNext()) {
            fields.add(fieldNames.next());
        }
        return Set.copyOf(fields);
    }

    private String loadIndexDefinitionJson() {
        ClassPathResource resource = new ClassPathResource(INDEX_DEFINITION_RESOURCE);
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new ApplicationException(
                ErrorType.INTERNAL,
                "search.mapping_definition_unavailable",
                "Unable to load Elasticsearch index definition resource '" + INDEX_DEFINITION_RESOURCE + "'",
                exception
            );
        }
    }

    private JsonNode parseIndexDefinition(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new ApplicationException(
                ErrorType.INTERNAL,
                "search.mapping_definition_invalid",
                "Unable to parse Elasticsearch index definition resource '" + INDEX_DEFINITION_RESOURCE + "'",
                exception
            );
        }
    }

    private String canonicalJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(canonicalize(node));
        } catch (JsonProcessingException exception) {
            throw new ApplicationException(
                ErrorType.INTERNAL,
                "search.mapping_hash_failed",
                "Unable to serialize Elasticsearch mapping fingerprint payload",
                exception
            );
        }
    }

    private JsonNode canonicalize(JsonNode node) {
        if (node == null || node.isNull() || node.isValueNode()) {
            return node == null ? JsonNodeFactory.instance.nullNode() : node;
        }
        if (node.isArray()) {
            ArrayNode arrayNode = JsonNodeFactory.instance.arrayNode();
            node.forEach(item -> arrayNode.add(canonicalize(item)));
            return arrayNode;
        }
        if (node.isObject()) {
            ObjectNode objectNode = JsonNodeFactory.instance.objectNode();
            TreeMap<String, JsonNode> orderedFields = new TreeMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                orderedFields.put(field.getKey(), canonicalize(field.getValue()));
            }
            orderedFields.forEach(objectNode::set);
            return objectNode;
        }
        return node;
    }

    private String sha256(String value) {
        try {
            byte[] payload = value.getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (NoSuchAlgorithmException exception) {
            throw new ApplicationException(
                ErrorType.INTERNAL,
                "search.mapping_hash_unavailable",
                "SHA-256 digest is unavailable",
                exception
            );
        }
    }
}
