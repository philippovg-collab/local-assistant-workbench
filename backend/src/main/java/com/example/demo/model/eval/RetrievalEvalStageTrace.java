package com.example.demo.model.eval;

import java.util.List;

public record RetrievalEvalStageTrace(
    List<RetrievalEvalCandidate> semanticCandidates,
    List<RetrievalEvalCandidate> lexicalCandidates,
    List<RetrievalEvalCandidate> fusedCandidates,
    List<RetrievalEvalCandidate> preRerankCandidates,
    List<RetrievalEvalCandidate> finalChunks
) {
    public RetrievalEvalStageTrace {
        semanticCandidates = semanticCandidates == null ? List.of() : List.copyOf(semanticCandidates);
        lexicalCandidates = lexicalCandidates == null ? List.of() : List.copyOf(lexicalCandidates);
        fusedCandidates = fusedCandidates == null ? List.of() : List.copyOf(fusedCandidates);
        preRerankCandidates = preRerankCandidates == null ? List.of() : List.copyOf(preRerankCandidates);
        finalChunks = finalChunks == null ? List.of() : List.copyOf(finalChunks);
    }
}
