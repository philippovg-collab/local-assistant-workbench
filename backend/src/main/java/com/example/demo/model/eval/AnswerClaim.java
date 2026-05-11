package com.example.demo.model.eval;

import java.util.List;

public record AnswerClaim(
    String claimId,
    String text,
    List<AnswerCitation> citations
) {
    public AnswerClaim {
        citations = citations == null ? List.of() : List.copyOf(citations);
    }
}
