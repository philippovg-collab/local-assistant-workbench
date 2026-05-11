package com.example.demo.service.eval;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.demo.error.ApplicationException;
import com.example.demo.model.EvidenceLocator;
import com.example.demo.model.eval.EvalCase;
import com.example.demo.model.eval.EvalCaseOrigin;
import com.example.demo.model.eval.EvalCaseSeverity;
import com.example.demo.model.eval.EvalCaseType;
import com.example.demo.model.eval.EvalExpectedMode;
import com.example.demo.model.eval.EvalReviewStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EvalCaseValidationServiceTest {

    private final EvalCaseValidationService service = new EvalCaseValidationService();

    @Test
    void validatesExactFactAndNormalizesAcceptedAnswers() {
        EvalCase evalCase = evalCase(
            EvalCaseType.EXACT_FACT,
            EvalExpectedMode.ANSWER,
            List.of(" limit is 10 "),
            List.of("  Ten   Units "),
            List.of(locator("DOC-1", null, null, null)),
            List.of(),
            Map.of("versionLabel", "v1")
        );

        assertEquals(List.of("ten units"), evalCase.acceptedAnswers());
        assertDoesNotThrow(() -> service.validateForReview(evalCase));
    }

    @Test
    void rejectsDirectReviewWhenGoldEvidenceIsMissing() {
        ApplicationException exception = assertThrows(ApplicationException.class, () ->
            service.validateForReview(evalCase(
                EvalCaseType.EXACT_FACT,
                EvalExpectedMode.ANSWER,
                List.of("limit is 10"),
                List.of(),
                List.of(),
                List.of(),
                Map.of()
            ))
        );

        assertEquals("eval_case.validation_failed", exception.getCode());
    }

    @Test
    void enforcesMultiDocumentAndDateVersionRules() {
        assertDoesNotThrow(() -> service.validateForReview(evalCase(
            EvalCaseType.MULTI_DOCUMENT_COMPARISON,
            EvalExpectedMode.ANSWER,
            List.of(),
            List.of(),
            List.of(),
            List.of(
                List.of(locator("DOC-1", null, null, null)),
                List.of(locator("DOC-2", null, null, null))
            ),
            Map.of()
        )));

        assertDoesNotThrow(() -> service.validateForReview(evalCase(
            EvalCaseType.DATE_VERSION_FILTER,
            EvalExpectedMode.ANSWER,
            List.of(),
            List.of("10"),
            List.of(locator("DOC-1", null, null, null)),
            List.of(),
            Map.of("effectiveDate", "2026-05-10")
        )));
    }

    @Test
    void enforcesModeSpecificCases() {
        assertDoesNotThrow(() -> service.validateForReview(evalCase(
            EvalCaseType.AMBIGUOUS_QUERY,
            EvalExpectedMode.CLARIFY,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            Map.of()
        )));
        assertDoesNotThrow(() -> service.validateForReview(evalCase(
            EvalCaseType.NO_ANSWER,
            EvalExpectedMode.ABSTAIN,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            Map.of()
        )));
        assertDoesNotThrow(() -> service.validateForReview(evalCase(
            EvalCaseType.TABLE_QUESTION,
            EvalExpectedMode.ANSWER,
            List.of(),
            List.of("10"),
            List.of(locator("DOC-1", "T-1", "R-1", "C-1")),
            List.of(),
            Map.of()
        )));
    }

    private EvalCase evalCase(
        EvalCaseType caseType,
        EvalExpectedMode expectedMode,
        List<String> facts,
        List<String> acceptedAnswers,
        List<EvidenceLocator> locators,
        List<List<EvidenceLocator>> groups,
        Map<String, Object> filters
    ) {
        return new EvalCase(
            "case-id",
            "dataset-id",
            "case-1",
            1,
            caseType,
            expectedMode,
            EvalCaseSeverity.BLOCKER,
            "What is the limit?",
            Map.of(),
            filters,
            facts,
            acceptedAnswers,
            locators,
            groups,
            List.of(),
            List.of("core"),
            new EvalCaseOrigin("manual", "seed", null, Map.of()),
            EvalReviewStatus.DRAFT,
            true,
            Instant.parse("2026-05-10T00:00:00Z"),
            Instant.parse("2026-05-10T00:00:00Z")
        );
    }

    private EvidenceLocator locator(String documentNumber, String tableId, String rowKey, String columnKey) {
        return new EvidenceLocator(
            "source-" + documentNumber,
            "material-" + documentNumber,
            documentNumber,
            "v1",
            null,
            1,
            0,
            1,
            List.of(),
            List.of(),
            tableId,
            null,
            rowKey,
            columnKey,
            null,
            null
        );
    }
}
