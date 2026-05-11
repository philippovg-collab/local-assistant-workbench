package com.example.demo.model.eval;

public record RetrievalEvalCaseRef(
    String datasetId,
    String caseId,
    String caseKey,
    Integer caseRevision
) {
}
