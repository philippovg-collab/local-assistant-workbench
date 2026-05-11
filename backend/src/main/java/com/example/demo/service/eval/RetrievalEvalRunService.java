package com.example.demo.service.eval;

import com.example.demo.error.ApplicationException;
import com.example.demo.error.ErrorType;
import com.example.demo.model.eval.CreateRetrievalEvalRunRequest;
import com.example.demo.model.eval.EvalCase;
import com.example.demo.model.eval.EvalExecutionConfig;
import com.example.demo.model.eval.EvalFailureCode;
import com.example.demo.model.eval.EvalRun;
import com.example.demo.model.eval.EvalRunItem;
import com.example.demo.model.eval.EvalRunItemStatus;
import com.example.demo.model.eval.EvalRunKind;
import com.example.demo.model.eval.EvalRunStatus;
import com.example.demo.model.eval.RetrievalEvalMetric;
import com.example.demo.model.eval.RetrievalEvalMetricStatus;
import com.example.demo.model.eval.RetrievalEvalPreviewRequest;
import com.example.demo.model.eval.RetrievalEvalPreviewResponse;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class RetrievalEvalRunService {

    private static final int DEFAULT_CASE_LIMIT = 50;
    private static final int HARD_CASE_LIMIT = 200;

    private final EvalDatasetVersionCaseResolver caseResolver;
    private final EvalRunRepository runRepository;
    private final EvalSnapshotConsistencyService snapshotConsistencyService;
    private final RetrievalEvalPreviewService previewService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public RetrievalEvalRunService(
        EvalDatasetVersionCaseResolver caseResolver,
        EvalRunRepository runRepository,
        EvalSnapshotConsistencyService snapshotConsistencyService,
        RetrievalEvalPreviewService previewService,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this.caseResolver = caseResolver;
        this.runRepository = runRepository;
        this.snapshotConsistencyService = snapshotConsistencyService;
        this.previewService = previewService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public EvalRun createRun(CreateRetrievalEvalRunRequest request) {
        CreateRetrievalEvalRunRequest safeRequest = request == null
            ? new CreateRetrievalEvalRunRequest(null, null, List.of(), null, null, null, null, List.of())
            : request;
        if (!StringUtils.hasText(safeRequest.datasetId())) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
                "eval.retrieval_run.dataset_required",
                "Field 'datasetId' is required"
            );
        }
        int caseLimit = caseLimit(safeRequest.limit());
        ResolvedCases resolvedCases = caseResolver.resolve(
            safeRequest.datasetId(),
            safeRequest.datasetVersion(),
            safeRequest.caseIds(),
            caseLimit,
            true
        );
        String datasetVersion = resolvedCases.datasetVersion().version();
        SnapshotConsistencyResult consistency = snapshotConsistencyService.validatePersistentRun(
            safeRequest.datasetId(),
            datasetVersion,
            safeRequest.corpusSnapshotId(),
            safeRequest.executionConfigHash(),
            safeRequest.referenceInstant()
        );
        if (resolvedCases.cases().isEmpty()) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
                "eval.retrieval_run.no_cases",
                "No eval cases matched the retrieval run request"
            );
        }

        Instant startedAt = clock.instant();
        String runId = UUID.randomUUID().toString();
        EvalExecutionConfig executionConfig = consistency.resolvedConfig().executionConfig();
        EvalRun run = runRepository.saveRun(new EvalRun(
            runId,
            safeRequest.datasetId(),
            safeRequest.corpusSnapshotId(),
            EvalRunKind.RETRIEVAL_ONLY,
            EvalRunStatus.RUNNING,
            executionConfig,
            consistency.resolvedConfig().configHash(),
            startedAt,
            null,
            baseSummary(resolvedCases, safeRequest.tags()),
            List.of(),
            startedAt,
            startedAt
        ));

        List<EvalRunItem> items = new ArrayList<>();
        for (ResolvedCase resolvedCase : resolvedCases.cases()) {
            items.add(runRepository.saveItem(runItem(run.id(), resolvedCase, safeRequest, datasetVersion)));
        }
        Instant completedAt = clock.instant();
        EvalRun completed = runRepository.saveRun(new EvalRun(
            run.id(),
            run.datasetId(),
            run.snapshotId(),
            EvalRunKind.RETRIEVAL_ONLY,
            EvalRunStatus.COMPLETED,
            run.executionConfig(),
            run.configHash(),
            run.startedAt(),
            completedAt,
            summary(items, safeRequest.tags(), resolvedCases),
            items,
            run.createdAt(),
            completedAt
        ));
        return completed;
    }

    private EvalRunItem runItem(
        String runId,
        ResolvedCase resolvedCase,
        CreateRetrievalEvalRunRequest request,
        String datasetVersion
    ) {
        EvalCase evalCase = resolvedCase.caseSnapshot();
        Instant now = clock.instant();
        String itemId = UUID.randomUUID().toString();
        try {
            RetrievalEvalPreviewResponse preview = previewService.preview(new RetrievalEvalPreviewRequest(
                null,
                request.datasetId(),
                datasetVersion,
                evalCase.id(),
                evalCase.revision(),
                request.corpusSnapshotId(),
                request.executionConfigHash(),
                null,
                null,
                List.of(),
                request.referenceInstant(),
                null,
                true,
                false
            ), datasetVersion);
            EvalFailureCode failureCode = failureCode(preview);
            EvalRunItemStatus status = failureCode == null ? EvalRunItemStatus.PASSED : EvalRunItemStatus.FAILED;
            Map<String, Object> artifact = new LinkedHashMap<>(toMap(preview));
            artifact.putAll(itemMetadata(resolvedCase, datasetVersion));
            return new EvalRunItem(
                itemId,
                runId,
                evalCase.id(),
                evalCase.revision(),
                null,
                null,
                status,
                failureCode,
                null,
                artifact,
                Map.of("metrics", preview.metrics()),
                Map.of(),
                now,
                now
            );
        } catch (RuntimeException exception) {
            return new EvalRunItem(
                itemId,
                runId,
                evalCase.id(),
                evalCase.revision(),
                null,
                null,
                EvalRunItemStatus.ERROR,
                EvalFailureCode.RETRIEVAL_EXECUTION_ERROR,
                exception.getMessage(),
                errorArtifact(resolvedCase, datasetVersion, exception),
                Map.of(),
                Map.of(),
                now,
                now
            );
        }
    }

    private EvalFailureCode failureCode(RetrievalEvalPreviewResponse preview) {
        if (preview.stages().finalChunks().isEmpty()) {
            return EvalFailureCode.RETRIEVAL_NO_RESULTS;
        }
        boolean filterViolation = preview.metrics().stream()
            .anyMatch(metric -> "filter_adherence_retrieval".equals(metric.name())
                && metric.status() == RetrievalEvalMetricStatus.SCORED
                && metric.value() != null
                && metric.value() < 1.0d);
        return filterViolation ? EvalFailureCode.FILTER_VIOLATION : null;
    }

    private Map<String, Object> baseSummary(ResolvedCases resolvedCases, List<String> tags) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("runKind", EvalRunKind.RETRIEVAL_ONLY.name());
        summary.put("tags", tags == null ? List.of() : tags);
        summary.put("datasetVersion", resolvedCases.datasetVersion().version());
        summary.put("datasetVersionId", resolvedCases.datasetVersion().id());
        summary.put("caseRevisionRefHash", resolvedCases.caseRevisionRefHash());
        summary.put("selectedCaseCount", resolvedCases.cases().size());
        return summary;
    }

    private Map<String, Object> summary(List<EvalRunItem> items, List<String> tags, ResolvedCases resolvedCases) {
        Map<String, Object> summary = baseSummary(resolvedCases, tags);
        summary.put("runKind", EvalRunKind.RETRIEVAL_ONLY.name());
        summary.put("totalItems", items.size());
        summary.put("passedItems", items.stream().filter(item -> item.status() == EvalRunItemStatus.PASSED).count());
        summary.put("failedItems", items.stream().filter(item -> item.status() == EvalRunItemStatus.FAILED).count());
        summary.put("errorItems", items.stream().filter(item -> item.status() == EvalRunItemStatus.ERROR).count());
        summary.put("metrics", metricAverages(items));
        return summary;
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
        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("query", resolvedCase.caseSnapshot().question());
        artifact.put("error", exception.getClass().getSimpleName());
        artifact.put("message", exception.getMessage() == null ? "" : exception.getMessage());
        artifact.putAll(itemMetadata(resolvedCase, datasetVersion));
        return artifact;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> metricAverages(List<EvalRunItem> items) {
        Map<String, List<Double>> valuesByMetric = new LinkedHashMap<>();
        for (EvalRunItem item : items) {
            Object metrics = item.scorer().get("metrics");
            if (!(metrics instanceof List<?> list)) {
                continue;
            }
            for (Object rawMetric : list) {
                RetrievalEvalMetric metric = rawMetric instanceof RetrievalEvalMetric typed
                    ? typed
                    : objectMapper.convertValue(rawMetric, RetrievalEvalMetric.class);
                if (metric.status() == RetrievalEvalMetricStatus.SCORED && metric.value() != null) {
                    valuesByMetric.computeIfAbsent(metric.name(), ignored -> new ArrayList<>()).add(metric.value());
                }
            }
        }
        Map<String, Object> averages = new LinkedHashMap<>();
        valuesByMetric.forEach((name, values) -> averages.put(
            name,
            Map.of(
                "average", values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0d),
                "count", values.size()
            )
        ));
        return averages;
    }

    private int caseLimit(Integer requestedLimit) {
        if (requestedLimit == null) {
            return DEFAULT_CASE_LIMIT;
        }
        if (requestedLimit < 1 || requestedLimit > HARD_CASE_LIMIT) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
                "eval.retrieval_run.invalid_limit",
                "Field 'limit' must be between 1 and " + HARD_CASE_LIMIT
            );
        }
        return requestedLimit;
    }

    private Map<String, Object> toMap(Object value) {
        return objectMapper.convertValue(value, new TypeReference<>() {
        });
    }
}
