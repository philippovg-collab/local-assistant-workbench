package com.example.demo.infrastructure.material;

import java.util.Arrays;
import java.util.Locale;

public enum LexicalProviderType {
    POSTGRES("postgres"),
    ELASTICSEARCH("elasticsearch");

    private final String propertyValue;

    LexicalProviderType(String propertyValue) {
        this.propertyValue = propertyValue;
    }

    public String propertyValue() {
        return propertyValue;
    }

    public static LexicalProviderType fromProperty(String rawValue) {
        String normalized = rawValue == null ? "" : rawValue.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalStateException(
                "Property 'app.rag.lexical-provider' must not be blank. Supported values in Phase 1: " + supportedValues() + "."
            );
        }

        return Arrays.stream(values())
            .filter(type -> type.propertyValue.equals(normalized))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "Unsupported app.rag.lexical-provider='" + rawValue + "'. Supported values in Phase 1: " + supportedValues() + "."
            ));
    }

    private static String supportedValues() {
        return Arrays.stream(values())
            .map(LexicalProviderType::propertyValue)
            .sorted()
            .reduce((left, right) -> left + ", " + right)
            .orElse("");
    }
}
