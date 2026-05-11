package com.example.demo.model.eval;

import java.util.List;

public record EvalDatasetDetail(
    EvalDataset dataset,
    List<EvalCase> cases,
    List<EvalCaseReview> reviews
) {
    public EvalDatasetDetail {
        cases = cases == null ? List.of() : List.copyOf(cases);
        reviews = reviews == null ? List.of() : List.copyOf(reviews);
    }
}
