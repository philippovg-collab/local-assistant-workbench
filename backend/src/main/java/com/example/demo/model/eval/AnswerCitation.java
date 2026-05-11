package com.example.demo.model.eval;

import com.example.demo.model.EvidenceLocator;

public record AnswerCitation(
    Integer sourceId,
    EvidenceLocator evidenceLocator
) {
}
