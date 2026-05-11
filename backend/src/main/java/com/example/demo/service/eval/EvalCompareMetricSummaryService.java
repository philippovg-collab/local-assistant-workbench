package com.example.demo.service.eval;

import com.example.demo.model.eval.EvalCompatibilityStatus;
import com.example.demo.model.eval.EvalFailureCode;
import com.example.demo.model.eval.EvalRun;
import com.example.demo.model.eval.EvalRunItem;
import com.example.demo.model.eval.EvalRunItemStatus;
import com.example.demo.model.eval.EvalRunKind;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class EvalCompareMetricSummaryService {

    private static final String VERDICT_PASS = "PASS";
    private static final String VERDICT_FAIL = "FAIL";
    private static final String VERDICT_BLOCKED = "BLOCKED";
    private static final String VERDICT_INCONCLUSIVE = "INCONCLUSIVE";
    private static final String METRIC_VERDICT_REGRESSED = "REGRESSED";
    private static final String METRIC_VERDICT_IMPROVED = "IMPROVED";
    private static final String METRIC_VERDICT_UNCHANGED = "UNCHANGED";
    private static final String METRIC_VERDICT_INSUFFICIENT_DATA = "INSUFFICIENT_DATA";
    private static final String HIGHER_IS_BETTER = "HIGHER_IS_BETTER";
    private static final String LOWER_IS_BETTER = "LOWER_IS_BETTER";
    private static final List<String> RETRIEVAL_REQUIRED_METRICS = List.of(
        "mrr",
        "ndcg",
        "filter_adherence_retrieval",
        "forbidden_doc_violation_rate",
        "retrieval_no_results_rate"
    );
    private static final List<String> E2E_REQUIRED_METRICS = List.of(
        "output_format_validity",
        "citation_resolution_validity",
        "answer_correctness",
        "grounding",
        "abstain_correctness",
        "e2e_pass_rate"
    );
    private static final Map<String, String> METRIC_ALIASES = Map.ofEntries(
        Map.entry("mrr@k", "mrr"),
        Map.entry("ndcg@k", "ndcg"),
        Map.entry("forbidden_doc_rate", "forbidden_doc_violation_rate"),
        Map.entry("forbidden_doc_violation", "forbidden_doc_violation_rate"),
        Map.entry("accepted_answer_match", "answer_correctness"),
        Map.entry("required_gold_fact_coverage", "grounding"),
        Map.entry("abstain_recall", "abstain_correctness"),
        Map.entry("abstain_precision", "abstain_correctness")
    );
    private static final Set<String> LOWER_IS_BETTER_METRICS = Set.of(
        "forbidden_doc_violation_rate",
        "retrieval_no_results_rate",
        "latency_ms",
        "error_rate"
    );

    Map<String, Object> metricSummary(EvalRun baseline, EvalRun candidate) {
        MetricSnapshot baselineMetrics = metricSnapshot(baseline);
        MetricSnapshot candidateMetrics = metricSnapshot(candidate);
        LinkedHashSet<String> metricNames = new LinkedHashSet<>(requiredMetrics(runKind(baseline)));
        baselineMetrics.metricNames().stream().sorted().forEach(metricNames::add);
        candidateMetrics.metricNames().stream().sorted().forEach(metricNames::add);

        Map<String, Object> summary = new LinkedHashMap<>();
        for (String metricName : metricNames) {
            MetricAccumulator baselineMetric = baselineMetrics.metric(metricName);
            MetricAccumulator candidateMetric = candidateMetrics.metric(metricName);
            EvalMetricDelta delta = delta(metricName, direction(metricName), baselineMetric, candidateMetric);
            summary.put(metricName, delta.asMap());
        }
        return summary;
    }

    String overallVerdict(EvalCompatibilityStatus status, EvalRunKind runKind, Map<String, Object> metricSummary) {
        if (status == EvalCompatibilityStatus.BLOCKED) {
            return VERDICT_BLOCKED;
        }
        boolean hasInsufficientData = false;
        for (String metricName : requiredMetrics(runKind)) {
            String verdict = text(mapValue(metricSummary.get(metricName)).get("verdict"));
            if (METRIC_VERDICT_REGRESSED.equals(verdict)) {
                return VERDICT_FAIL;
            }
            if (METRIC_VERDICT_INSUFFICIENT_DATA.equals(verdict) || isBlank(verdict)) {
                hasInsufficientData = true;
            }
        }
        return hasInsufficientData ? VERDICT_INCONCLUSIVE : VERDICT_PASS;
    }

    List<Map<String, Object>> worstRegressions(EvalRunKind runKind, Map<String, Object> metricSummary) {
        return requiredMetrics(runKind).stream()
            .map(metricName -> mapValue(metricSummary.get(metricName)))
            .filter(metric -> METRIC_VERDICT_REGRESSED.equals(text(metric.get("verdict"))))
            .sorted(Comparator.comparingDouble(this::absoluteDelta).reversed())
            .map(metric -> {
                Map<String, Object> regression = new LinkedHashMap<>();
                regression.put("metric", metric.get("metric"));
                regression.put("severity", "HIGH");
                regression.put("baseline", metric.get("baselineValue"));
                regression.put("candidate", metric.get("candidateValue"));
                regression.put("delta", metric.get("delta"));
                regression.put("message", "Required eval metric regressed");
                return regression;
            })
            .toList();
    }

    private MetricSnapshot metricSnapshot(EvalRun run) {
        MetricSnapshot snapshot = new MetricSnapshot();
        for (EvalRunItem item : run.items()) {
            if (runKind(run) == EvalRunKind.RETRIEVAL_ONLY) {
                collectRetrievalMetrics(snapshot, item);
            } else {
                collectE2EMetrics(snapshot, item);
            }
        }
        collectRunSummaryMetrics(snapshot, run);
        return snapshot;
    }

    private void collectRetrievalMetrics(MetricSnapshot snapshot, EvalRunItem item) {
        boolean sawNoResultsMetric = false;
        Object metrics = item.scorer().get("metrics");
        if (metrics instanceof List<?> list) {
            for (Object rawMetric : list) {
                String metricName = canonicalMetricName(metricField(rawMetric, "name"));
                if (isBlank(metricName)) {
                    continue;
                }
                if ("retrieval_no_results_rate".equals(metricName)) {
                    sawNoResultsMetric = true;
                }
                String status = metricField(rawMetric, "status");
                Double value = number(metricValue(rawMetric, "value"));
                if ("SCORED".equals(status) && value != null) {
                    snapshot.metric(metricName).add(value);
                } else if ("NOT_SCORABLE".equals(status)) {
                    snapshot.metric(metricName).addNonScorable();
                }
            }
        }
        if (!sawNoResultsMetric && isTerminal(item.status())) {
            snapshot.metric("retrieval_no_results_rate").add(item.failureCode() == EvalFailureCode.RETRIEVAL_NO_RESULTS ? 1.0d : 0.0d);
        }
    }

    @SuppressWarnings("unchecked")
    private void collectE2EMetrics(MetricSnapshot snapshot, EvalRunItem item) {
        Map<String, Object> scoreSummary = item.scoreSummary();
        Map<String, Object> metrics = mapValue(scoreSummary.get("metrics"));
        Set<String> seenCanonicalMetrics = new LinkedHashSet<>();
        for (Map.Entry<String, Object> entry : metrics.entrySet()) {
            String metricName = canonicalMetricName(entry.getKey());
            Double value = number(entry.getValue());
            if (!isBlank(metricName) && value != null) {
                snapshot.metric(metricName).add(value);
                seenCanonicalMetrics.add(metricName);
            }
        }
        Object notScorable = mapValue(scoreSummary.get("details")).get("notScorable");
        if (notScorable instanceof List<?> list) {
            for (Object raw : list) {
                String metricName = raw instanceof Map<?, ?> rawMap
                    ? canonicalMetricName(text(((Map<String, Object>) rawMap).get("name")))
                    : null;
                if (!isBlank(metricName)) {
                    snapshot.metric(metricName).addNonScorable();
                }
            }
        }
        if (isTerminal(item.status())) {
            snapshot.metric("e2e_pass_rate").add(item.status() == EvalRunItemStatus.PASSED ? 1.0d : 0.0d);
        }
        if (item.failureCode() == EvalFailureCode.OUTPUT_FORMAT_ERROR) {
            addIfMissing(snapshot, seenCanonicalMetrics, "output_format_validity", 0.0d);
            addIfMissing(snapshot, seenCanonicalMetrics, "citation_resolution_validity", 0.0d);
        } else if (item.failureCode() == EvalFailureCode.CITATION_RESOLUTION_ERROR) {
            addIfMissing(snapshot, seenCanonicalMetrics, "output_format_validity", 1.0d);
            addIfMissing(snapshot, seenCanonicalMetrics, "citation_resolution_validity", 0.0d);
        } else if (!scoreSummary.isEmpty()) {
            addIfMissing(snapshot, seenCanonicalMetrics, "output_format_validity", 1.0d);
            addIfMissing(snapshot, seenCanonicalMetrics, "citation_resolution_validity", 1.0d);
        }
    }

    private void addIfMissing(MetricSnapshot snapshot, Set<String> seenMetrics, String metricName, double value) {
        if (!seenMetrics.contains(metricName)) {
            snapshot.metric(metricName).add(value);
        }
    }

    @SuppressWarnings("unchecked")
    private void collectRunSummaryMetrics(MetricSnapshot snapshot, EvalRun run) {
        Map<String, Object> metrics = mapValue(run.summary().get("metrics"));
        for (Map.Entry<String, Object> entry : metrics.entrySet()) {
            String metricName = canonicalMetricName(entry.getKey());
            if (isBlank(metricName) || snapshot.metric(metricName).sampleSize() > 0) {
                continue;
            }
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> rawMap) {
                Map<String, Object> metricMap = (Map<String, Object>) rawMap;
                Double average = number(firstPresent(metricMap, "average", "value"));
                int count = integer(firstPresent(metricMap, "count", "sampleSize"));
                if (average != null && count > 0) {
                    snapshot.metric(metricName).addAggregate(average, count);
                }
            } else {
                Double numeric = number(value);
                if (numeric != null) {
                    snapshot.metric(metricName).add(numeric);
                }
            }
        }
    }

    private Object firstPresent(Map<String, Object> map, String first, String second) {
        return map.containsKey(first) ? map.get(first) : map.get(second);
    }

    private EvalMetricDelta delta(
        String metricName,
        String direction,
        MetricAccumulator baseline,
        MetricAccumulator candidate
    ) {
        Double baselineValue = baseline.average();
        Double candidateValue = candidate.average();
        String verdict = metricVerdict(direction, baselineValue, candidateValue);
        Double delta = baselineValue == null || candidateValue == null ? null : candidateValue - baselineValue;
        return new EvalMetricDelta(
            metricName,
            baselineValue,
            candidateValue,
            delta,
            relativeDelta(baselineValue, delta),
            direction,
            verdict,
            Math.min(baseline.sampleSize(), candidate.sampleSize()),
            baseline.nonScorableCount() + candidate.nonScorableCount(),
            baseline.sampleSize(),
            candidate.sampleSize(),
            baseline.nonScorableCount(),
            candidate.nonScorableCount()
        );
    }

    private String metricVerdict(String direction, Double baselineValue, Double candidateValue) {
        if (baselineValue == null || candidateValue == null) {
            return METRIC_VERDICT_INSUFFICIENT_DATA;
        }
        int comparison = Double.compare(candidateValue, baselineValue);
        if (comparison == 0) {
            return METRIC_VERDICT_UNCHANGED;
        }
        if (HIGHER_IS_BETTER.equals(direction)) {
            return comparison > 0 ? METRIC_VERDICT_IMPROVED : METRIC_VERDICT_REGRESSED;
        }
        return comparison < 0 ? METRIC_VERDICT_IMPROVED : METRIC_VERDICT_REGRESSED;
    }

    private Double relativeDelta(Double baselineValue, Double delta) {
        if (baselineValue == null || delta == null) {
            return null;
        }
        if (Double.compare(baselineValue, 0.0d) == 0) {
            return Double.compare(delta, 0.0d) == 0 ? 0.0d : null;
        }
        return delta / Math.abs(baselineValue);
    }

    private List<String> requiredMetrics(EvalRunKind runKind) {
        return runKind == EvalRunKind.RETRIEVAL_ONLY ? RETRIEVAL_REQUIRED_METRICS : E2E_REQUIRED_METRICS;
    }

    private String direction(String metricName) {
        if (LOWER_IS_BETTER_METRICS.contains(metricName)
            || metricName.contains("latency")
            || metricName.endsWith("_error_rate")
            || metricName.endsWith("_violation_rate")) {
            return LOWER_IS_BETTER;
        }
        return HIGHER_IS_BETTER;
    }

    private String canonicalMetricName(String name) {
        return isBlank(name) ? null : METRIC_ALIASES.getOrDefault(name, name);
    }

    private EvalRunKind runKind(EvalRun run) {
        return run.runKind() == null ? EvalRunKind.E2E : run.runKind();
    }

    private boolean isTerminal(EvalRunItemStatus status) {
        return status == EvalRunItemStatus.PASSED
            || status == EvalRunItemStatus.FAILED
            || status == EvalRunItemStatus.ERROR
            || status == EvalRunItemStatus.SKIPPED;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> rawMap) {
            return (Map<String, Object>) rawMap;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private String metricField(Object rawMetric, String field) {
        if (rawMetric instanceof Map<?, ?> rawMap) {
            return text(((Map<String, Object>) rawMap).get(field));
        }
        if (rawMetric == null) {
            return null;
        }
        try {
            return text(rawMetric.getClass().getMethod(field).invoke(rawMetric));
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Object metricValue(Object rawMetric, String field) {
        if (rawMetric instanceof Map<?, ?> rawMap) {
            return ((Map<String, Object>) rawMap).get(field);
        }
        if (rawMetric == null) {
            return null;
        }
        try {
            return rawMetric.getClass().getMethod(field).invoke(rawMetric);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private Double number(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException exception) {
                return null;
            }
        }
        return null;
    }

    private int integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException exception) {
                return 0;
            }
        }
        return 0;
    }

    private double absoluteDelta(Map<String, Object> metric) {
        Double delta = number(metric.get("delta"));
        return delta == null ? 0.0d : Math.abs(delta);
    }

    private static final class MetricSnapshot {
        private final Map<String, MetricAccumulator> metrics = new LinkedHashMap<>();

        private MetricAccumulator metric(String name) {
            return metrics.computeIfAbsent(name, ignored -> new MetricAccumulator());
        }

        private Set<String> metricNames() {
            return metrics.keySet();
        }
    }

    private static final class MetricAccumulator {
        private double sum;
        private int sampleSize;
        private int nonScorableCount;

        private void add(double value) {
            sum += value;
            sampleSize++;
        }

        private void addAggregate(double average, int count) {
            sum += average * count;
            sampleSize += count;
        }

        private void addNonScorable() {
            nonScorableCount++;
        }

        private Double average() {
            return sampleSize == 0 ? null : sum / sampleSize;
        }

        private int sampleSize() {
            return sampleSize;
        }

        private int nonScorableCount() {
            return nonScorableCount;
        }
    }

    private record EvalMetricDelta(
        String metric,
        Double baselineValue,
        Double candidateValue,
        Double delta,
        Double relativeDelta,
        String direction,
        String verdict,
        int sampleSize,
        int nonScorableCount,
        int baselineSampleSize,
        int candidateSampleSize,
        int baselineNonScorableCount,
        int candidateNonScorableCount
    ) {
        private Map<String, Object> asMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("metric", metric);
            map.put("baselineValue", baselineValue);
            map.put("candidateValue", candidateValue);
            map.put("delta", delta);
            map.put("relativeDelta", relativeDelta);
            map.put("direction", direction);
            map.put("verdict", verdict);
            map.put("sampleSize", sampleSize);
            map.put("nonScorableCount", nonScorableCount);
            map.put("baselineSampleSize", baselineSampleSize);
            map.put("candidateSampleSize", candidateSampleSize);
            map.put("baselineNonScorableCount", baselineNonScorableCount);
            map.put("candidateNonScorableCount", candidateNonScorableCount);
            return map;
        }
    }
}
