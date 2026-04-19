package com.example.demo.model;

import java.util.List;

public record KnowledgeScopeResolved(
    List<KnowledgePresetReference> presets,
    List<KnowledgeDocumentClass> documentClasses,
    List<String> tags,
    String workspaceKey,
    boolean uploadedTodayOnly
) {
    public static KnowledgeScopeResolved empty() {
        return new KnowledgeScopeResolved(List.of(), List.of(), List.of(), null, false);
    }
}
