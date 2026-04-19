package com.example.demo.model;

import java.util.LinkedHashSet;
import java.util.List;

public record KnowledgeScope(
    List<String> presetIds,
    List<KnowledgeDocumentClass> documentClasses,
    List<String> tags,
    String workspaceKey,
    boolean uploadedTodayOnly
) {

    public KnowledgeScope {
        presetIds = normalizeStringList(presetIds);
        documentClasses = documentClasses == null ? List.of() : List.copyOf(new LinkedHashSet<>(documentClasses));
        tags = normalizeStringList(tags);
        workspaceKey = normalizeText(workspaceKey);
    }

    public static KnowledgeScope empty() {
        return new KnowledgeScope(List.of(), List.of(), List.of(), null, false);
    }

    private static List<String> normalizeStringList(List<String> rawValues) {
        if (rawValues == null || rawValues.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String rawValue : rawValues) {
            String candidate = normalizeText(rawValue);
            if (candidate != null) {
                normalized.add(candidate);
            }
        }
        return List.copyOf(normalized);
    }

    private static String normalizeText(String rawValue) {
        if (rawValue == null) {
            return null;
        }

        String normalized = rawValue.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
