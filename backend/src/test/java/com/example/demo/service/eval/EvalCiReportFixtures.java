package com.example.demo.service.eval;

import com.example.demo.model.eval.EvalCase;
import com.example.demo.model.eval.EvalCaseOrigin;
import com.example.demo.model.eval.EvalCaseSeverity;
import com.example.demo.model.eval.EvalCaseType;
import com.example.demo.model.eval.EvalCiReport;
import com.example.demo.model.eval.EvalCompareStatus;
import com.example.demo.model.eval.EvalCompatibilityStatus;
import com.example.demo.model.eval.EvalDataset;
import com.example.demo.model.eval.EvalDatasetKind;
import com.example.demo.model.eval.EvalE2EScoreSummary;
import com.example.demo.model.eval.EvalExecutionConfig;
import com.example.demo.model.eval.EvalExpectedMode;
import com.example.demo.model.eval.EvalFailureCode;
import com.example.demo.model.eval.EvalLifecycleStatus;
import com.example.demo.model.eval.EvalRun;
import com.example.demo.model.eval.EvalRunCompare;
import com.example.demo.model.eval.EvalRunItem;
import com.example.demo.model.eval.EvalRunItemStatus;
import com.example.demo.model.eval.EvalRunKind;
import com.example.demo.model.eval.EvalRunStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

final class EvalCiReportFixtures {

    static final ZoneOffset UTC = ZoneOffset.UTC;
    static final Instant GENERATED_AT = Instant.parse("2026-05-11T00:00:00Z");

    private final Clock clock;
    private final EvalCiReportService reportService;

    EvalCiReportFixtures(Clock clock) {
        this.clock = clock;
        this.reportService = new EvalCiReportService(new ObjectMapper().findAndRegisterModules(), clock);
    }

    EvalCiReportService reportService() {
        return reportService;
    }

    EvalCiReport report(String mode) {
        return report(mode, EvalRunItemStatus.PASSED);
    }

    EvalCiReport report(String mode, EvalRunItemStatus firstItemStatus) {
        EvalDataset dataset = dataset(mode);
        List<EvalCase> cases = cases(dataset.id());
        EvalRun run = run(mode, dataset, cases, firstItemStatus);
        EvalRunCompare compare = "compare".equals(mode) ? compare(run) : null;
        return reportService.createReport(mode, dataset, cases, run, compare);
    }

    private EvalDataset dataset(String mode) {
        EvalDatasetKind kind = "smoke".equals(mode) ? EvalDatasetKind.SMOKE : EvalDatasetKind.GOLDEN;
        return new EvalDataset(
            "dataset-" + mode,
            kind.name().toLowerCase() + "-ci",
            kind,
            "2026.05.11",
            EvalLifecycleStatus.ACTIVE,
            kind.name() + " CI dataset",
            "Deterministic CI fixture dataset",
            List.of("ci", mode),
            clock.instant(),
            clock.instant()
        );
    }

    private List<EvalCase> cases(String datasetId) {
        return List.of(
            evalCase("case-answer", datasetId, EvalCaseType.EXACT_FACT, EvalExpectedMode.ANSWER),
            evalCase("case-no-answer", datasetId, EvalCaseType.NO_ANSWER, EvalExpectedMode.ABSTAIN),
            evalCase("case-ambiguous", datasetId, EvalCaseType.AMBIGUOUS_QUERY, EvalExpectedMode.CLARIFY),
            evalCase("case-retrieval", datasetId, EvalCaseType.DATE_VERSION_FILTER, EvalExpectedMode.ANSWER)
        );
    }

    private EvalCase evalCase(
        String id,
        String datasetId,
        EvalCaseType caseType,
        EvalExpectedMode expectedMode
    ) {
        return new EvalCase(
            id,
            datasetId,
            id,
            1,
            caseType,
            expectedMode,
            EvalCaseSeverity.BLOCKER,
            "Question for " + id,
            Map.of(),
            Map.of(),
            List.of("required fact"),
            List.of("accepted answer"),
            List.of(),
            List.of(),
            List.of(),
            List.of("ci"),
            new EvalCaseOrigin("manual", "ci-fixture", null, Map.of()),
            null,
            true,
            clock.instant(),
            clock.instant()
        );
    }

