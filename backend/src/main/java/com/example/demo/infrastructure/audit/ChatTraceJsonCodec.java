package com.example.demo.infrastructure.audit;

import com.example.demo.api.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import org.springframework.http.HttpStatus;

final class ChatTraceJsonCodec {

    private final ObjectMapper objectMapper = JsonMapper.builder()
        .findAndAddModules()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
        .addHandler(new DeserializationProblemHandler() {
            @Override
            public Object handleWeirdStringValue(
                DeserializationContext context,
                Class<?> targetType,
                String valueToConvert,
                String failureMessage
            ) throws IOException {
                if (targetType != null && targetType.isEnum()) {
                    return null;
                }
                return NOT_HANDLED;
            }

            @Override
            public Object handleInstantiationProblem(
                DeserializationContext context,
                Class<?> instantiatedClass,
                Object argument,
                Throwable throwable
            ) throws IOException {
                if (instantiatedClass != null && instantiatedClass.isEnum()) {
                    return null;
                }
                return NOT_HANDLED;
            }
        })
        .build();

    String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "chat_trace.storage_encode_failed",
                "Unable to encode chat trace JSON",
                exception
            );
        }
    }

    JsonNode readTree(String rawJson, String context) {
        if (rawJson == null) {
            return null;
        }
        try {
            return objectMapper.readTree(rawJson);
        } catch (JsonProcessingException exception) {
            throw decodeException(context, exception);
        }
    }

    <T> T read(String rawJson, Class<T> type, String context) {
        if (rawJson == null) {
            return null;
        }
        try {
            return objectMapper.readValue(rawJson, type);
        } catch (JsonProcessingException exception) {
            throw decodeException(context, exception);
        }
    }

    <T> T read(String rawJson, TypeReference<T> type, String context) {
        if (rawJson == null) {
            return null;
        }
        try {
            return objectMapper.readValue(rawJson, type);
        } catch (JsonProcessingException exception) {
            throw decodeException(context, exception);
        }
    }

    private ApiException decodeException(String context, JsonProcessingException exception) {
        String suffix = context == null || context.isBlank() ? "" : " at " + context;
        return new ApiException(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "chat_trace.storage_decode_failed",
            "Unable to decode chat trace JSON" + suffix,
            exception
        );
    }
}
