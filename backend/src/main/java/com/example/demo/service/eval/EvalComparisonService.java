package com.example.demo.service.eval;

import com.example.demo.error.ApplicationException;
import com.example.demo.error.ErrorType;
import com.example.demo.model.eval.CorpusSnapshot;
import com.example.demo.model.eval.EvalCompareRequest;
import com.example.demo.model.eval.EvalCompatibilityReason;
import com.example.demo.model.eval.EvalCompatibilityResult;
import com.example.demo.model.eval.EvalCompatibilityStatus;
import com.example.demo.model.eval.EvalExecutionConfig;
import com.example.demo.model.eval.EvalRun;
import com.example.demo.model.eval.EvalRunCompare;
import com.example.demo.model.eval.EvalRunKind;
import com.example.demo.model.eval.EvalRunStatus;
import com.example.demo.service.eval.port.CorpusSnapshotRepository;
import com.example.demo.service.eval.port.EvalCompareRepository;
import com.example.demo.service.eval.port.EvalCorpusReader;
import com.example.demo.service.eval.port.EvalRunRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EvalComparisonService {

    private final EvalRunRepository runRepository;
    private final CorpusSnapshotRepository snapshotRepository;
    private final EvalCompareRepository compareRepository;
    private final EvalCorpusReader corpusReader;
    private final EvalCompareMetricSummaryService metricSummaryService;
    private final Clock clock;

    public EvalComparisonService(
        EvalRunRepository runRepository,
        CorpusSnapshotRepository snapshotRepository,
        EvalCompareRepository compareRepository,
        EvalCorpusReader corpusReader,
        EvalCompareMetricSummaryService metricSummaryService,
        Clock clock
    ) {
        this.runRepository = runRepository;
        this.snapshotRepository = snapshotRepository;
        this.compareRepository = compareRepository;
        this.corpusReader = corpusReader;
        this.metricSummaryService = metricSummaryService;
        this.clock = clock;
    }

    @Transactional
    public EvalRunCompare createCompare(EvalCompareRequest request) {
        if (request == null || isBlank(request.baselineRunId()) || isBlank(request.candidateRunId())) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
                "eval_compare.invalid_request",
                "Fields 'baselineRunId' and 'candidateRunId' are required"
            );
        }
        if (request.baselineRunId().equals(request.candidateRunId())) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
                "eval_compare.same_run",
                "Baseline and candidate eval runs must be different"
            );
        }

        EvalRun baseline = run(request.baselineRunId());
        EvalRun candidate = run(request.candidateRunId());
        CorpusSnapshot baselineSnapshot = snapshotForRun(baseline, "baseline");
        CorpusSnapshot candidateSnapshot = snapshotForRun(candidate, "candidate");
        EvalCompatibilityResult compatibility = compatibility(baseline, candidate, baselineSnapshot, candidateSnapshot);
        String reasonSummary = compatibility.reasons().stream()
            .map(EvalCompatibilityReason::code)
            .reduce((left, right) -> left + "," + right)
            .orElse(null);
        return compareRepository.saveCompare(new EvalRunCompare(
            UUID.randomUUID().toString(),
            baseline.id(),
            candidate.id(),
            EvalRunCompare.compareStatusFrom(compatibility.status()),
            compatibility.status(),
            compatibility.reasons(),
            reasonSummary,
            datasetVersion(baseline),
            datasetVersion(candidate),
            snapshotId(baseline),
            snapshotId(candidate),
            baselineSnapshot == null ? null : baselineSnapshot.materialSetHash(),
            candidateSnapshot == null ? null : candidateSnapshot.materialSetHash(),
            baselineSnapshot == null ? null : baselineSnapshot.searchStateHash(),
            candidateSnapshot == null ? null : candidateSnapshot.searchStateHash(),
            configHash(baseline),
            configHash(candidate),
            compatibility.summary(),
            clock.instant(),
            clock.instant()
        ));
    }

    public EvalCompatibilityResult compatibility(
        EvalRun baseline,
        EvalRun candidate,
        CorpusSnapshot baselineSnapshot,
        CorpusSnapshot candidateSnapshot
    ) {
        List<EvalCompatibilityReason> reasons = new ArrayList<>();
        blockRequiredField(reasons, "dataset_id", "Dataset id", datasetId(baseline), datasetId(candidate));
        blockRequiredField(reasons, "dataset_version", "Dataset version", datasetVersion(baseline), datasetVersion(candidate));
        blockRequiredField(reasons, "corpus_snapshot", "Corpus snapshot", snapshotId(baseline), snapshotId(candidate));
        blockRequiredField(
            reasons,
            "search_state_hash",
            "Search state hash",
            searchStateHash(baseline, baselineSnapshot),
            searchStateHash(candidate, candidateSnapshot)
        );
        blockRequiredField(reasons, "config_hash", "Config hash", configHash(baseline), configHash(candidate));
        blockRequiredField(
            reasons,
            "case_revision_ref_hash",
            "Case revision ref hash",
            caseRevisionRefHash(baseline),
            caseRevisionRefHash(candidate)
        );
        if (baseline.runKind() != candidate.runKind()) {
            block(reasons, "run_kind_mismatch", "Run kind differs", details(baseline.runKind(), candidate.runKind()));
        }
        if (baseline.status() != EvalRunStatus.COMPLETED || candidate.status() != EvalRunStatus.COMPLETED) {
            block(reasons, "run_not_completed", "Both eval runs must be COMPLETED", details(baseline.status(), candidate.status()));
        }
        if (isBlank(snapshotId(baseline)) || baselineSnapshot == null) {
            block(reasons, "baseline_snapshot_missing", "Baseline snapshot artifact is missing", Map.of("runId", baseline.id()));
        }
        if (isBlank(snapshotId(candidate)) || candidateSnapshot == null) {
            block(reasons, "candidate_snapshot_missing", "Candidate snapshot artifact is missing", Map.of("runId", candidate.id()));
        }
        if (isBlank(configHash(baseline))) {
            block(reasons, "baseline_config_missing", "Baseline execution config artifact is missing", Map.of("runId", baseline.id()));
        }
        if (isBlank(configHash(candidate))) {
            block(reasons, "candidate_config_missing", "Candidate execution config artifact is missing", Map.of("runId", candidate.id()));
        }
        addMissingPinReasons(reasons, "baseline", baselineSnapshot);
        addMissingPinReasons(reasons, "candidate", candidateSnapshot);
        if (!isBlank(configHash(baseline))
            && Objects.equals(configHash(baseline), configHash(candidate))
            && !Objects.equals(gitCommitSha(baseline), gitCommitSha(candidate))) {
            warn(reasons, "git_commit_sha_differs", "Git commit SHA differs; recorded as provenance only", details(
                gitCommitSha(baseline),
                gitCommitSha(candidate)
            ));
        }
        addMappingHashCompatibilityReasons(reasons, baseline, candidate);

        EvalCompatibilityStatus status = status(reasons);
        Map<String, Object> metricSummary = metricSummaryService.metricSummary(baseline, candidate);
        String overallVerdict = metricSummaryService.overallVerdict(status, runKind(baseline), metricSummary);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("compatibilityStatus", status.name());
        summary.put("reasonCount", reasons.size());
        summary.put("overallVerdict", overallVerdict);
        summary.put("metricSummary", metricSummary);
        summary.put("worstRegressions", metricSummaryService.worstRegressions(runKind(baseline), metricSummary));
        summary.put("baselineRunId", baseline.id());
        summary.put("candidateRunId", candidate.id());
        summary.put("datasetId", datasetId(baseline));
        summary.put("datasetVersion", datasetVersion(baseline));
        summary.put("corpusSnapshotId", snapshotId(baseline));
        summary.put("configHash", configHash(baseline));
        summary.put("searchStateHash", searchStateHash(baseline, baselineSnapshot));
        summary.put("mappingHash", mappingHash(baseline));
        summary.put("caseRevisionRefHash", caseRevisionRefHash(baseline));
        summary.put("runKind", runKind(baseline).name());
        summary.put("comparedAt", clock.instant().toString());
        return new EvalCompatibilityResult(status, reasons, summary);
    }

    private EvalRun run(String id) {
        return runRepository.findRun(id)
            .orElseThrow(() -> new ApplicationException(
                ErrorType.NOT_FOUND,
                "eval_run.not_found",
                "Eval run '" + id + "' does not exist"
            ));
    }

    private CorpusSnapshot snapshotForRun(EvalRun run, String role) {
        String snapshotId = snapshotId(run);
        if (isBlank(snapshotId)) {
            return null;
        }
        return snapshotRepository.findSnapshot(snapshotId).orElse(null);
    }

    private void addMissingPinReasons(List<EvalCompatibilityReason> reasons, String role, CorpusSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        Map<String, Object> pins = revisionPins(snapshot);
        List<String> missingPins = corpusReader.findMissingRevisionPins(pins);
        if (!missingPins.isEmpty()) {
            block(
                reasons,
                role + "_revision_pins_missing",
                "Snapshot revision pins are missing or deleted",
                Map.of("snapshotId", snapshot.id(), "missingPins", missingPins)
            );
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> revisionPins(CorpusSnapshot snapshot) {
        Object value = snapshot.manifest().get("revisionPins");
        if (value instanceof Map<?, ?> rawMap) {
            return (Map<String, Object>) rawMap;
        }
        return Map.of();
    }

    private void blockRequiredField(
        List<EvalCompatibilityReason> reasons,
        String field,
        String label,
        Object baseline,
        Object candidate
    ) {
        boolean baselineMissing = isMissing(baseline);
        boolean candidateMissing = isMissing(candidate);
        if (baselineMissing) {
            block(
                reasons,
                "baseline_" + field + "_missing",
                "Baseline " + label + " is missing",
                Map.of("field", field)
            );
        }
        if (candidateMissing) {
            block(
                reasons,
                "candidate_" + field + "_missing",
                "Candidate " + label + " is missing",
                Map.of("field", field)
            );
        }
        if (!baselineMissing && !candidateMissing && !Objects.equals(baseline, candidate)) {
            block(reasons, field + "_mismatch", label + " differs", details(baseline, candidate));
        }
    }

    private void block(
        List<EvalCompatibilityReason> reasons,
        String code,
        String message,
        Map<String, Object> details
    ) {
        reasons.add(new EvalCompatibilityReason(code, message, EvalCompatibilityStatus.BLOCKED, details));
    }

    private void warn(
        List<EvalCompatibilityReason> reasons,
        String code,
        String message,
        Map<String, Object> details
    ) {
        reasons.add(new EvalCompatibilityReason(code, message, EvalCompatibilityStatus.WARNING_ONLY, details));
    }

    private Map<String, Object> details(Object baseline, Object candidate) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("baseline", baseline);
        details.put("candidate", candidate);
        return details;
    }

    private EvalCompatibilityStatus status(List<EvalCompatibilityReason> reasons) {
        boolean blocked = reasons.stream().anyMatch(reason -> reason.status() == EvalCompatibilityStatus.BLOCKED);
        if (blocked) {
            return EvalCompatibilityStatus.BLOCKED;
        }
        return reasons.isEmpty() ? EvalCompatibilityStatus.COMPATIBLE : EvalCompatibilityStatus.WARNING_ONLY;
    }

    private String snapshotId(EvalRun run) {
        if (!isBlank(run.snapshotId())) {
            return run.snapshotId();
        }
        return run.executionConfig().corpusSnapshotId();
    }

    private String datasetVersion(EvalRun run) {
        if (!isBlank(run.executionConfig().datasetVersion())) {
            return run.executionConfig().datasetVersion();
        }
        return text(run.summary().get("datasetVersion"));
    }

    private String configHash(EvalRun run) {
        if (!isBlank(run.configHash())) {
            return run.configHash();
        }
        return run.executionConfig().configHash();
    }

    private String gitCommitSha(EvalRun run) {
        EvalExecutionConfig config = run.executionConfig();
        return config == null ? null : config.gitCommitSha();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isMissing(Object value) {
        return value == null || value instanceof String text && text.isBlank();
    }

    private String datasetId(EvalRun run) {
        if (!isBlank(run.datasetId())) {
            return run.datasetId();
        }
        return run.executionConfig().datasetId();
    }

    private EvalRunKind runKind(EvalRun run) {
        return run.runKind() == null ? EvalRunKind.E2E : run.runKind();
    }

    private String searchStateHash(EvalRun run, CorpusSnapshot snapshot) {
        if (snapshot != null && !isBlank(snapshot.searchStateHash())) {
            return snapshot.searchStateHash();
        }
        if (!isBlank(run.executionConfig().searchStateHash())) {
            return run.executionConfig().searchStateHash();
        }
        return text(run.summary().get("searchStateHash"));
    }

    private String caseRevisionRefHash(EvalRun run) {
        return text(run.summary().get("caseRevisionRefHash"));
    }

    private String mappingHash(EvalRun run) {
        String fromSummary = text(run.summary().get("mappingHash"));
        if (!isBlank(fromSummary)) {
            return fromSummary;
        }
        String fromSearchSync = text(nested(run.executionConfig().options(), "searchSync", "mappingHash"));
        if (!isBlank(fromSearchSync)) {
            return fromSearchSync;
        }
        String fromOptions = text(run.executionConfig().options().get("mappingHash"));
        if (!isBlank(fromOptions)) {
            return fromOptions;
        }
        return text(run.executionConfig().rolloutFlags().get("mappingHash"));
    }

    private Object nested(Map<String, Object> root, String firstKey, String secondKey) {
        if (root == null) {
            return null;
        }
        Object first = root.get(firstKey);
        if (first instanceof Map<?, ?> map) {
            return map.get(secondKey);
        }
        return null;
    }

    private void addMappingHashCompatibilityReasons(List<EvalCompatibilityReason> reasons, EvalRun baseline, EvalRun candidate) {
        String baselineMappingHash = mappingHash(baseline);
        String candidateMappingHash = mappingHash(candidate);
        if (isBlank(baselineMappingHash) && isBlank(candidateMappingHash)) {
            return;
        }
        if (isBlank(baselineMappingHash) || isBlank(candidateMappingHash)) {
            block(
                reasons,
                "mapping_hash_missing",
                "Mapping hash is missing on one run",
                details(baselineMappingHash, candidateMappingHash)
            );
            return;
        }
        if (!Objects.equals(baselineMappingHash, candidateMappingHash)) {
            block(
                reasons,
                "mapping_hash_mismatch",
                "Mapping hash differs",
                details(baselineMappingHash, candidateMappingHash)
            );
        }
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
