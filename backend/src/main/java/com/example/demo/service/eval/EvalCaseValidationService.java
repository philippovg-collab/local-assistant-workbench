package com.example.demo.service.eval;

import com.example.demo.error.ApplicationException;
import com.example.demo.error.ErrorType;
import com.example.demo.model.EvidenceLocator;
import com.example.demo.model.eval.EvalCase;
import com.example.demo.model.eval.EvalCaseType;
import com.example.demo.model.eval.EvalExpectedMode;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class EvalCaseValidationService {

    public void validateForReview(EvalCase evalCase) {
        if (evalCase.expectedMode() == null) {
            invalid("expectedMode is required before review");
        }
        switch (evalCase.caseType()) {
            case EXACT_FACT -> validateExactFact(evalCase);
            case MULTI_DOCUMENT_COMPARISON -> validateMultiDocument(evalCase);
            case DATE_VERSION_FILTER -> validateDateVersion(evalCase);
            case TABLE_QUESTION -> validateTableQuestion(evalCase);
            case AMBIGUOUS_QUERY -> requireMode(evalCase, EvalExpectedMode.CLARIFY);
            case NO_ANSWER -> requireMode(evalCase, EvalExpectedMode.ABSTAIN);
            case CONFLICTING_SOURCES -> validateConflictingSources(evalCase);
        }
    }

    private void validateExactFact(EvalCase evalCase) {
        requireMode(evalCase, EvalExpectedMode.ANSWER);
        if (evalCase.goldFacts().isEmpty() && evalCase.acceptedAnswers().isEmpty()) {
            invalid("EXACT_FACT cases require at least one gold fact or accepted answer");
        }
        if (evalCase.goldEvidenceLocators().isEmpty()) {
            invalid("EXACT_FACT cases require at least one gold evidence locator");
        }
    }

    private void validateMultiDocument(EvalCase evalCase) {
        requireMode(evalCase, EvalExpectedMode.ANSWER);
        requireTwoDocGroups(evalCase, "MULTI_DOCUMENT_COMPARISON cases require at least two required doc groups");
    }

    private void validateDateVersion(EvalCase evalCase) {
        requireMode(evalCase, EvalExpectedMode.ANSWER);
        Map<String, Object> filters = evalCase.retrievalFilters();
        boolean pinned = hasText(filters, "effectiveDate")
            || hasText(filters, "versionLabel")
            || hasText(filters, "versionSelectionMode")
            || hasText(filters, "versionState")
            || hasText(filters, "uploadedAfterInclusive")
            || hasText(filters, "uploadedBeforeExclusive");
        if (!pinned) {
            invalid("DATE_VERSION_FILTER cases require effective date or version retrieval filters");
        }
    }

    private void validateTableQuestion(EvalCase evalCase) {
        requireMode(evalCase, EvalExpectedMode.ANSWER);
        boolean hasTableLocator = evalCase.goldEvidenceLocators().stream().anyMatch(this::isTableLocator)
            || evalCase.requiredDocGroups().stream().flatMap(List::stream).anyMatch(this::isTableLocator);
        if (!hasTableLocator) {
            invalid("TABLE_QUESTION cases require a table, row, or column evidence locator");
        }
    }

    private void validateConflictingSources(EvalCase evalCase) {
        requireTwoDocGroups(evalCase, "CONFLICTING_SOURCES cases require at least two required doc groups");
        if (evalCase.goldFacts().isEmpty() && evalCase.acceptedAnswers().isEmpty()) {
            invalid("CONFLICTING_SOURCES cases require a gold fact or accepted answer describing the expected resolution");
        }
    }

    private void requireTwoDocGroups(EvalCase evalCase, String message) {
        long nonEmptyGroups = evalCase.requiredDocGroups().stream()
            .filter(group -> group != null && !group.isEmpty())
            .count();
        if (nonEmptyGroups < 2) {
            invalid(message);
        }
    }

    private void requireMode(EvalCase evalCase, EvalExpectedMode expectedMode) {
        if (evalCase.expectedMode() != expectedMode) {
            invalid(evalCase.caseType() + " cases require expectedMode=" + expectedMode);
        }
    }

    private boolean hasText(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value != null && StringUtils.hasText(String.valueOf(value));
    }

    private boolean isTableLocator(EvidenceLocator locator) {
        return locator != null
            && (StringUtils.hasText(locator.tableId())
                || StringUtils.hasText(locator.rowKey())
                || StringUtils.hasText(locator.columnKey()));
    }

    private void invalid(String message) {
        throw new ApplicationException(
            ErrorType.INVALID_REQUEST,
            "eval_case.validation_failed",
            message
        );
    }
}
