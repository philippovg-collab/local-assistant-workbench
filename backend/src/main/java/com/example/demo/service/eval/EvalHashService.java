package com.example.demo.service.eval;

import com.example.demo.error.ApplicationException;
import com.example.demo.error.ErrorType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

@Service
public class EvalHashService {

    private final ObjectMapper objectMapper;

    public EvalHashService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    public String hash(Object value) {
        return sha256(canonicalJson(value));
    }

    public String canonicalJson(Object value) {
        try {
            return objectMapper.writeValueAsString(canonicalize(objectMapper.valueToTree(value)));
        } catch (JsonProcessingException exception) {
            throw new ApplicationException(
                ErrorType.INTERNAL,
                "eval.hash_json_failed",
                "Unable to serialize eval fingerprint payload",
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
                "eval.hash_unavailable",
                "SHA-256 digest is unavailable",
                exception
            );
        }
    }
}
