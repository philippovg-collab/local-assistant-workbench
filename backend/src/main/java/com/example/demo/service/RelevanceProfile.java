package com.example.demo.service;

import java.util.Arrays;

public enum RelevanceProfile {
    LEGACY("legacy"),
    HYBRID_V1("hybrid-v1"),
    HYBRID_RERANK_V1("hybrid-rerank-v1");

    private final String propertyValue;

    RelevanceProfile(String propertyValue) {
        this.propertyValue = propertyValue;
    }

    public String propertyValue() {
        return propertyValue;
    }

    public static RelevanceProfile fromProperty(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException(
                "Property 'app.rag.relevance-profile' must not be blank. Supported values: " + supportedValues() + "."
            );
        }

        return Arrays.stream(values())
            .filter(value -> value.propertyValue.equalsIgnoreCase(rawValue.trim()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "Unsupported app.rag.relevance-profile='" + rawValue + "'. Supported values: " + supportedValues() + "."
            ));
    }

    private static String supportedValues() {
        return Arrays.stream(values())
            .map(RelevanceProfile::propertyValue)
            .sorted()
            .reduce((left, right) -> left + ", " + right)
            .orElse("");
    }
}
