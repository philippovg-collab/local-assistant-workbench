package com.example.demo.model.eval;

import com.example.demo.model.ChunkScoreBreakdown;
import com.example.demo.model.DocumentBlockType;
import com.example.demo.model.EvidenceLocator;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RetrievalEvalCandidate(
    String stage,
    int rank,
    String materialId,
    Integer chunkIndex,
    String title,
    String excerpt,
    Integer page,
    DocumentBlockType chunkType,
    Double semanticDistance,
    Double lexicalScore,
    Integer fusedScore,
    Integer finalScore,
    ChunkScoreBreakdown scoreBreakdown,
    EvidenceLocator evidenceLocator
) {
}
