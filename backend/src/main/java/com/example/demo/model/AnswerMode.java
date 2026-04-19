package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

public enum AnswerMode {
    BRIEF("brief"),
    WITH_QUOTES("with_quotes"),
    DOCUMENTS_ONLY("documents_only"),
    BROADER_REASONING("broader_reasoning"),
    STRICT_SOURCES_ONLY("strict_sources_only");

    private final String value;

    AnswerMode(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static AnswerMode fromValue(String rawValue) {
        if (rawValue == null) {
            return null;
        }

        return Arrays.stream(values())
            .filter(mode -> mode.value.equalsIgnoreCase(rawValue.trim()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unsupported answer mode: " + rawValue));
    }
}
