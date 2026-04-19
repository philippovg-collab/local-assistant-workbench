package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

public enum KnowledgeDocumentClass {
    CONTRACTS("contracts"),
    REGULATIONS("regulations"),
    CORRESPONDENCE("correspondence"),
    TECHDOCS("techdocs"),
    OTHER("other");

    private final String value;

    KnowledgeDocumentClass(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static KnowledgeDocumentClass fromValue(String rawValue) {
        if (rawValue == null) {
            return null;
        }

        return Arrays.stream(values())
            .filter(documentClass -> documentClass.value.equalsIgnoreCase(rawValue.trim()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unsupported knowledge document class: " + rawValue));
    }
}
