package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

public enum InstructionCategory {
    SYSTEM("system"),
    USER("user"),
    CONTEXT("context"),
    SAFETY("safety");

    private final String value;

    InstructionCategory(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static InstructionCategory fromValue(String rawValue) {
        if (rawValue == null) {
            return null;
        }

        return Arrays.stream(values())
            .filter(category -> category.value.equalsIgnoreCase(rawValue.trim()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unsupported instruction category: " + rawValue));
    }

    public static InstructionCategory fromValueOrDefault(String rawValue, InstructionCategory fallback) {
        if (rawValue == null || rawValue.isBlank()) {
            return fallback;
        }

        try {
            return fromValue(rawValue);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
