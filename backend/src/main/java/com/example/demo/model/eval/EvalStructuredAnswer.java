package com.example.demo.model.eval;

import java.util.List;

public record EvalStructuredAnswer(
    String answer,
    String finalMode,
    List<AnswerClaim> claims
) {
    public EvalStructuredAnswer {
        claims = claims == null ? List.of() : List.copyOf(claims);
    }
}
