package com.example.demo.model;

import java.util.Map;

public record RetrievalWindowHealth(
    int sampleSize,
    double noContextRate,
    Map<String, Integer> hitDistributionByChunkType,
    RerankerDelta rerankerDelta
) {
    public RetrievalWindowHealth {
        hitDistributionByChunkType = hitDistributionByChunkType == null ? Map.of() : Map.copyOf(hitDistributionByChunkType);
        rerankerDelta = rerankerDelta == null ? RerankerDelta.empty() : rerankerDelta;
    }

    public static RetrievalWindowHealth empty() {
        return new RetrievalWindowHealth(0, 0.0d, Map.of(), RerankerDelta.empty());
    }
}
