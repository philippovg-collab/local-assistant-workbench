package com.example.demo.service.material;

import java.util.Arrays;
import java.util.Locale;

public enum LexicalProviderMode {
    POSTGRES("postgres"),
    ELASTICSEARCH("elasticsearch"),
    AUTO("auto");

    private final String propertyValue;

    LexicalProviderMode(String propertyValue) {
        this.propertyValue = propertyValue;
    }

    public String propertyValue() {
        return propertyValue;
    }

    public static LexicalProviderMode fromProperty(String rawValue) {
        String normalized = rawValue == null ? "" : rawValue.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalStateException(
                "Property 'app.rag.lexical-provider' must not be blank. Supported values: " + supportedValues() + "."
            );
        }

        return Arrays.stream(values())
            .filter(mode -> mode.propertyValue.equals(normalized))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "Unsupported app.rag.lexical-provider='" + rawValue + "'. Supported values: " + supportedValues() + "."
            ));
    }

    private static String supportedValues() {
        return Arrays.stream(values())
            .map(LexicalProviderMode::propertyValue)
            .sorted()
            .reduce((left, right) -> left + ", " + right)
            .orElse("");
    }
}
