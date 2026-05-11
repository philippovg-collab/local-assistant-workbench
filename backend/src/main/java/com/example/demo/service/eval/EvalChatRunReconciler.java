package com.example.demo.service.eval;

import com.example.demo.config.EvalProperties;
import com.example.demo.error.ApplicationException;
import com.example.demo.error.ErrorType;
import com.example.demo.model.ChatExecutionResponse;
import com.example.demo.model.ChatRunContextDetail;
import com.example.demo.model.ChatRunOutputTrace;
import com.example.demo.model.ChatRunStatusResponse;
import com.example.demo.model.ChatRunTraceDetail;
import com.example.demo.model.eval.EvalCase;
import com.example.demo.model.eval.EvalCaseRevision;
import com.example.demo.model.eval.EvalE2EScoreSummary;
import com.example.demo.model.eval.EvalFailureCode;
import com.example.demo.model.eval.EvalJudgeMode;
import com.example.demo.model.eval.EvalRun;
import com.example.demo.model.eval.EvalRunItem;
import com.example.demo.model.eval.EvalRunItemArtifact;
import com.example.demo.model.eval.EvalRunItemArtifactType;
import com.example.demo.model.eval.EvalRunItemStatus;
import com.example.demo.model.eval.EvalRunKind;
import com.example.demo.model.eval.EvalRunStatus;
import com.example.demo.model.eval.EvalStructuredAnswer;
import com.example.demo.service.ChatRunQueryService;
import com.example.demo.service.context.ContextAssemblyQueryService;
import com.example.demo.service.eval.port.EvalDatasetRepository;
import com.example.demo.service.eval.port.EvalRunRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class EvalChatRunReconciler {

    private final EvalRunRepository runRepository;
    private final EvalDatasetRepository datasetRepository;
    private final ChatRunQueryService chatRunQueryService;
    private final ObjectProvider<ContextAssemblyQueryService> contextAssemblyQueryServiceProvider;
    private final EvalStructuredOutputParser outputParser;
    private final EvalCitationResolver citationResolver;
    private final EvalE2EScoringService scoringService;
    private final EvalCaseRevisionMapper caseRevisionMapper;
    private final EvalProperties evalProperties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public EvalChatRunReconciler(
        EvalRunRepository runRepository,
        EvalDatasetRepository datasetRepository,
        ChatRunQueryService chatRunQueryService,
        ObjectProvider<ContextAssemblyQueryService> contextAssemblyQueryServiceProvider,
        EvalStructuredOutputParser outputParser,
        EvalCitationResolver citationResolver,
        EvalE2EScoringService scoringService,
        EvalCaseRevisionMapper caseRevisionMapper,
        EvalProperties evalProperties,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this.runRepository = runRepository;
        this.datasetRepository = datasetRepository;
        this.chatRunQueryService = chatRunQueryService;
        this.contextAssemblyQueryServiceProvider = contextAssemblyQueryServiceProvider;
        this.outputParser = outputParser;
        this.citationResolver = citationResolver;
        this.scoringService = scoringService;
        this.caseRevisionMapper = caseRevisionMapper;
        this.evalProperties = evalProperties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public EvalRun reconcileRun(String runId) {
        EvalRun run = requireE2ERun(runId);
        for (EvalRunItem item : runRepository.findOpenE2EItemsByRunId(run.id())) {
            reconcileItem(run, item);
        }
        return refreshRun(run.id());
    }

    @Transactional
    public void reconcileOpenItems() {
        for (EvalRunItem item : runRepository.findOpenE2EItems()) {
            EvalRun run = runRepository.findRun(item.runId()).orElse(null);
            if (run != null && run.runKind() == EvalRunKind.E2E) {
                reconcileItem(run, item);
                refreshRun(run.id());
            }
        }
    }

    private void reconcileItem(EvalRun run, EvalRunItem item) {
        if (!StringUtils.hasText(item.chatRunId())) {
            return;
        }
        ChatRunStatusResponse status = chatRunQueryService.getStatus(item.chatRunId());
        if (isTimedOut(item)) {
            markTerminal(item, EvalRunItemStatus.ERROR, EvalFailureCode.CHAT_RUN_TIMEOUT, "Chat run timed out before completion", Map.of());
            return;
        }
        switch (status.status()) {
            case "COMPLETED" -> completeItem(run, item);
            case "FAILED" -> markTerminal(
                item,
                EvalRunItemStatus.ERROR,
                EvalFailureCode.CHAT_RUN_FAILED,
                status.failureMessage(),
                Map.of("failureStage", valueOrEmpty(status.failureStage()), "failureCode", valueOrEmpty(status.failureCode()))
            );
            case "CANCELLED" -> markTerminal(
                item,
                EvalRunItemStatus.ERROR,
                EvalFailureCode.CHAT_RUN_CANCELLED,
                status.failureMessage(),
                Map.of()
            );
            default -> {
            }
        }
    }

    private void completeItem(EvalRun run, EvalRunItem item) {
        EvalCaseRevision caseRevision = pinnedCaseRevision(run, item);
        if (caseRevision == null) {
            saveArtifact(item.id(), EvalRunItemArtifactType.CASE_SNAPSHOT, missingCaseSnapshot(run, item));
            markTerminal(
                item,
                EvalRunItemStatus.ERROR,
                EvalFailureCode.PINNED_CASE_REVISION_MISSING,
                "Pinned eval case revision is missing for case '" + item.caseId() + "' revision '" + item.caseRevision() + "'",
                Map.of()
            );
            return;
        }
        EvalCase evalCase = caseRevisionMapper.toEvalCase(caseRevision);
        saveArtifact(item.id(), EvalRunItemArtifactType.CASE_SNAPSHOT, caseSnapshot(run, caseRevision));
        ChatExecutionResponse result = chatRunQueryService.getResult(item.chatRunId());
        ChatRunTraceDetail trace = chatRunQueryService.getTrace(item.chatRunId());
        persistBaseArtifacts(item, result, trace);
        String rawOutput = rawOutput(result, trace);
        saveArtifact(item.id(), EvalRunItemArtifactType.RAW_OUTPUT, Map.of("rawOutput", rawOutput));
        EvalStructuredAnswer parsed;
        try {
            parsed = outputParser.parse(rawOutput);
        } catch (EvalOutputFormatException exception) {
            markTerminal(item, EvalRunItemStatus.FAILED, EvalFailureCode.OUTPUT_FORMAT_ERROR, exception.getMessage(), Map.of(
                "output_format_validity", 0.0d
            ));
            return;
        }
        EvalStructuredAnswer resolved;
        try {
            resolved = citationResolver.resolve(parsed, result.sources());
        } catch (EvalCitationResolutionException exception) {
            saveArtifact(item.id(), EvalRunItemArtifactType.STRUCTURED_OUTPUT, toMap(parsed));
            markTerminal(item, EvalRunItemStatus.FAILED, EvalFailureCode.CITATION_RESOLUTION_ERROR, exception.getMessage(), Map.of(
                "output_format_validity", 1.0d,
                "citation_resolution_validity", 0.0d
            ));
            return;
        }
        EvalE2EScoreSummary scoreSummary;
        try {
            scoreSummary = scoringService.score(evalCase, resolved);
        } catch (RuntimeException exception) {
            markTerminal(item, EvalRunItemStatus.ERROR, EvalFailureCode.SCORER_ERROR, exception.getMessage(), Map.of());
            return;
        }
        saveArtifact(item.id(), EvalRunItemArtifactType.STRUCTURED_OUTPUT, toMap(resolved));
        saveArtifact(item.id(), EvalRunItemArtifactType.ANSWER_CLAIMS, Map.of("claims", toJsonValue(resolved.claims())));
        saveArtifact(item.id(), EvalRunItemArtifactType.SCORER_OUTPUT, toMap(scoreSummary));
        saveArtifact(item.id(), EvalRunItemArtifactType.JUDGE_OUTPUT, judgeOutput(run));
        Instant now = clock.instant();
        runRepository.saveItem(new EvalRunItem(
            item.id(),
            item.runId(),
            item.caseId(),
            item.caseRevision(),
            item.chatRunId(),
            result.contextAssemblyId(),
            scoreSummary.passed() ? EvalRunItemStatus.PASSED : EvalRunItemStatus.FAILED,
            null,
            null,
            Map.of("rawOutputCaptured", true, "structuredOutputCaptured", true),
            toMap(scoreSummary),
            toMap(scoreSummary),
            item.createdAt(),
            now
        ));
    }

    private void persistBaseArtifacts(EvalRunItem item, ChatExecutionResponse result, ChatRunTraceDetail trace) {
        saveArtifact(item.id(), EvalRunItemArtifactType.CHAT_RUN_RESULT, toMap(result));
        saveArtifact(item.id(), EvalRunItemArtifactType.CHAT_RUN_TRACE, toMap(trace));
        if (trace.promptSnapshot() != null) {
            saveArtifact(item.id(), EvalRunItemArtifactType.PROMPT_SNAPSHOT, toMap(trace.promptSnapshot()));
        }
        if (trace.retrievalSummary() != null) {
            saveArtifact(item.id(), EvalRunItemArtifactType.RETRIEVAL_SUMMARY, toMap(trace.retrievalSummary()));
        }
        saveContextArtifact(item);
    }

    private void saveContextArtifact(EvalRunItem item) {
        ContextAssemblyQueryService contextService = contextAssemblyQueryServiceProvider.getIfAvailable();
        if (contextService == null) {
            saveArtifact(item.id(), EvalRunItemArtifactType.CONTEXT_ASSEMBLY, Map.of("status", "UNAVAILABLE"));
            return;
        }
        try {
            ChatRunContextDetail context = contextService.getByRunId(item.chatRunId());
            saveArtifact(item.id(), EvalRunItemArtifactType.CONTEXT_ASSEMBLY, toMap(context));
        } catch (RuntimeException exception) {
            saveArtifact(item.id(), EvalRunItemArtifactType.CONTEXT_ASSEMBLY, Map.of(
                "status", "UNAVAILABLE",
                "reason", exception.getClass().getSimpleName()
            ));
        }
    }

    private Map<String, Object> judgeOutput(EvalRun run) {
        Object judgeMode = run.summary().get("judgeMode");
        String mode = judgeMode == null ? EvalJudgeMode.DETERMINISTIC_ONLY.name() : String.valueOf(judgeMode);
        if (EvalJudgeMode.LLM_JUDGE.name().equals(mode)) {
            return Map.of("status", "SKIPPED", "judgeMode", mode, "reason", "LLM judge execution is not configured for durable E2E eval runs yet");
        }
        return Map.of("status", "SKIPPED", "judgeMode", mode);
    }

    private String rawOutput(ChatExecutionResponse result, ChatRunTraceDetail trace) {
        ChatRunOutputTrace output = trace.output();
        if (output != null && StringUtils.hasText(output.rawModelAnswer())) {
            return output.rawModelAnswer();
        }
        return result.answer() == null ? "" : result.answer();
    }

    private EvalCaseRevision pinnedCaseRevision(EvalRun run, EvalRunItem item) {
        if (!StringUtils.hasText(item.caseId()) || item.caseRevision() == null) {
            return null;
        }
        return datasetRepository.findCaseRevision(item.caseId(), item.caseRevision())
            .filter(revision -> matchesRunDataset(run, revision))
            .orElse(null);
    }

    private boolean matchesRunDataset(EvalRun run, EvalCaseRevision revision) {
        return revision.caseSnapshot() != null
            && run.datasetId().equals(revision.caseSnapshot().datasetId());
    }

    private Map<String, Object> caseSnapshot(EvalRun run, EvalCaseRevision revision) {
        EvalCase evalCase = caseRevisionMapper.toEvalCase(revision);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("caseId", evalCase.id());
        snapshot.put("caseKey", evalCase.caseKey());
        snapshot.put("revision", evalCase.revision());
        snapshot.put("contentHash", revision.contentHash());
        snapshot.put("reviewStatus", evalCase.reviewStatus().name());
        snapshot.put("datasetVersion", datasetVersion(run));
        snapshot.put("caseSnapshotSource", "CASE_REVISION");
        return snapshot;
    }

    private Map<String, Object> missingCaseSnapshot(EvalRun run, EvalRunItem item) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("status", "MISSING");
        snapshot.put("caseId", item.caseId());
        snapshot.put("revision", item.caseRevision());
        snapshot.put("datasetVersion", datasetVersion(run));
        snapshot.put("caseSnapshotSource", "CASE_REVISION");
        return snapshot;
    }

    private String datasetVersion(EvalRun run) {
        Object summaryVersion = run.summary().get("datasetVersion");
        if (summaryVersion != null && StringUtils.hasText(String.valueOf(summaryVersion))) {
            return String.valueOf(summaryVersion);
        }
        return run.executionConfig() == null ? null : run.executionConfig().datasetVersion();
    }

    private void markTerminal(
        EvalRunItem item,
        EvalRunItemStatus status,
        EvalFailureCode failureCode,
        String failureMessage,
        Map<String, Object> scoreSummary
    ) {
        Instant now = clock.instant();
        if (scoreSummary != null && !scoreSummary.isEmpty()) {
            saveArtifact(item.id(), EvalRunItemArtifactType.SCORER_OUTPUT, Map.of("metrics", scoreSummary));
        }
        runRepository.saveItem(new EvalRunItem(
            item.id(),
            item.runId(),
            item.caseId(),
            item.caseRevision(),
            item.chatRunId(),
            item.contextAssemblyId(),
            status,
            failureCode,
            failureMessage,
            item.artifact(),
            item.scorer(),
            scoreSummary,
            item.createdAt(),
            now
        ));
    }

    private EvalRun refreshRun(String runId) {
        EvalRun run = requireE2ERun(runId);
        List<EvalRunItem> items = runRepository.findItemsByRunId(run.id());
        long open = items.stream().filter(item -> item.status() == EvalRunItemStatus.RUNNING || item.status() == EvalRunItemStatus.PENDING).count();
        Instant completedAt = open == 0 ? clock.instant() : null;
        EvalRunStatus status = open == 0 ? EvalRunStatus.COMPLETED : EvalRunStatus.RUNNING;
        return runRepository.saveRun(new EvalRun(
            run.id(),
            run.datasetId(),
            run.snapshotId(),
            run.runKind(),
            status,
            run.executionConfig(),
            run.configHash(),
            run.startedAt(),
            completedAt,
            summary(items, run.summary().get("judgeMode")),
            items,
            run.createdAt(),
            clock.instant()
        ));
    }

    private Map<String, Object> summary(List<EvalRunItem> items, Object judgeMode) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("runKind", EvalRunKind.E2E.name());
        summary.put("judgeMode", judgeMode == null ? EvalJudgeMode.DETERMINISTIC_ONLY.name() : String.valueOf(judgeMode));
        summary.put("totalItems", items.size());
        summary.put("openItems", items.stream().filter(item -> item.status() == EvalRunItemStatus.RUNNING || item.status() == EvalRunItemStatus.PENDING).count());
        summary.put("passedItems", items.stream().filter(item -> item.status() == EvalRunItemStatus.PASSED).count());
        summary.put("failedItems", items.stream().filter(item -> item.status() == EvalRunItemStatus.FAILED).count());
        summary.put("errorItems", items.stream().filter(item -> item.status() == EvalRunItemStatus.ERROR).count());
        summary.put("completedItems", items.stream().filter(item ->
            item.status() == EvalRunItemStatus.PASSED
                || item.status() == EvalRunItemStatus.FAILED
                || item.status() == EvalRunItemStatus.ERROR
                || item.status() == EvalRunItemStatus.SKIPPED
        ).count());
        summary.put("notScorableItems", items.stream().filter(item -> hasNotScorableMetrics(item.scoreSummary())).count());
        summary.put("failureCodes", failureCounts(items));
        return summary;
    }

    private Map<String, Long> failureCounts(List<EvalRunItem> items) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (EvalRunItem item : items) {
            if (item.failureCode() != null) {
                counts.merge(item.failureCode().name(), 1L, Long::sum);
            }
        }
        return counts;
    }

    @SuppressWarnings("unchecked")
    private boolean hasNotScorableMetrics(Map<String, Object> scoreSummary) {
        Object details = scoreSummary.get("details");
        if (details instanceof Map<?, ?> detailsMap) {
            Object notScorable = detailsMap.get("notScorable");
            return notScorable instanceof List<?> list && !list.isEmpty();
        }
        Object notScorableMetricCount = scoreSummary.get("notScorableMetricCount");
        return notScorableMetricCount instanceof Number number && number.intValue() > 0;
    }

    private EvalRun requireE2ERun(String runId) {
        EvalRun run = runRepository.findRun(runId)
            .orElseThrow(() -> new ApplicationException(
                ErrorType.NOT_FOUND,
                "eval_run.not_found",
                "Eval run '" + runId + "' does not exist"
            ));
        if (run.runKind() != EvalRunKind.E2E) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
                "eval_run.kind_mismatch",
                "Eval run '" + runId + "' is not an E2E run"
            );
        }
        return run;
    }

    private boolean isTimedOut(EvalRunItem item) {
        long timeoutMillis = evalProperties.getChatRunTimeoutMs();
        return timeoutMillis > 0
            && item.createdAt() != null
            && Duration.between(item.createdAt(), clock.instant()).toMillis() > timeoutMillis;
    }

    private void saveArtifact(String itemId, EvalRunItemArtifactType artifactType, Map<String, Object> payload) {
        Instant now = clock.instant();
        runRepository.saveArtifact(new EvalRunItemArtifact(itemId, artifactType, payload, now, now));
    }

    private Map<String, Object> toMap(Object value) {
        return objectMapper.convertValue(value, new TypeReference<>() {
        });
    }

    private Object toJsonValue(Object value) {
        return objectMapper.convertValue(value, Object.class);
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
