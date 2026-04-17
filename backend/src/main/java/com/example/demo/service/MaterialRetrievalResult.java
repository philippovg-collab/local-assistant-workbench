package com.example.demo.service;

import com.example.demo.model.ChatSource;
import java.util.List;

public record MaterialRetrievalResult(
    int materialCount,
    int activeMaterialCount,
    int readyMaterialCount,
    List<RetrievedMaterialChunk> matches
) {
    public List<ChatSource> sources() {
        return matches.stream().map(RetrievedMaterialChunk::source).toList();
    }
}
