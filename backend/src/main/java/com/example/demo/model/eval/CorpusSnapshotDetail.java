package com.example.demo.model.eval;

import java.util.Map;

public record CorpusSnapshotDetail(
    CorpusSnapshot snapshot,
    Map<String, Object> manifest,
    Map<String, Object> summary
) {
    public CorpusSnapshotDetail {
        manifest = manifest == null ? Map.of() : manifest;
        summary = summary == null ? Map.of() : summary;
    }
}
