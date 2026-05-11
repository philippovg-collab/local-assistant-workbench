package com.example.demo.service.eval;

import com.example.demo.model.EvidenceLocator;
import com.example.demo.model.eval.AnswerCitation;
import com.example.demo.model.eval.AnswerClaim;
import com.example.demo.model.eval.EvalCase;
import com.example.demo.model.eval.EvalE2EScoreSummary;
import com.example.demo.model.eval.EvalExpectedMode;
import com.example.demo.model.eval.EvalStructuredAnswer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class EvalE2EScoringService {

    public EvalE2EScoreSummary score(EvalCase evalCase, EvalStructuredAnswer answer) {
        ScoreBuilder scores = new ScoreBuilder();
        scores.scored("output_format_validity", 1.0d);
        scoreAcceptedAnswers(scores, evalCase, answer);
        scoreGoldFacts(scores, evalCase, answer);
        scoreCitationLocators(scores, evalCase, answer);
        scoreForbiddenDocs(scores, evalCase, answer);
        scoreExpectedModes(scores, evalCase, answer);
        return scores.build();
    }

    private void scoreAcceptedAnswers(ScoreBuilder scores, EvalCase evalCase, EvalStructuredAnswer answer) {
        Set<String> acceptedAnswers = normalizedSet(evalCase.acceptedAnswers());
        if (acceptedAnswers.isEmpty()) {
            scores.notScorable("accepted_answer_match", "No accepted answers are defined");
            return;
        }
        String normalizedAnswer = normalize(answer.answer());
        boolean matched = acceptedAnswers.stream().anyMatch(accepted ->
            normalizedAnswer != null && normalizedAnswer.contains(accepted)
        );
        scores.scored("accepted_answer_match", matched ? 1.0d : 0.0d);
    }

    private void scoreGoldFacts(ScoreBuilder scores, EvalCase evalCase, EvalStructuredAnswer answer) {
        Set<String> requiredFacts = normalizedSet(evalCase.goldFacts());
        if (requiredFacts.isEmpty()) {
            scores.notScorable("required_gold_fact_coverage", "No required gold facts are defined");
            return;
        }
        String searchableAnswer = normalize(answer.answer() + " " + claimText(answer));
        long covered = requiredFacts.stream().filter(fact -> searchableAnswer != null && searchableAnswer.contains(fact)).count();
        scores.scored("required_gold_fact_coverage", covered / (double) requiredFacts.size());
    }

    private void scoreCitationLocators(ScoreBuilder scores, EvalCase evalCase, EvalStructuredAnswer answer) {
        List<GoldLocator> goldLocators = GoldLocator.fromEvidenceLocators(evalCase.goldEvidenceLocators());
        if (goldLocators.isEmpty()) {
            scores.notScorable("citation_locator_match", "No gold evidence locators are defined");
            return;
        }
        List<EvidenceLocator> citedLocators = citedLocators(answer);
        long matched = goldLocators.stream()
            .filter(gold -> citedLocators.stream().anyMatch(gold::matches))
            .count();
        scores.scored("citation_locator_match", matched / (double) goldLocators.size());
    }

    private void scoreForbiddenDocs(ScoreBuilder scores, EvalCase evalCase, EvalStructuredAnswer answer) {
        Set<String> forbiddenDocuments = normalizedSet(evalCase.forbiddenDocumentRefs());
        if (forbiddenDocuments.isEmpty()) {
            scores.notScorable("forbidden_doc_violation", "No forbidden documents are defined");
            return;
        }
        boolean violation = citedLocators(answer).stream().anyMatch(locator -> matchesAnyDocument(locator, forbiddenDocuments));
        scores.scored("forbidden_doc_violation", violation ? 1.0d : 0.0d);
    }

    private void scoreExpectedModes(ScoreBuilder scores, EvalCase evalCase, EvalStructuredAnswer answer) {
        boolean abstained = "abstained".equals(answer.finalMode());
        boolean clarified = "clarification_requested".equals(answer.finalMode());
        if (evalCase.expectedMode() == EvalExpectedMode.ABSTAIN) {
            scores.scored("abstain_recall", abstained ? 1.0d : 0.0d);
        } else {
            scores.notScorable("abstain_recall", "Case does not expect abstention");
        }
        if (abstained) {
            scores.scored("abstain_precision", evalCase.expectedMode() == EvalExpectedMode.ABSTAIN ? 1.0d : 0.0d);
        } else {
            scores.notScorable("abstain_precision", "Answer did not abstain");
        }
        if (evalCase.expectedMode() == EvalExpectedMode.CLARIFY) {
            scores.scored("clarification_recall", clarified ? 1.0d : 0.0d);
        } else {
            scores.notScorable("clarification_recall", "Case does not expect clarification");
        }
        if (clarified) {
            scores.scored("clarification_precision", evalCase.expectedMode() == EvalExpectedMode.CLARIFY ? 1.0d : 0.0d);
        } else {
            scores.notScorable("clarification_precision", "Answer did not request clarification");
        }
    }

    private String claimText(EvalStructuredAnswer answer) {
        return answer.claims().stream()
            .map(AnswerClaim::text)
            .reduce("", (left, right) -> left + " " + right);
    }

    private List<EvidenceLocator> citedLocators(EvalStructuredAnswer answer) {
        return answer.claims().stream()
            .flatMap(claim -> claim.citations().stream())
            .map(AnswerCitation::evidenceLocator)
            .filter(locator -> locator != null)
            .toList();
    }

    private boolean matchesAnyDocument(EvidenceLocator locator, Set<String> documents) {
        return documents.stream().anyMatch(document -> documentMatches(locator, document));
    }

    private boolean documentMatches(EvidenceLocator locator, String expectedDocument) {
        String expected = normalize(expectedDocument);
        return expected != null
            && locator != null
            && (expected.equals(normalize(locator.materialId()))
                || expected.equals(normalize(locator.sourceKey()))
                || expected.equals(normalize(locator.documentNumber())));
    }

    private Set<String> normalizedSet(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        values.stream().map(this::normalize).filter(item -> item != null).forEach(normalized::add);
        return Set.copyOf(normalized);
    }

    private String normalize(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim().replaceAll("\\s+", " ");
        return text.isEmpty() ? null : text.toLowerCase(Locale.ROOT);
    }

    private static final class ScoreBuilder {
        private final Map<String, Double> metrics = new LinkedHashMap<>();
        private final List<Map<String, Object>> notScorable = new ArrayList<>();

        void scored(String name, double value) {
            metrics.put(name, value);
        }

        void notScorable(String name, String reason) {
            notScorable.add(Map.of("name", name, "reason", reason));
        }

        EvalE2EScoreSummary build() {
            boolean passed = metrics.entrySet().stream().allMatch(entry -> {
                if ("forbidden_doc_violation".equals(entry.getKey())) {
                    return entry.getValue() == 0.0d;
                }
                return entry.getValue() >= 1.0d;
            });
            return new EvalE2EScoreSummary(
                passed,
                metrics.size(),
                notScorable.size(),
                metrics,
                Map.of("notScorable", notScorable)
            );
        }
    }

    private record GoldLocator(
        String sourceKey,
        String materialId,
        String documentNumber,
        String versionLabel,
        Integer chunkIndex,
        Integer page,
        String tableId,
        String rowKey,
        String columnKey
    ) {
        private static List<GoldLocator> fromEvidenceLocators(List<EvidenceLocator> rawLocators) {
            if (rawLocators == null || rawLocators.isEmpty()) {
                return List.of();
            }
            List<GoldLocator> locators = new ArrayList<>();
            for (EvidenceLocator locator : rawLocators) {
                locators.add(new GoldLocator(
                    normalizeStatic(locator.sourceKey()),
                    normalizeStatic(locator.materialId()),
                    normalizeStatic(locator.documentNumber()),
                    normalizeStatic(locator.versionLabel()),
                    locator.chunkIndex(),
                    locator.page(),
                    normalizeStatic(locator.tableId()),
                    normalizeStatic(locator.rowKey()),
                    normalizeStatic(locator.columnKey())
                ));
            }
            return List.copyOf(locators);
        }

        private boolean matches(EvidenceLocator locator) {
            if (locator == null) {
                return false;
            }
            return matchesText(sourceKey, locator.sourceKey())
                && matchesText(materialId, locator.materialId())
                && matchesText(documentNumber, locator.documentNumber())
                && matchesText(versionLabel, locator.versionLabel())
                && matchesNumber(chunkIndex, locator.chunkIndex())
                && matchesNumber(page, locator.page())
                && matchesText(tableId, locator.tableId())
                && matchesText(rowKey, locator.rowKey())
                && matchesText(columnKey, locator.columnKey());
        }

        private boolean matchesText(String expected, String actual) {
            return expected == null || expected.equals(normalizeStatic(actual));
        }

        private boolean matchesNumber(Integer expected, Integer actual) {
            return expected == null || expected.equals(actual);
        }

        private static String normalizeStatic(Object value) {
            if (value == null) {
                return null;
            }
            String text = String.valueOf(value).trim().replaceAll("\\s+", " ");
            return text.isEmpty() ? null : text.toLowerCase(Locale.ROOT);
        }
    }
}
