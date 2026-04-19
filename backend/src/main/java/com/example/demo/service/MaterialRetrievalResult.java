package com.example.demo.service;

import com.example.demo.model.ChatSource;
import com.example.demo.model.RetrievalDebug;
import com.example.demo.model.RetrievalTrace;
import java.util.List;

public record MaterialRetrievalResult(
    int materialCount,
    int activeMaterialCount,
    int readyMaterialCount,
    int scopedMaterialCount,
    int scopedActiveMaterialCount,
    int scopedReadyMaterialCount,
    List<RetrievedMaterialChunk> matches,
    RetrievalTrace retrievalTrace,
    RetrievalDebug retrievalDebug
) {
    public MaterialRetrievalResult(
        int materialCount,
        int activeMaterialCount,
        int readyMaterialCount,
        int scopedMaterialCount,
        int scopedActiveMaterialCount,
        int scopedReadyMaterialCount,
        List<RetrievedMaterialChunk> matches,
        RetrievalTrace retrievalTrace
    ) {
        this(
            materialCount,
            activeMaterialCount,
            readyMaterialCount,
            scopedMaterialCount,
            scopedActiveMaterialCount,
            scopedReadyMaterialCount,
            matches,
            retrievalTrace,
            null
        );
    }

    public MaterialRetrievalResult(
        int materialCount,
        int activeMaterialCount,
        int readyMaterialCount,
        List<RetrievedMaterialChunk> matches
    ) {
        this(
            materialCount,
            activeMaterialCount,
            readyMaterialCount,
            materialCount,
            activeMaterialCount,
            readyMaterialCount,
            matches == null ? List.of() : matches,
            new RetrievalTrace(
                materialCount,
                activeMaterialCount,
                readyMaterialCount,
                materialCount,
                activeMaterialCount,
                readyMaterialCount,
                0,
                0,
                matches == null ? 0 : matches.size()
            ),
            null
        );
    }

    public List<ChatSource> sources() {
        return matches.stream().map(RetrievedMaterialChunk::source).toList();
    }
}
