package com.example.demo.service.eval;

import com.example.demo.model.eval.EvalCiReport;
import com.example.demo.model.eval.EvalRun;
import com.example.demo.model.eval.EvalRunCompare;
import com.example.demo.model.eval.EvalRunItemStatus;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.util.StringUtils;

record EvalCiReportReleaseMetadata(
    String runStatus,
    String mappingHash,
    String caseRevisionRefHash,
    String overallVerdict,
    Map<String, Object> metricSummary,
    Map<String, Long> itemCounts,
    List<EvalCiReport.ArtifactLink> artifacts
) {

    static EvalCiReportReleaseMetadata from(
        EvalRun run,
        EvalRunCompare compare,
        List<EvalCiReport.Failure> failures,
        EvalCiReport.Compatibility compatibility
    ) {
        return new EvalCiReportReleaseMetadata(
            run.status() == null ? null : run.status().name(),
            mappingHash(run),
            text(run.summary().get("caseRevisionRefHash")),
            overallVerdict(run, compare, failures, compatibility),
            metricSummary(compare),
            itemCounts(run),
            List.of()
        );
    }

    private static String mappingHash(EvalRun run) {
        String fromSummary = text(run.summary().get("mappingHash"));
        if (StringUtils.hasText(fromSummary)) {
            return fromSummary;
        }
        String fromOptions = text(run.executionConfig().options().get("mappingHash"));
        if (StringUtils.hasText(fromOptions)) {
            return fromOptions;
        }
        String fromSearchSync = text(nested(run.executionConfig().options(), "searchSync", "mappingHash"));
        if (StringUtils.hasText(fromSearchSync)) {
            return fromSearchSync;
        }
        return text(run.executionConfig().rolloutFlags().get("mappingHash"));
    }

    private static String overallVerdict(
        EvalRun run,
        EvalRunCompare compare,
        List<EvalCiReport.Failure> failures,
        EvalCiReport.Compatibility compatibility
    ) {
        if (compare != null) {
            String compareVerdict = text(compare.summary().get("overallVerdict"));
            if (StringUtils.hasText(compareVerdict)) {
                return compareVerdict;
            }
        }
        String runVerdict = text(run.summary().get("overallVerdict"));
        if (!StringUtils.hasText(runVerdict)) {
            runVerdict = text(run.summary().get("verdict"));
        }
        if (StringUtils.hasText(runVerdict)) {
            return runVerdict.toUpperCase(Locale.ROOT);
        }
        if (run.status() == null || !"COMPLETED".equals(run.status().name())) {
            return "FAIL";
        }
        if (!failures.isEmpty()) {
            return "FAIL";
        }
        if ("BLOCKED".equals(compatibility.status()) || "INCOMPATIBLE".equals(compatibility.status())) {
            return "BLOCKED";
        }
        return "PASS";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> metricSummary(EvalRunCompare compare) {
        if (compare == null) {
            return Map.of();
        }
        Object value = compare.summary().get("metricSummary");
        if (!(value instanceof Map<?, ?> rawMap)) {
            return Map.of();
        }
        return new LinkedHashMap<>((Map<String, Object>) rawMap);
    }

    private static Map<String, Long> itemCounts(EvalRun run) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("total", (long) run.items().size());
        counts.put("passed", run.items().stream().filter(item -> item.status() == EvalRunItemStatus.PASSED).count());
        counts.put("failed", run.items().stream().filter(item -> item.status() == EvalRunItemStatus.FAILED).count());
        counts.put("error", run.items().stream().filter(item -> item.status() == EvalRunItemStatus.ERROR).count());
        counts.put("open", run.items().stream()
            .filter(item -> item.status() == EvalRunItemStatus.PENDING || item.status() == EvalRunItemStatus.RUNNING)
            .count());
        return counts;
    }

    @SuppressWarnings("unchecked")
    private static Object nested(Map<String, Object> map, String firstKey, String secondKey) {
        Object first = map.get(firstKey);
        if (!(first instanceof Map<?, ?> firstMap)) {
            return null;
        }
        return ((Map<String, Object>) firstMap).get(secondKey);
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
