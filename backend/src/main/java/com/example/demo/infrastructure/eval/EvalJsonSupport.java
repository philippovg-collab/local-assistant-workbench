package com.example.demo.infrastructure.eval;

import com.example.demo.error.ErrorType;
import com.example.demo.error.StorageException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;

final class EvalJsonSupport {

    static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    EvalJsonSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    String write(Object value, String code, String message) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new StorageException(ErrorType.STORAGE_FAILURE, code, message, exception);
        }
    }

    <T> T read(String value, Class<T> type, String code, String message) {
        try {
            return objectMapper.readValue(emptyObjectIfBlank(value), type);
        } catch (JsonProcessingException exception) {
            throw new StorageException(ErrorType.STORAGE_FAILURE, code, message, exception);
        }
    }

    <T> T read(String value, TypeReference<T> type, String code, String message) {
        try {
            return objectMapper.readValue(emptyObjectIfBlank(value), type);
        } catch (JsonProcessingException exception) {
            throw new StorageException(ErrorType.STORAGE_FAILURE, code, message, exception);
        }
    }

    List<String> readStringList(String value, String code, String message) {
        try {
            return objectMapper.readValue(emptyArrayIfBlank(value), STRING_LIST);
        } catch (JsonProcessingException exception) {
            throw new StorageException(ErrorType.STORAGE_FAILURE, code, message, exception);
        }
    }

    <T> List<T> readList(String value, Class<T> itemType, String code, String message) {
        try {
            return objectMapper.readValue(
                emptyArrayIfBlank(value),
                objectMapper.getTypeFactory().constructCollectionType(List.class, itemType)
            );
        } catch (JsonProcessingException exception) {
            throw new StorageException(ErrorType.STORAGE_FAILURE, code, message, exception);
        }
    }

    Map<String, Object> readMap(String value, String code, String message) {
        return read(value, MAP, code, message);
    }

    private String emptyObjectIfBlank(String value) {
        return value == null || value.isBlank() ? "{}" : value;
    }

    private String emptyArrayIfBlank(String value) {
        return value == null || value.isBlank() ? "[]" : value;
    }
}
