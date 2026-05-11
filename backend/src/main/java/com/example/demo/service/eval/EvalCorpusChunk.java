package com.example.demo.service.eval;

import java.util.List;

public record EvalCorpusChunk(
    int chunkIndex,
    String chunkText,
    Integer page,
    String chunkType,
    List<String> sectionPath,
    List<String> headingTrail,
    String tableId,
    String slideId,
    String parserConfidence
) {
    public EvalCorpusChunk {
        sectionPath = sectionPath == null ? List.of() : List.copyOf(sectionPath);
        headingTrail = headingTrail == null ? List.of() : List.copyOf(headingTrail);
    }
}
