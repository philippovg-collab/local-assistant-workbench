package com.example.demo.service.eval;

import com.example.demo.model.eval.EvalCase;
import com.example.demo.model.eval.EvalCaseSeverity;
import com.example.demo.model.eval.EvalCiReport;
import com.example.demo.model.eval.EvalDataset;
import com.example.demo.model.eval.EvalExpectedMode;
import com.example.demo.model.eval.EvalRun;
import com.example.demo.model.eval.EvalRunCompare;
import com.example.demo.model.eval.EvalRunItem;
import com.example.demo.model.eval.EvalRunItemStatus;
import com.example.demo.model.eval.RetrievalEvalMetric;
import com.example.demo.model.eval.RetrievalEvalMetricStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class EvalCiReportService {

    public static final String SCHEMA_VERSION = "eval-ci-report/v1";

    private static final Map<String, String> METRIC_ALIASES = Map.of(
        "accepted_answer_match", "answer_correctness",
        "required_gold_fact_coverage", "groundedness",
        "citation_locator_match", "citation_precision",
        "abstain_recall", "abstention_recall"
    );

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public EvalCiReportService(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper.copy()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.clock = clock;
    }

    public EvalCiReport createReport(
        String mode,
        EvalDataset dataset,
        List<EvalCase> cases,
        EvalRun run,
        EvalRunCompare compare
    ) {
        Map<String, EvalCase> casesById = casesById(cases);
        Map<String, MetricAccumulator> metricAccumulators = new LinkedHashMap<>();
        collectMetrics(metricAccumulators, run.summary());
        for (EvalRunItem item : run.items()) {
            collectItemMetrics(metricAccumulators, item);
        }

        List<EvalCiReport.Failure> failures = failures(run, casesById);
        EvalCiReport.Compatibility compatibility = compatibility(compare);
        EvalCiReportReleaseMetadata releaseMetadata = EvalCiReportReleaseMetadata.from(
            run,
            compare,
            failures,
            compatibility
        );
        return new EvalCiReport(
            SCHEMA_VERSION,
            normalizeMode(mode),
            preliminaryGateStatus(run, failures, compatibility),
            run.id(),
            run.runKind(),
            dataset.kind(),
            datasetVersion(dataset, run),
            snapshotId(run),
            configHash(run),
            clock.instant(),
            metrics(metricAccumulators),
            slices(run, casesById),
            failures,
            regressions(compare),
            compatibility,
            releaseMetadata.runStatus(),
            releaseMetadata.mappingHash(),
            releaseMetadata.caseRevisionRefHash(),
            releaseMetadata.overallVerdict(),
            releaseMetadata.metricSummary(),
            releaseMetadata.itemCounts(),
            releaseMetadata.artifacts()
        );
    }

    public void writeReport(EvalCiReport report, Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        String mode = normalizeMode(report.mode());
        objectMapper.writerWithDefaultPrettyPrinter()
            .writeValue(outputDirectory.resolve(mode + "-report.json").toFile(), report);
        Files.writeString(outputDirectory.resolve(mode + "-report.md"), markdown(report));
    }

    private Map<String, EvalCase> casesById(List<EvalCase> cases) {
        Map<String, EvalCase> byId = new LinkedHashMap<>();
        if (cases != null) {
            for (EvalCase evalCase : cases) {
                byId.put(evalCase.id(), evalCase);
            }
        }
        return byId;
    }

    private String preliminaryGateStatus(
        EvalRun run,
        List<EvalCiReport.Failure> failures,
        EvalCiReport.Compatibility compatibility
    ) {
        if (run.status() == null || !"COMPLETED".equals(run.status().name())) {
            return "FAIL";
        }
        if (!failures.isEmpty()) {
            return "FAIL";
        }
        if ("BLOCKED".equals(compatibility.status()) || "INCOMPATIBLE".equals(compatibility.status())) {
            return "FAIL";
        }
        return "PASS";
    }

    private String datasetVersion(EvalDataset dataset, EvalRun run) {
        if (StringUtils.hasText(run.executionConfig().datasetVersion())) {
            return run.executionConfig().datasetVersion();
        }
        return dataset.version();
    }

    private String snapshotId(EvalRun run) {
        if (StringUtils.hasText(run.snapshotId())) {
            return run.snapshotId();
        }
        return run.executionConfig().corpusSnapshotId();
    }

    private String configHash(EvalRun run) {
        if (StringUtils.hasText(run.configHash())) {
            return run.configHash();
        }
        return run.executionConfig().configHash();
    }

    private List<EvalCiReport.Failure> failures(EvalRun run, Map<String, EvalCase> casesById) {
        List<EvalCiReport.Failure> failures = new ArrayList<>();
        for (EvalRunItem item : run.items()) {
            if (item.status() == EvalRunItemStatus.FAILED || item.status() == EvalRunItemStatus.ERROR) {
                EvalCase evalCase = casesById.get(item.caseId());
                failures.add(new EvalCiReport.Failure(
                    item.failureCode() == null ? item.status().name() : item.failureCode().name(),
                    StringUtils.hasText(item.failureMessage()) ? item.failureMessage() : "Eval item did not pass",
                    severity(evalCase),
                    item.caseId(),
                    null,
                    null,
                    null
                ));
            }
        }
        return List.copyOf(failures);
    }

    private List<EvalCiReport.Slice> slices(EvalRun run, Map<String, EvalCase> casesById) {
        Map<String, SliceAccumulator> slices = new LinkedHashMap<>();
        for (EvalRunItem item : run.items()) {
            EvalCase evalCase = casesById.get(item.caseId());
            String sliceName = sliceName(evalCase);
            SliceAccumulator slice = slices.computeIfAbsent(sliceName, ignored -> new SliceAccumulator(sliceName));
            slice.severity = highestSeverity(slice.severity, severity(evalCase));
            collectItemMetrics(slice.metrics, item);
            if (item.status() == EvalRunItemStatus.FAILED || item.status() == EvalRunItemStatus.ERROR) {
                slice.failures.add(new EvalCiReport.Failure(
                    item.failureCode() == null ? item.status().name() : item.failureCode().name(),
                    StringUtils.hasText(item.failureMessage()) ? item.failureMessage() : "Eval item did not pass",
                    severity(evalCase),
                    item.caseId(),
                    null,
                    null,
                    null
                ));
            }
        }
        return slices.values().stream()
            .map(slice -> new EvalCiReport.Slice(
                slice.name,
                slice.severity,
                metrics(slice.metrics),
                slice.failures
            ))
            .toList();
    }

    private String sliceName(EvalCase evalCase) {
        if (evalCase == null) {
            return "ALL";
        }
        if (evalCase.expectedMode() == EvalExpectedMode.ABSTAIN) {
            return "NO_ANSWER";
        }
        if (evalCase.expectedMode() == EvalExpectedMode.CLARIFY) {
            return "AMBIGUOUS_QUERY";
        }
        return evalCase.caseType() == null ? "ALL" : evalCase.caseType().name();
    }

    private String severity(EvalCase evalCase) {
        return evalCase == null || evalCase.severity() == null ? EvalCaseSeverity.MEDIUM.name() : evalCase.severity().name();
    }

    private String highestSeverity(String left, String right) {
        return severityRank(right) < severityRank(left) ? right : left;
    }

    private int severityRank(String severity) {
        return switch (String.valueOf(severity).toUpperCase(Locale.ROOT)) {
            case "BLOCKER" -> 0;
            case "HIGH", "CRITICAL" -> 1;
            case "MEDIUM", "MAJOR" -> 2;
            case "LOW", "MINOR" -> 3;
            default -> 4;
        };
    }

    private void collectItemMetrics(Map<String, MetricAccumulator> accumulators, EvalRunItem item) {
        if (!item.scoreSummary().isEmpty()) {
            collectMetrics(accumulators, item.scoreSummary());
        } else {
            collectMetrics(accumulators, item.scorer());
        }
    }

    @SuppressWarnings("unchecked")
    private void collectMetrics(Map<String, MetricAccumulator> accumulators, Object payload) {
        if (payload instanceof RetrievalEvalMetric metric) {
            collectRetrievalMetric(accumulators, metric);
            return;
        }
        if (payload instanceof List<?> list) {
            for (Object item : list) {
                collectMetrics(accumulators, item);
            }
            return;
        }
        if (!(payload instanceof Map<?, ?> rawMap)) {
            return;
        }
        Map<String, Object> map = (Map<String, Object>) rawMap;
        Object nestedMetrics = map.get("metrics");
        if (nestedMetrics != null && nestedMetrics != map) {
            collectMetrics(accumulators, nestedMetrics);
        }
        if (map.containsKey("name") && map.containsKey("value")) {
            collectMetricMap(accumulators, map);
            return;
        }
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String metricName = canonicalMetricName(entry.getKey());
            Object value = entry.getValue();
            if ("metrics".equals(entry.getKey()) || shouldSkipMetricKey(entry.getKey())) {
                continue;
            }
            if (value instanceof Number number) {
                accumulators.computeIfAbsent(metricName, MetricAccumulator::new).add(number.doubleValue());
            } else if (value instanceof Map<?, ?> nestedMap) {
                Map<String, Object> typed = (Map<String, Object>) nestedMap;
                Optional<Double> numericValue = numericMetricValue(typed);
                numericValue.ifPresent(metricValue ->
                    accumulators.computeIfAbsent(metricName, MetricAccumulator::new).add(metricValue)
                );
            } else if (value instanceof List<?> list) {
                collectMetrics(accumulators, list);
            }
        }
    }

    private void collectRetrievalMetric(Map<String, MetricAccumulator> accumulators, RetrievalEvalMetric metric) {
        if (metric.status() == RetrievalEvalMetricStatus.SCORED && metric.value() != null) {
            accumulators.computeIfAbsent(canonicalMetricName(metric.name()), MetricAccumulator::new).add(metric.value());
        }
    }

    private void collectMetricMap(Map<String, MetricAccumulator> accumulators, Map<String, Object> map) {
        Object status = map.get("status");
        if (status != null && !"SCORED".equals(String.valueOf(status)) && !"PASS".equals(String.valueOf(status))) {
            return;
        }
        Object name = map.get("name");
        Optional<Double> value = numericMetricValue(map);
        if (name != null && value.isPresent()) {
            accumulators.computeIfAbsent(canonicalMetricName(String.valueOf(name)), MetricAccumulator::new).add(value.get());
        }
    }

    private Optional<Double> numericMetricValue(Map<String, Object> map) {
        Object value = map.get("value");
        if (!(value instanceof Number)) {
            value = map.get("average");
        }
        if (value instanceof Number number) {
            return Optional.of(number.doubleValue());
        }
        return Optional.empty();
    }

    private boolean shouldSkipMetricKey(String key) {
        return key.endsWith("Count")
            || "details".equals(key)
            || "tags".equals(key)
            || "runKind".equals(key)
            || "judgeMode".equals(key)
            || "totalItems".equals(key)
            || "passedItems".equals(key)
            || "failedItems".equals(key)
            || "errorItems".equals(key)
            || "openItems".equals(key)
            || "completedItems".equals(key)
            || "linkedChatRunItems".equals(key)
            || "notScorableItems".equals(key)
            || "failureCodes".equals(key);
    }

    private Map<String, EvalCiReport.Metric> metrics(Map<String, MetricAccumulator> accumulators) {
        Map<String, EvalCiReport.Metric> metrics = new LinkedHashMap<>();
        accumulators.values().stream()
            .sorted(Comparator.comparing(accumulator -> accumulator.name))
            .forEach(accumulator -> metrics.put(
                accumulator.name,
                new EvalCiReport.Metric(accumulator.average(), accumulator.count, "OBSERVED", null)
            ));
        return metrics;
    }

    private String canonicalMetricName(String name) {
        return METRIC_ALIASES.getOrDefault(name, name);
    }

    private EvalCiReport.Compatibility compatibility(EvalRunCompare compare) {
        if (compare == null) {
            return EvalCiReport.Compatibility.compatible();
        }
        List<Map<String, Object>> reasons = compare.compatibilityReasons().stream()
            .map(reason -> {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("code", reason.code());
                payload.put("message", reason.message());
                payload.put("status", reason.status() == null ? null : reason.status().name());
                payload.put("details", reason.details());
                return payload;
            })
            .toList();
        String status = compare.compatibilityStatus() == null ? compare.status().name() : compare.compatibilityStatus().name();
        return new EvalCiReport.Compatibility(status, reasons);
    }

    @SuppressWarnings("unchecked")
    private List<EvalCiReport.Regression> regressions(EvalRunCompare compare) {
        if (compare == null) {
            return List.of();
        }
        Object value = compare.summary().get("worstRegressions");
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<EvalCiReport.Regression> regressions = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> rawMap) {
                Map<String, Object> map = (Map<String, Object>) rawMap;
                regressions.add(new EvalCiReport.Regression(
                    text(map.get("metric")),
                    textOrDefault(map.get("severity"), "MEDIUM"),
                    text(map.get("caseId")),
                    number(map.get("baseline")),
                    number(map.get("candidate")),
                    number(map.get("delta")),
                    text(map.get("message"))
                ));
            }
        }
        return regressions.stream()
            .sorted(Comparator
                .comparingInt((EvalCiReport.Regression regression) -> severityRank(regression.severity()))
                .thenComparing(regression -> Objects.requireNonNullElse(regression.delta(), 0.0d)))
            .toList();
    }

    private Double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String textOrDefault(Object value, String fallback) {
        String text = text(value);
        return StringUtils.hasText(text) ? text : fallback;
    }

    private String markdown(EvalCiReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Eval ").append(report.mode()).append(" report\n\n");
        builder.append("- Gate status: ").append(report.gateStatus()).append("\n");
        builder.append("- Run: ").append(report.runId()).append("\n");
        builder.append("- Dataset: ").append(report.datasetKind()).append(" ").append(report.datasetVersion()).append("\n");
        builder.append("- Snapshot: ").append(nullToDash(report.snapshotId())).append("\n");
        builder.append("- Config hash: ").append(nullToDash(report.configHash())).append("\n\n");
        if (!report.failures().isEmpty()) {
            builder.append("## Failures\n\n");
            for (EvalCiReport.Failure failure : report.failures()) {
                builder.append("- [").append(failure.severity()).append("] ")
                    .append(failure.code()).append(" case=").append(nullToDash(failure.caseId()))
                    .append(": ").append(failure.message()).append("\n");
            }
            builder.append("\n");
        }
        builder.append("## Metrics\n\n");
        builder.append("| Metric | Value | Count |\n");
        builder.append("| --- | ---: | ---: |\n");
        report.metrics().forEach((name, metric) -> builder
            .append("| ").append(name)
            .append(" | ").append(String.format(Locale.ROOT, "%.4f", metric.value()))
            .append(" | ").append(metric.count()).append(" |\n"));
        builder.append("\n");
        builder.append("## Worst Regressions\n\n");
        if (report.worstRegressions().isEmpty()) {
            builder.append("- None\n");
        } else {
            for (EvalCiReport.Regression regression : report.worstRegressions()) {
                builder.append("- [").append(regression.severity()).append("] ")
                    .append(regression.metric()).append(" delta=")
                    .append(regression.delta() == null ? "-" : String.format(Locale.ROOT, "%.4f", regression.delta()))
                    .append(" case=").append(nullToDash(regression.caseId())).append("\n");
            }
        }
        return builder.toString();
    }

    private String nullToDash(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }

    private String normalizeMode(String mode) {
        return StringUtils.hasText(mode) ? mode.toLowerCase(Locale.ROOT) : "smoke";
    }

    private static final class MetricAccumulator {
        private final String name;
        private int count;
        private double sum;

        private MetricAccumulator(String name) {
            this.name = name;
        }

        private void add(double value) {
            count++;
            sum += value;
        }

        private double average() {
            return count == 0 ? 0.0d : sum / count;
        }
    }

    private static final class SliceAccumulator {
        private final String name;
        private final Map<String, MetricAccumulator> metrics = new LinkedHashMap<>();
        private final List<EvalCiReport.Failure> failures = new ArrayList<>();
        private String severity = EvalCaseSeverity.MEDIUM.name();

        private SliceAccumulator(String name) {
            this.name = name;
        }
    }
}
