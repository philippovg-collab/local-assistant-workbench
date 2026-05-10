package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

public enum MemoryEntryStatus {
    PENDING_REVIEW("pending_review"),
    APPROVED("approved"),
    REJECTED("rejected"),
    DELETED("deleted");

    private final String value;

    MemoryEntryStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static MemoryEntryStatus fromValue(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        return Arrays.stream(values())
            .filter(status -> status.value.equalsIgnoreCase(rawValue.trim())
                || status.name().equalsIgnoreCase(rawValue.trim()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unsupported memory status: " + rawValue));
    }
}
