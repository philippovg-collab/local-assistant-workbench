package com.example.demo.model;

public record RetrievalTrace(
    int totalMaterials,
    int totalActiveMaterials,
    int totalReadyMaterials,
    int scopedMaterials,
    int scopedActiveMaterials,
    int scopedReadyMaterials,
    int semanticCandidates,
    int lexicalCandidates,
    int finalChunks,
    String supportVerdict
) {
    public RetrievalTrace {
        supportVerdict = supportVerdict == null ? (finalChunks <= 0 ? "none" : "weak") : supportVerdict;
    }

    public RetrievalTrace(
        int totalMaterials,
        int totalActiveMaterials,
        int totalReadyMaterials,
        int scopedMaterials,
        int scopedActiveMaterials,
        int scopedReadyMaterials,
        int semanticCandidates,
        int lexicalCandidates,
        int finalChunks
    ) {
        this(
            totalMaterials,
            totalActiveMaterials,
            totalReadyMaterials,
            scopedMaterials,
            scopedActiveMaterials,
            scopedReadyMaterials,
            semanticCandidates,
            lexicalCandidates,
            finalChunks,
            finalChunks <= 0 ? "none" : "weak"
        );
    }
}
