package com.example.demo.service.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.model.EvidenceLocator;
import com.example.demo.model.eval.AnswerCitation;
import com.example.demo.model.eval.AnswerClaim;
import com.example.demo.model.eval.EvalCase;
import com.example.demo.model.eval.EvalCaseOrigin;
import com.example.demo.model.eval.EvalCaseType;
import com.example.demo.model.eval.EvalE2EScoreSummary;
import com.example.demo.model.eval.EvalExpectedMode;
import com.example.demo.model.eval.EvalSeverity;
import com.example.demo.model.eval.EvalStructuredAnswer;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EvalE2EScoringServiceTest {

    private final EvalE2EScoringService scoringService = new EvalE2EScoringService();

    @Test
    void scoresAcceptedAnswersFactsAndCitations() {
        EvalE2EScoreSummary summary = scoringService.score(evalCase(), new EvalStructuredAnswer(
            "The approved limit is 10.",
            "answered",
            List.of(new AnswerClaim(
                "c1",
                "limit is 10",
                List.of(new AnswerCitation(1, new EvidenceLocator(
                    "source-1",
                    "mat-1",
                    "DOC-1",
                    "v1",
                    null,
                    null,
                    0,
                    1,
                    List.of(),
                    List.of(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
                )))
            ))
        ));

        assertTrue(summary.passed());
        assertEquals(1.0d, summary.metrics().get("accepted_answer_match"));
        assertEquals(1.0d, summary.metrics().get("required_gold_fact_coverage"));
        assertEquals(1.0d, summary.metrics().get("citation_locator_match"));
    }

    @Test
    void flagsForbiddenCitationAsFailure() {
        EvalE2EScoreSummary summary = scoringService.score(evalCase(), new EvalStructuredAnswer(
            "The approved limit is 10.",
            "answered",
            List.of(new AnswerClaim(
                "c1",
                "limit is 10",
                List.of(new AnswerCitation(1, new EvidenceLocator(
                    null,
                    "forbidden-mat",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    List.of(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
                )))
            ))
        ));

        assertFalse(summary.passed());
        assertEquals(1.0d, summary.metrics().get("forbidden_doc_violation"));
    }

    private EvalCase evalCase() {
        return new EvalCase(
            "case",
            "dataset",
            "case-1",
            1,
            EvalCaseType.EXACT_FACT,
            EvalExpectedMode.ANSWER,
            EvalSeverity.BLOCKER,
            "What is the limit?",
            Map.of(),
            Map.of(),
            Map.of("facts", List.of("limit is 10")),
            Map.of("accepted", List.of("10")),
            Map.of(
                "locators",
                List.of(Map.of("materialId", "mat-1", "documentNumber", "DOC-1", "versionLabel", "v1", "page", 1)),
                "forbiddenDocuments",
                List.of("forbidden-mat")
            ),
            new EvalCaseOrigin("manual", "seed", null, Map.of()),
            Instant.parse("2026-05-10T00:00:00Z"),
            Instant.parse("2026-05-10T00:00:00Z")
        );
    }
}
