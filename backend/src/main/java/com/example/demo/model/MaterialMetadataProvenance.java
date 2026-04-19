package com.example.demo.model;

import java.util.LinkedHashMap;
import java.util.Map;

public record MaterialMetadataProvenance(
    Map<String, MetadataValueOrigin> fieldOrigins,
    Map<String, Double> fieldConfidence
) {

    public MaterialMetadataProvenance {
        fieldOrigins = fieldOrigins == null
            ? Map.of()
            : Map.copyOf(new LinkedHashMap<>(fieldOrigins));
        fieldConfidence = normalizeConfidence(fieldConfidence);
    }

    public static MaterialMetadataProvenance empty() {
        return new MaterialMetadataProvenance(Map.of(), Map.of());
    }

    private static Map<String, Double> normalizeConfidence(Map<String, Double> rawConfidence) {
        if (rawConfidence == null || rawConfidence.isEmpty()) {
            return Map.of();
        }

        Map<String, Double> normalized = new LinkedHashMap<>();
        rawConfidence.forEach((field, confidence) -> {
            if (field == null || field.isBlank() || confidence == null) {
                return;
            }
            double bounded = Math.max(0.0d, Math.min(1.0d, confidence));
            normalized.put(field, bounded);
        });
        return Map.copyOf(normalized);
    }
}
