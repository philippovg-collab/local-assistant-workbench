package com.example.demo.service.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.model.eval.CorpusSnapshot;
import com.example.demo.model.eval.CorpusSnapshotItem;
import com.example.demo.model.eval.EvalCompareRequest;
import com.example.demo.model.eval.EvalCompareStatus;
import com.example.demo.model.eval.EvalCompatibilityStatus;
import com.example.demo.model.eval.EvalExecutionConfig;
import com.example.demo.model.eval.EvalFailureCode;
import com.example.demo.model.eval.EvalLifecycleStatus;
import com.example.demo.model.eval.EvalRun;
import com.example.demo.model.eval.EvalRunCompare;
import com.example.demo.model.eval.EvalRunItem;
import com.example.demo.model.eval.EvalRunItemStatus;
import com.example.demo.model.eval.EvalRunKind;
import com.example.demo.model.eval.EvalRunStatus;
import com.example.demo.service.eval.port.CorpusSnapshotRepository;
import com.example.demo.service.eval.port.EvalCompareRepository;
import com.example.demo.service.eval.port.EvalCorpusReader;
import com.example.demo.service.eval.port.EvalRunRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EvalComparisonServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-10T00:00:00Z");
    private static final String SNAPSHOT_ID = "00000000-0000-0000-0000-000000000101";
    private static final String BASELINE_RUN_ID = "00000000-0000-0000-0000-000000000201";
    private static final String CANDIDATE_RUN_ID = "00000000-0000-0000-0000-000000000202";

    @Test
    void compareAllowsGitDifferenceAsWarningOnly() {
        Fixture fixture = new Fixture();
        fixture.snapshots.put(SNAPSHOT_ID, snapshot(SNAPSHOT_ID, "materials", "search", "config"));
        fixture.runs.put(BASELINE_RUN_ID, e2eRun(BASELINE_RUN_ID, "config", "commit-a", completeE2EItem(BASELINE_RUN_ID, 1.0d, 1.0d, 1.0d)));
        fixture.runs.put(CANDIDATE_RUN_ID, e2eRun(CANDIDATE_RUN_ID, "config", "commit-b", completeE2EItem(CANDIDATE_RUN_ID, 1.0d, 1.0d, 1.0d)));

        EvalRunCompare compare = fixture.service.createCompare(new EvalCompareRequest(BASELINE_RUN_ID, CANDIDATE_RUN_ID));

        assertEquals(EvalCompatibilityStatus.WARNING_ONLY, compare.compatibilityStatus());
        assertEquals(EvalCompareStatus.COMPATIBLE, compare.status());
        assertEquals("PASS", compare.summary().get("overallVerdict"));
        assertTrue(compare.compatibilityReasons().stream().anyMatch(reason -> "git_commit_sha_differs".equals(reason.code())));
        assertMetric(compare, "answer_correctness", "UNCHANGED", 1.0d, 1.0d);
    }

    @Test
    void compareBlocksIncompatibleSnapshotConfigCaseRefsAndPins() {
        Fixture fixture = new Fixture(List.of("instruction:00000000-0000-0000-0000-000000000999@3"));
        String baselineSnapshotId = SNAPSHOT_ID;
        String candidateSnapshotId = "00000000-0000-0000-0000-000000000102";
        fixture.snapshots.put(baselineSnapshotId, snapshot(baselineSnapshotId, "materials-a", "search-a", "config-a"));
        fixture.snapshots.put(candidateSnapshotId, snapshot(candidateSnapshotId, "materials-b", "search-b", "config-b"));
        fixture.runs.put(BASELINE_RUN_ID, run(
            BASELINE_RUN_ID,
            baselineSnapshotId,
            EvalRunKind.E2E,
            EvalRunStatus.COMPLETED,
            "dataset",
            "v1",
            "config-a",
            "commit",
            "case-refs-a",
            List.of()
        ));
        fixture.runs.put(CANDIDATE_RUN_ID, run(
            CANDIDATE_RUN_ID,
            candidateSnapshotId,
            EvalRunKind.E2E,
            EvalRunStatus.COMPLETED,
            "dataset",
            "v2",
            "config-b",
            "commit",
            "case-refs-b",
            List.of()
        ));

        EvalRunCompare compare = fixture.service.createCompare(new EvalCompareRequest(BASELINE_RUN_ID, CANDIDATE_RUN_ID));

        assertEquals(EvalCompatibilityStatus.BLOCKED, compare.compatibilityStatus());
        assertEquals("BLOCKED", compare.summary().get("overallVerdict"));
        List<String> codes = compare.compatibilityReasons().stream().map(reason -> reason.code()).toList();
        assertTrue(codes.contains("dataset_version_mismatch"));
        assertTrue(codes.contains("corpus_snapshot_mismatch"));
        assertTrue(codes.contains("search_state_hash_mismatch"));
        assertTrue(codes.contains("config_hash_mismatch"));
        assertTrue(codes.contains("case_revision_ref_hash_mismatch"));
        assertTrue(codes.contains("baseline_revision_pins_missing"));
    }

    @Test
    void compareBlocksMappingHashMismatch() {
        Fixture fixture = compatibleFixture();
        fixture.runs.put(BASELINE_RUN_ID, run(
            BASELINE_RUN_ID,
            SNAPSHOT_ID,
            EvalRunKind.RETRIEVAL_ONLY,
            EvalRunStatus.COMPLETED,
            "dataset",
            "v1",
            "config",
            "commit",
            "case-refs",
            "mapping-a",
            List.of()
        ));
        fixture.runs.put(CANDIDATE_RUN_ID, run(
            CANDIDATE_RUN_ID,
            SNAPSHOT_ID,
            EvalRunKind.RETRIEVAL_ONLY,
            EvalRunStatus.COMPLETED,
            "dataset",
            "v1",
            "config",
            "commit",
            "case-refs",
            "mapping-b",
            List.of()
        ));

        EvalRunCompare compare = fixture.service.createCompare(new EvalCompareRequest(BASELINE_RUN_ID, CANDIDATE_RUN_ID));

        assertEquals(EvalCompatibilityStatus.BLOCKED, compare.compatibilityStatus());
        assertTrue(compare.compatibilityReasons().stream().anyMatch(reason -> "mapping_hash_mismatch".equals(reason.code())));
    }

    @Test
    void compareBlocksNonCompletedRuns() {
        Fixture fixture = compatibleFixture();
        fixture.runs.put(BASELINE_RUN_ID, e2eRun(BASELINE_RUN_ID, "config", "commit", completeE2EItem(BASELINE_RUN_ID, 1.0d, 1.0d, 1.0d)));
        fixture.runs.put(CANDIDATE_RUN_ID, run(
            CANDIDATE_RUN_ID,
            SNAPSHOT_ID,
            EvalRunKind.E2E,
            EvalRunStatus.RUNNING,
            "dataset",
            "v1",
            "config",
            "commit",
            "case-refs",
            List.of()
        ));

        EvalRunCompare compare = fixture.service.createCompare(new EvalCompareRequest(BASELINE_RUN_ID, CANDIDATE_RUN_ID));

        assertEquals(EvalCompatibilityStatus.BLOCKED, compare.compatibilityStatus());
        assertTrue(compare.compatibilityReasons().stream().anyMatch(reason -> "run_not_completed".equals(reason.code())));
    }

    @Test
    void retrievalRegressionFailsOverallVerdict() {
        Fixture fixture = compatibleFixture();
        fixture.runs.put(BASELINE_RUN_ID, retrievalRun(BASELINE_RUN_ID, retrievalItem(BASELINE_RUN_ID, 1.0d, 1.0d, 1.0d, 0.0d, 0.0d)));
        fixture.runs.put(CANDIDATE_RUN_ID, retrievalRun(CANDIDATE_RUN_ID, retrievalItem(CANDIDATE_RUN_ID, 0.5d, 0.75d, 1.0d, 0.0d, 0.0d)));

        EvalRunCompare compare = fixture.service.createCompare(new EvalCompareRequest(BASELINE_RUN_ID, CANDIDATE_RUN_ID));

        assertEquals(EvalCompatibilityStatus.COMPATIBLE, compare.compatibilityStatus());
        assertEquals("FAIL", compare.summary().get("overallVerdict"));
        assertMetric(compare, "mrr", "REGRESSED", 1.0d, 0.5d);
        assertMetric(compare, "ndcg", "REGRESSED", 1.0d, 0.75d);
    }

    @Test
    void e2eImprovementPassesOverallVerdict() {
        Fixture fixture = compatibleFixture();
        fixture.runs.put(BASELINE_RUN_ID, e2eRun(BASELINE_RUN_ID, "config", "commit", completeE2EItem(BASELINE_RUN_ID, 0.5d, 1.0d, 1.0d)));
        fixture.runs.put(CANDIDATE_RUN_ID, e2eRun(CANDIDATE_RUN_ID, "config", "commit", completeE2EItem(CANDIDATE_RUN_ID, 1.0d, 1.0d, 1.0d)));

        EvalRunCompare compare = fixture.service.createCompare(new EvalCompareRequest(BASELINE_RUN_ID, CANDIDATE_RUN_ID));

        assertEquals(EvalCompatibilityStatus.COMPATIBLE, compare.compatibilityStatus());
        assertEquals("PASS", compare.summary().get("overallVerdict"));
        assertMetric(compare, "answer_correctness", "IMPROVED", 0.5d, 1.0d);
    }

    @Test
    void missingRequiredMetricIsInconclusive() {
        Fixture fixture = compatibleFixture();
        fixture.runs.put(BASELINE_RUN_ID, e2eRun(BASELINE_RUN_ID, "config", "commit", e2eItemWithoutAbstainMetric(BASELINE_RUN_ID)));
        fixture.runs.put(CANDIDATE_RUN_ID, e2eRun(CANDIDATE_RUN_ID, "config", "commit", e2eItemWithoutAbstainMetric(CANDIDATE_RUN_ID)));

        EvalRunCompare compare = fixture.service.createCompare(new EvalCompareRequest(BASELINE_RUN_ID, CANDIDATE_RUN_ID));

        assertEquals(EvalCompatibilityStatus.COMPATIBLE, compare.compatibilityStatus());
        assertEquals("INCONCLUSIVE", compare.summary().get("overallVerdict"));
        assertEquals("INSUFFICIENT_DATA", metric(compare, "abstain_correctness").get("verdict"));
    }

    @Test
    void nonScorableMetricsAreCountedAndExcludedFromAverages() {
        Fixture fixture = compatibleFixture();
        fixture.runs.put(BASELINE_RUN_ID, retrievalRun(BASELINE_RUN_ID, retrievalItemWithNonScorableMrr(BASELINE_RUN_ID)));
        fixture.runs.put(CANDIDATE_RUN_ID, retrievalRun(CANDIDATE_RUN_ID, retrievalItem(CANDIDATE_RUN_ID, 1.0d, 1.0d, 1.0d, 0.0d, 0.0d)));

        EvalRunCompare compare = fixture.service.createCompare(new EvalCompareRequest(BASELINE_RUN_ID, CANDIDATE_RUN_ID));

        Map<String, Object> mrr = metric(compare, "mrr");
        assertEquals("INSUFFICIENT_DATA", mrr.get("verdict"));
        assertEquals(1, mrr.get("baselineNonScorableCount"));
        assertEquals("INCONCLUSIVE", compare.summary().get("overallVerdict"));
    }

    private static Fixture compatibleFixture() {
        Fixture fixture = new Fixture();
        fixture.snapshots.put(SNAPSHOT_ID, snapshot(SNAPSHOT_ID, "materials", "search", "config"));
        return fixture;
    }

    private static EvalRun e2eRun(String id, String configHash, String gitCommitSha, EvalRunItem item) {
        return run(
            id,
            SNAPSHOT_ID,
            EvalRunKind.E2E,
            EvalRunStatus.COMPLETED,
            "dataset",
            "v1",
            configHash,
            gitCommitSha,
            "case-refs",
            List.of(item)
        );
    }

    private static EvalRun retrievalRun(String id, EvalRunItem item) {
        return run(
            id,
            SNAPSHOT_ID,
            EvalRunKind.RETRIEVAL_ONLY,
            EvalRunStatus.COMPLETED,
            "dataset",
            "v1",
            "config",
            "commit",
            "case-refs",
            List.of(item)
        );
    }

    private static EvalRun run(
        String id,
        String snapshotId,
        EvalRunKind runKind,
        EvalRunStatus status,
        String datasetId,
        String datasetVersion,
        String configHash,
        String gitCommitSha,
        String caseRevisionRefHash,
        List<EvalRunItem> items
    ) {
        return run(
            id,
            snapshotId,
            runKind,
            status,
            datasetId,
            datasetVersion,
            configHash,
            gitCommitSha,
            caseRevisionRefHash,
            "mapping",
            items
        );
    }

    private static EvalRun run(
        String id,
        String snapshotId,
        EvalRunKind runKind,
        EvalRunStatus status,
        String datasetId,
        String datasetVersion,
        String configHash,
        String gitCommitSha,
        String caseRevisionRefHash,
        String mappingHash,
        List<EvalRunItem> items
    ) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("runKind", runKind.name());
        summary.put("datasetVersion", datasetVersion);
        summary.put("caseRevisionRefHash", caseRevisionRefHash);
        summary.put("mappingHash", mappingHash);
        return new EvalRun(
            id,
            datasetId,
            snapshotId,
            runKind,
            status,
            new EvalExecutionConfig(
                gitCommitSha,
                datasetId,
                datasetVersion,
                snapshotId,
                NOW,
                "materials",
                "search",
                configHash,
                "production-current",
                null,
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of("mappingHash", mappingHash)
            ),
            configHash,
            NOW,
            status == EvalRunStatus.COMPLETED ? NOW : null,
            summary,
            items,
            NOW,
            NOW
        );
    }

    private static EvalRunItem completeE2EItem(String runId, double answerCorrectness, double grounding, double abstainCorrectness) {
        return e2eItem(runId, EvalRunItemStatus.PASSED, null, Map.of(
            "metrics",
            Map.of(
                "accepted_answer_match", answerCorrectness,
                "required_gold_fact_coverage", grounding,
                "abstain_recall", abstainCorrectness
            )
        ));
    }

    private static EvalRunItem e2eItemWithoutAbstainMetric(String runId) {
        return e2eItem(runId, EvalRunItemStatus.PASSED, null, Map.of(
            "metrics",
            Map.of(
                "accepted_answer_match", 1.0d,
                "required_gold_fact_coverage", 1.0d
            )
        ));
    }

    private static EvalRunItem e2eItem(
        String runId,
        EvalRunItemStatus status,
        EvalFailureCode failureCode,
        Map<String, Object> scoreSummary
    ) {
        return new EvalRunItem(
            runId + "-item",
            runId,
            "case-1",
            1,
            null,
            null,
            status,
            failureCode,
            null,
            Map.of(),
            Map.of(),
            scoreSummary,
            NOW,
            NOW
        );
    }

    private static EvalRunItem retrievalItem(
        String runId,
        double mrr,
        double ndcg,
        double filterAdherence,
        double forbiddenDocRate,
        double noResultsRate
    ) {
        return retrievalItem(runId, List.of(
            scoredMetric("mrr@k", mrr),
            scoredMetric("ndcg@k", ndcg),
            scoredMetric("filter_adherence_retrieval", filterAdherence),
            scoredMetric("forbidden_doc_rate", forbiddenDocRate),
            scoredMetric("retrieval_no_results_rate", noResultsRate)
        ));
    }

    private static EvalRunItem retrievalItemWithNonScorableMrr(String runId) {
        return retrievalItem(runId, List.of(
            Map.of("name", "mrr@k", "status", "NOT_SCORABLE", "reason", "No retrieval candidates were returned"),
            scoredMetric("ndcg@k", 1.0d),
            scoredMetric("filter_adherence_retrieval", 1.0d),
            scoredMetric("forbidden_doc_rate", 0.0d),
            scoredMetric("retrieval_no_results_rate", 0.0d)
        ));
    }

    private static EvalRunItem retrievalItem(String runId, List<Map<String, Object>> metrics) {
        return new EvalRunItem(
            runId + "-item",
            runId,
            "case-1",
            1,
            null,
            null,
            EvalRunItemStatus.PASSED,
            null,
            null,
            Map.of(),
            Map.of("metrics", metrics),
            Map.of(),
            NOW,
            NOW
        );
    }

    private static Map<String, Object> scoredMetric(String name, double value) {
        return Map.of("name", name, "status", "SCORED", "value", value);
    }

    private static CorpusSnapshot snapshot(String id, String materialSetHash, String searchStateHash, String configHash) {
        return new CorpusSnapshot(
            id,
            "snapshot-" + id.substring(id.length() - 3),
            EvalLifecycleStatus.ACTIVE,
            NOW,
            materialSetHash,
            searchStateHash,
            configHash,
            Map.of("revisionPins", Map.of("instructions", List.of(Map.of("id", "00000000-0000-0000-0000-000000000999", "revision", 3)))),
            1,
            1,
            0,
            1,
            Map.of(),
            List.of(),
            NOW,
            NOW
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> metric(EvalRunCompare compare, String name) {
        Object metricSummary = compare.summary().get("metricSummary");
        assertNotNull(metricSummary);
        Object metric = ((Map<String, Object>) metricSummary).get(name);
        assertNotNull(metric);
        return (Map<String, Object>) metric;
    }

    private static void assertMetric(
        EvalRunCompare compare,
        String name,
        String verdict,
        Double baselineValue,
        Double candidateValue
    ) {
        Map<String, Object> metric = metric(compare, name);
        assertEquals(verdict, metric.get("verdict"));
        assertEquals(baselineValue, metric.get("baselineValue"));
        assertEquals(candidateValue, metric.get("candidateValue"));
    }

    private static final class Fixture {
        private final Map<String, EvalRun> runs = new HashMap<>();
        private final Map<String, CorpusSnapshot> snapshots = new HashMap<>();
        private final EvalComparisonService service;

        private Fixture() {
            this(List.of());
        }

        private Fixture(List<String> missingPins) {
            service = new EvalComparisonService(
                new InMemoryRunRepository(runs),
                new InMemorySnapshotRepository(snapshots),
                new CapturingCompareRepository(),
                new StaticCorpusReader(missingPins),
                new EvalCompareMetricSummaryService(),
                Clock.fixed(NOW, ZoneOffset.UTC)
            );
        }
    }

    private record InMemoryRunRepository(Map<String, EvalRun> runs) implements EvalRunRepository {
        @Override
        public List<EvalRun> findRuns() {
            return List.copyOf(runs.values());
        }

        @Override
        public Optional<EvalRun> findRun(String id) {
            return Optional.ofNullable(runs.get(id));
        }

        @Override
        public EvalRun saveRun(EvalRun run) {
            runs.put(run.id(), run);
            return run;
        }

        @Override
        public EvalRunItem saveItem(EvalRunItem item) {
            return item;
        }

        @Override
        public List<EvalRunItem> findItemsByRunId(String runId) {
            return runs.get(runId).items();
        }

        @Override
        public boolean isStorageReady() {
            return true;
        }
    }

    private record InMemorySnapshotRepository(Map<String, CorpusSnapshot> snapshots) implements CorpusSnapshotRepository {
        @Override
        public List<CorpusSnapshot> findSnapshots() {
            return List.copyOf(snapshots.values());
        }

        @Override
        public Optional<CorpusSnapshot> findSnapshot(String id) {
            return Optional.ofNullable(snapshots.get(id));
        }

        @Override
        public CorpusSnapshot saveSnapshot(CorpusSnapshot snapshot) {
            snapshots.put(snapshot.id(), snapshot);
            return snapshot;
        }

        @Override
        public CorpusSnapshotItem saveItem(CorpusSnapshotItem item) {
            return item;
        }

        @Override
        public List<CorpusSnapshotItem> findItemsBySnapshotId(String snapshotId) {
            return List.of();
        }

        @Override
        public List<CorpusSnapshotItem> findItemsBySnapshotId(String snapshotId, int offset, int limit) {
            return List.of();
        }

        @Override
        public boolean isStorageReady() {
            return true;
        }
    }

    private static final class CapturingCompareRepository implements EvalCompareRepository {
        @Override
        public List<EvalRunCompare> findCompares() {
            return List.of();
        }

        @Override
        public Optional<EvalRunCompare> findCompare(String id) {
            return Optional.empty();
        }

        @Override
        public EvalRunCompare saveCompare(EvalRunCompare compare) {
            return compare;
        }

        @Override
        public boolean isStorageReady() {
            return true;
        }
    }

    private record StaticCorpusReader(List<String> missingPins) implements EvalCorpusReader {
        @Override
        public List<EvalCorpusMaterial> readSnapshotMaterials(boolean includeSuperseded) {
            return List.of();
        }

        @Override
        public Map<String, Object> readRevisionPins() {
            return Map.of();
        }

        @Override
        public List<String> findMissingRevisionPins(Map<String, Object> revisionPins) {
            return missingPins;
        }
    }
}