    private EvalRun run(
        String mode,
        EvalDataset dataset,
        List<EvalCase> cases,
        EvalRunItemStatus firstItemStatus
    ) {
        List<EvalRunItem> items = List.of(
            item("item-answer", "run-" + mode, cases.get(0), firstItemStatus, e2eMetrics()),
            item("item-no-answer", "run-" + mode, cases.get(1), EvalRunItemStatus.PASSED, Map.of(
                "metrics",
                Map.of("abstain_recall", 1.0d, "output_format_validity", 1.0d)
            )),
            item("item-ambiguous", "run-" + mode, cases.get(2), EvalRunItemStatus.PASSED, Map.of(
                "metrics",
                Map.of("clarification_recall", 1.0d, "output_format_validity", 1.0d)
            )),
            item("item-retrieval", "run-" + mode, cases.get(3), EvalRunItemStatus.PASSED, retrievalMetrics())
        );
        return new EvalRun(
            "run-" + mode,
            dataset.id(),
            "snapshot-ci",
            EvalRunKind.E2E,
            EvalRunStatus.COMPLETED,
            executionConfig(dataset),
            "config-ci-hash",
            clock.instant(),
            clock.instant(),
            Map.of("runKind", EvalRunKind.E2E.name(), "totalItems", items.size()),
            items,
            clock.instant(),
            clock.instant()
        );
    }

    private EvalRunItem item(
        String id,
        String runId,
        EvalCase evalCase,
        EvalRunItemStatus status,
        Map<String, Object> scoreSummary
    ) {
        return new EvalRunItem(
            id,
            runId,
            evalCase.id(),
            evalCase.revision(),
            "chat-" + id,
            "context-" + id,
            status,
            status == EvalRunItemStatus.PASSED ? null : EvalFailureCode.OUTPUT_FORMAT_ERROR,
            status == EvalRunItemStatus.PASSED ? null : "Structured output was malformed",
            Map.of("caseKey", evalCase.caseKey()),
            scoreSummary,
            scoreSummary,
            clock.instant(),
            clock.instant()
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> e2eMetrics() {
        EvalE2EScoreSummary summary = new EvalE2EScoreSummary(
            true,
            5,
            0,
            Map.of(
                "accepted_answer_match", 1.0d,
                "required_gold_fact_coverage", 1.0d,
                "citation_locator_match", 1.0d,
                "instruction_adherence", 1.0d,
                "output_format_validity", 1.0d
            ),
            Map.of()
        );
        return new ObjectMapper().convertValue(summary, Map.class);
    }

    private Map<String, Object> retrievalMetrics() {
        return Map.of(
            "metrics",
            Map.of(
                "doc_recall@k", Map.of("average", 1.0d, "count", 1),
                "filter_adherence_retrieval", Map.of("average", 1.0d, "count", 1),
                "forbidden_doc_rate", Map.of("average", 0.0d, "count", 1)
            )
        );
    }

    private EvalExecutionConfig executionConfig(EvalDataset dataset) {
        return new EvalExecutionConfig(
            "ci-git-sha",
            dataset.id(),
            dataset.version(),
            "snapshot-ci",
            GENERATED_AT,
            "material-set-ci",
            "search-state-ci",
            "config-ci-hash",
            "prompt-ci",
            "judge-prompt-ci",
            Map.of("llm", "deterministic"),
            Map.of("topK", 5),
            Map.of(),
            Map.of(),
            Map.of("ciSafe", true, "mappingHash", "mapping-ci")
        );
    }

    private EvalRunCompare compare(EvalRun run) {
        Map<String, Object> answerCorrectnessSummary = Map.of(
            "metric", "answer_correctness",
            "baselineValue", 1.0d,
            "candidateValue", 1.0d,
            "delta", 0.0d,
            "relativeDelta", 0.0d,
            "direction", "HIGHER_IS_BETTER",
            "verdict", "UNCHANGED",
            "sampleSize", 1,
            "nonScorableCount", 0
        );
        return new EvalRunCompare(
            "compare-ci",
            "baseline-run",
            run.id(),
            EvalCompareStatus.COMPATIBLE,
            EvalCompatibilityStatus.COMPATIBLE,
            List.of(),
            null,
            run.executionConfig().datasetVersion(),
            run.executionConfig().datasetVersion(),
            run.snapshotId(),
            run.snapshotId(),
            "material-set-ci",
            "material-set-ci",
            "search-state-ci",
            "search-state-ci",
            run.configHash(),
            run.configHash(),
            Map.of(
                "mappingHash", "mapping-ci",
                "overallVerdict", "PASS",
                "metricSummary", Map.of("answer_correctness", answerCorrectnessSummary),
                "worstRegressions", List.of(
                    Map.of(
                        "metric", "answer_correctness",
                        "severity", "LOW",
                        "baseline", 1.0d,
                        "candidate", 1.0d,
                        "delta", 0.0d,
                        "message", "No observed regression"
                    )
                )
            ),
            clock.instant(),
            clock.instant()
        );
    }
}
