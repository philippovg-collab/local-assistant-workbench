package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum ChatMode {
    DIRECT,
    RAG;

    @JsonCreator
    public static ChatMode fromValue(String value) {
        if (value == null) {
            return DIRECT;
        }

        return ChatMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

    @JsonValue
    public String toValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
