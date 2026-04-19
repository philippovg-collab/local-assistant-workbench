package com.example.demo.model;

import java.util.List;

public record KnowledgePresetRevisionDiff(
    String presetId,
    int fromRevision,
    int toRevision,
    List<RevisionDiffEntry> changes
) {
}
