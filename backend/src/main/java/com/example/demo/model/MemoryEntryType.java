package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

public enum MemoryEntryType {
    USER_PREFERENCE("user_preference"),
    USER_ALIAS("user_alias"),
    WORKSPACE_NOTE("workspace_note"),
    PROJECT_NOTE("project_note"),
    PINNED_USER_FACT("pinned_user_fact");

    private final String value;

    MemoryEntryType(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static MemoryEntryType fromValue(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        return Arrays.stream(values())
            .filter(type -> type.value.equalsIgnoreCase(rawValue.trim())
                || type.name().equalsIgnoreCase(rawValue.trim()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unsupported memory entry type: " + rawValue));
    }
}
