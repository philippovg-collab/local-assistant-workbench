package com.example.demo.infrastructure.audit;

import com.example.demo.model.AnswerMode;
import com.example.demo.model.ChatRunMessage;
import com.example.demo.model.ChatSource;
import com.example.demo.model.InstructionTraceEntry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

final class PostgresChatRunTraceValueMapper {

    private static final ChatTraceJsonCodec JSON_CODEC = new ChatTraceJsonCodec();
    private static final TypeReference<List<ChatRunMessage>> MESSAGES_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<InstructionTraceEntry>> INSTRUCTION_TRACE_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<ChatSource>> SOURCES_TYPE = new TypeReference<>() {
    };

    private PostgresChatRunTraceValueMapper() {
    }

    static String writeJson(Object value) {
        return JSON_CODEC.write(value);
    }

    static JsonNode readTree(String rawJson, String context) {
        return JSON_CODEC.readTree(rawJson, context);
    }

    static <T> T readJson(String rawJson, Class<T> type, String context) {
        return JSON_CODEC.read(rawJson, type, context);
    }

    static List<ChatRunMessage> readMessages(String rawJson, String context) {
        return JSON_CODEC.read(rawJson, MESSAGES_TYPE, context);
    }

    static List<InstructionTraceEntry> readInstructionTrace(String rawJson, String context) {
        return JSON_CODEC.read(rawJson, INSTRUCTION_TRACE_TYPE, context);
    }

    static List<ChatSource> readSources(String rawJson, String context) {
        return JSON_CODEC.read(rawJson, SOURCES_TYPE, context);
    }

    static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? Instant.now() : timestamp.toInstant();
    }

    static Instant toInstantOrNull(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    static AnswerMode answerModeOf(String rawValue) {
        return rawValue == null ? null : AnswerMode.fromValue(rawValue);
    }

    static String clip(String value, int limit) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= limit) {
            return normalized;
        }
        return normalized.substring(0, limit) + "...";
    }

    static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    static String normalizeOptionalWorkspaceKey(String workspaceKey) {
        if (workspaceKey == null || workspaceKey.isBlank()) {
            return null;
        }
        return workspaceKey.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
