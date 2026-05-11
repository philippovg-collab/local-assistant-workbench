package com.example.demo.infrastructure.eval;

import com.example.demo.error.StorageException;
import com.example.demo.model.EvidenceLocator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;

final class EvalJdbcJsonMapper {

    static final TypeReference<List<List<EvidenceLocator>>> LOCATOR_GROUPS = new TypeReference<>() {
    };
    static final TypeReference<List<Map<String, Object>>> CASE_REVISION_REFS = new TypeReference<>() {
    };

    private final EvalJsonSupport jsonSupport;

    EvalJdbcJsonMapper(ObjectMapper objectMapper) {
        this.jsonSupport = new EvalJsonSupport(objectMapper);
    }

    String write(Object value) {
        return jsonSupport.write(value, "eval_dataset.json_write_failed", "Unable to serialize eval dataset payload");
    }

    List<String> readStringList(String value, String code, String message) {
        return jsonSupport.readStringList(value, code, message);
    }

    <T> List<T> readList(String value, Class<T> itemType, String code, String message) {
        return jsonSupport.readList(value, itemType, code, message);
    }

    <T> T read(String value, Class<T> type, String code, String message) {
        return jsonSupport.read(value, type, code, message);
    }

    <T> T read(String value, TypeReference<T> type, String code, String message) {
        return jsonSupport.read(value, type, code, message);
    }

    Map<String, Object> readMap(String value, String code, String message) {
        return jsonSupport.readMap(value, code, message);
    }

    List<String> readGoldFacts(String value, String code) {
        try {
            return readStringList(value, code, "Unable to read eval case gold facts");
        } catch (StorageException ignored) {
            Map<String, Object> payload = readMap(value, code, "Unable to read eval case gold facts");
            Object facts = payload.get("facts");
            if (!(facts instanceof List<?> list)) {
                return List.of();
            }
            return list.stream()
                .filter(item -> item instanceof String)
                .map(String.class::cast)
                .toList();
        }
    }
}
