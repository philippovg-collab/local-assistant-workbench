package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

public enum InstructionScopeLevel {
    ASSISTANT_SYSTEM("assistant_system"),
    WORKSPACE_PROJECT("workspace_project"),
    CHAT_SCENARIO("chat_scenario"),
    REQUEST_TEMPORARY("request_temporary");

    private final String value;

    InstructionScopeLevel(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static InstructionScopeLevel fromValue(String rawValue) {
        if (rawValue == null) {
            return null;
        }

        return Arrays.stream(values())
            .filter(scopeLevel -> scopeLevel.value.equalsIgnoreCase(rawValue.trim()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unsupported instruction scope: " + rawValue));
    }
}
