package com.example.demo.service.eval;

import com.example.demo.error.ApplicationException;
import com.example.demo.error.ErrorType;
import com.example.demo.model.ChatExecutionRequest;
import com.example.demo.model.ChatRunSubmissionResponse;
import com.example.demo.model.eval.CreateE2EEvalRunRequest;
import com.example.demo.model.eval.EvalCase;
import com.example.demo.model.eval.EvalExecutionConfig;
import com.example.demo.model.eval.EvalFailureCode;
import com.example.demo.model.eval.EvalRun;
import com.example.demo.model.eval.EvalRunItem;
import com.example.demo.model.eval.EvalRunItemArtifact;
import com.example.demo.model.eval.EvalRunItemArtifactType;
import com.example.demo.model.eval.EvalRunItemStatus;
import com.example.demo.model.eval.EvalRunKind;
import com.example.demo.model.eval.EvalRunStatus;
import com.example.demo.service.ChatRunSubmissionCoordinator;
import com.example.demo.service.eval.EvalDatasetVersionCaseResolver.ResolvedCase;
import com.example.demo.service.eval.EvalDatasetVersionCaseResolver.ResolvedCases;
import com.example.demo.service.eval.EvalSnapshotConsistencyService.SnapshotConsistencyResult;
import com.example.demo.service.eval.port.EvalRunRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class EvalE2ERunService {

    private static final int DEFAULT_CASE_LIMIT = 50;
    private static final int HARD_CASE_LIMIT = 200;

    private final EvalDatasetVersionCaseResolver caseResolver;
    private final EvalRunRepository runRepository;
    private final EvalSnapshotConsistencyService snapshotConsistencyService;
    private final EvalChatRunRequestFactory requestFactory;
    private final ChatRunSubmissionCoordinator chatRunSubmissionCoordinator;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public EvalE2ERunService(
        EvalDatasetVersionCaseResolver caseResolver,
        EvalRunRepository runRepository,
        EvalSnapshotConsistencyService snapshotConsistencyService,
        EvalChatRunRequestFactory requestFactory,
        ChatRunSubmissionCoordinator chatRunSubmissionCoordinator,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this.caseResolver = caseResolver;
        this.runRepository = runRepository;
        this.snapshotConsistencyService = snapshotConsistencyService;
        this.requestFactory = requestFactory;
        this.chatRunSubmissionCoordinator = chatRunSubmissionCoordinator;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public EvalRun createRun(CreateE2EEvalRunRequest request) {
        if (request == null || !StringUtils.hasText(request.datasetId())) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
                "eval.e2e_run.dataset_required",
                "Field 'datasetId' is required"
            );
        }
        int caseLimit = caseLimit(request.limit());
        ResolvedCases resolvedCases = caseResolver.resolve(
            request.datasetId(),
            request.datasetVersion(),
            request.caseIds(),
            caseLimit,
            true
        );
        String datasetVersion = resolvedCases.datasetVersion().version();
        SnapshotConsistencyResult consistency = snapshotConsistencyService.validatePersistentRun(
            request.datasetId(),
            datasetVersion,
            request.corpusSnapshotId(),
            request.executionConfigHash(),
            request.referenceInstant()
        );
        if (resolvedCases.cases().isEmpty()) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
                "eval.e2e_run.no_cases",
                "No eval cases matched the E2E run request"
            );
        }

        Instant startedAt = clock.instant();
        String runId = UUID.randomUUID().toString();
        EvalExecutionConfig executionConfig = consistency.resolvedConfig().executionConfig();
        EvalRun run = runRepository.saveRun(new EvalRun(
            runId,
            request.datasetId(),
            request.corpusSnapshotId(),
            EvalRunKind.E2E,
            EvalRunStatus.RUNNING,
            executionConfig,
            consistency.resolvedConfig().configHash(),
            startedAt,
            null,
            baseSummary(resolvedCases, request.judgeMode().name(), "submitted"),
            List.of(),
            startedAt,
            startedAt
        ));

        List<EvalRunItem> items = new ArrayList<>();
        for (ResolvedCase resolvedCase : resolvedCases.cases()) {
            items.add(submitCase(run.id(), resolvedCase, request, datasetVersion));
        }
        return saveRunSummary(run, items, request.judgeMode().name(), resolvedCases);
    }

    private EvalRunItem submitCase(
        String runId,
        ResolvedCase resolvedCase,
        CreateE2EEvalRunRequest request,
        String datasetVersion
    ) {
        EvalCase evalCase = resolvedCase.caseSnapshot();
        Instant now = clock.instant();
        String itemId = UUID.randomUUID().toString();
        try {
            ChatExecutionRequest chatRequest = requestFactory.create(evalCase, request);
            ChatRunSubmissionResponse submittedRun = chatRunSubmissionCoordinator.submit(chatRequest);
            EvalRunItem item = runRepository.saveItem(new EvalRunItem(
                itemId,
                runId,
                evalCase.id(),
                evalCase.revision(),
                submittedRun.id(),
                null,
                EvalRunItemStatus.RUNNING,
                null,
                null,
                submittedArtifact(resolvedCase, datasetVersion, submittedRun),
                Map.of(),
                Map.of(),
                now,
                now
            ));
            saveArtifact(item.id(), EvalRunItemArtifactType.PROMPT_SNAPSHOT, Map.of(
                "evalTemporaryInstruction", EvalChatRunRequestFactory.STRUCTURED_OUTPUT_INSTRUCTION,
                "chatRequest", toMap(chatRequest)
            ));
            return item;
        } catch (RuntimeException exception) {
            return runRepository.saveItem(new EvalRunItem(
                itemId,
                runId,
                evalCase.id(),
                evalCase.revision(),
                null,
                null,
                EvalRunItemStatus.ERROR,
                EvalFailureCode.CHAT_RUN_FAILED,
                exception.getMessage(),
                errorArtifact(resolvedCase, datasetVersion, exception),
                Map.of(),
                Map.of(),
                now,
                now
            ));
        }
    }

    private EvalRun saveRunSummary(EvalRun run, List<EvalRunItem> items, String judgeMode, ResolvedCases resolvedCases) {
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
            summary(items, judgeMode, resolvedCases),
            items,
            run.createdAt(),
            completedAt == null ? clock.instant() : completedAt
        ));
    }

    private Map<String, Object> baseSummary(ResolvedCases resolvedCases, String judgeMode, String phase) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("runKind", EvalRunKind.E2E.name());
        summary.put("judgeMode", judgeMode);
        if (phase != null) {
            summary.put("phase", phase);
        }
        summary.put("datasetVersion", resolvedCases.datasetVersion().version());
        summary.put("datasetVersionId", resolvedCases.datasetVersion().id());
        summary.put("caseRevisionRefHash", resolvedCases.caseRevisionRefHash());
        summary.put("selectedCaseCount", resolvedCases.cases().size());
        return summary;
    }

    private Map<String, Object> summary(List<EvalRunItem> items, String judgeMode, ResolvedCases resolvedCases) {
        Map<String, Object> summary = baseSummary(resolvedCases, judgeMode, null);
        summary.put("totalItems", items.size());
        summary.put("openItems", items.stream().filter(item -> item.status() == EvalRunItemStatus.RUNNING || item.status() == EvalRunItemStatus.PENDING).count());
        summary.put("passedItems", items.stream().filter(item -> item.status() == EvalRunItemStatus.PASSED).count());
        summary.put("failedItems", items.stream().filter(item -> item.status() == EvalRunItemStatus.FAILED).count());
        summary.put("errorItems", items.stream().filter(item -> item.status() == EvalRunItemStatus.ERROR).count());
        summary.put("linkedChatRunItems", items.stream().filter(item -> StringUtils.hasText(item.chatRunId())).count());
        return summary;
    }

    private Map<String, Object> submittedArtifact(
        ResolvedCase resolvedCase,
        String datasetVersion,
        ChatRunSubmissionResponse submittedRun
    ) {
        Map<String, Object> artifact = itemMetadata(resolvedCase, datasetVersion);
        artifact.put("submittedStatus", submittedRun.status());
        artifact.put("statusUrl", submittedRun.statusUrl());
        artifact.put("resultUrl", submittedRun.resultUrl());
        return artifact;
    }

    private Map<String, Object> itemMetadata(ResolvedCase resolvedCase, String datasetVersion) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("caseKey", resolvedCase.caseSnapshot().caseKey());
        metadata.put("caseRevision", resolvedCase.caseSnapshot().revision());
        metadata.put("caseContentHash", resolvedCase.contentHash());
        metadata.put("datasetVersion", datasetVersion);
        metadata.put("caseSnapshotSource", "CASE_REVISION");
        return metadata;
    }

    private Map<String, Object> errorArtifact(
        ResolvedCase resolvedCase,
        String datasetVersion,
        RuntimeException exception
    ) {
        Map<String, Object> artifact = itemMetadata(resolvedCase, datasetVersion);
        artifact.put("error", exception.getClass().getSimpleName());
        artifact.put("message", exception.getMessage() == null ? "" : exception.getMessage());
        return artifact;
    }

    private int caseLimit(Integer requestedLimit) {
        if (requestedLimit == null) {
            return DEFAULT_CASE_LIMIT;
        }
        if (requestedLimit < 1 || requestedLimit > HARD_CASE_LIMIT) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
                "eval.e2e_run.invalid_limit",
                "Field 'limit' must be between 1 and " + HARD_CASE_LIMIT
            );
        }
        return requestedLimit;
    }

    private void saveArtifact(String itemId, EvalRunItemArtifactType artifactType, Map<String, Object> payload) {
        Instant now = clock.instant();
        runRepository.saveArtifact(new EvalRunItemArtifact(itemId, artifactType, payload, now, now));
    }

    private Map<String, Object> toMap(Object value) {
        return objectMapper.convertValue(value, new TypeReference<>() {
        });
    }
}
