package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum MaterialLanguageCode {
    RU,
    KK,
    EN;

    @JsonCreator
    public static MaterialLanguageCode fromValue(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String normalized = rawValue.trim().toUpperCase(java.util.Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }
        return MaterialLanguageCode.valueOf(normalized);
    }
}
