package com.example.demo.infrastructure.material;

import java.util.Arrays;

public enum ChunkProfile {
    FIXED_V1("fixed-v1"),
    SENTENCE_V1("sentence-v1"),
    STRUCTURED_V1("structured-v1");

    private final String propertyValue;

    ChunkProfile(String propertyValue) {
        this.propertyValue = propertyValue;
    }

    public String propertyValue() {
        return propertyValue;
    }

    public static ChunkProfile fromProperty(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException(
                "Property 'app.materials.chunk-profile' must not be blank. Supported values: " + supportedValues() + "."
            );
        }

        return Arrays.stream(values())
            .filter(value -> value.propertyValue.equalsIgnoreCase(rawValue.trim()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "Unsupported app.materials.chunk-profile='" + rawValue + "'. Supported values: " + supportedValues() + "."
            ));
    }

    private static String supportedValues() {
        return Arrays.stream(values())
            .map(ChunkProfile::propertyValue)
            .sorted()
            .reduce((left, right) -> left + ", " + right)
            .orElse("");
    }
}
