package com.example.demo.controller.eval;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.api.ApiExceptionHandler;
import com.example.demo.config.MaterialProperties;
import com.example.demo.error.ApplicationException;
import com.example.demo.error.ErrorType;
import com.example.demo.model.eval.CorpusSnapshot;
import com.example.demo.model.eval.CorpusSnapshotDetail;
import com.example.demo.model.eval.CorpusSnapshotItemDetail;
import com.example.demo.model.eval.CreateCorpusSnapshotRequest;
import com.example.demo.model.eval.CreateE2EEvalRunRequest;
import com.example.demo.model.eval.CreateRetrievalEvalRunRequest;
import com.example.demo.model.eval.EvalCase;
import com.example.demo.model.eval.EvalCaseOrigin;
import com.example.demo.model.eval.EvalCaseReview;
import com.example.demo.model.eval.EvalCaseType;
import com.example.demo.model.eval.EvalCompareRequest;
import com.example.demo.model.eval.EvalCompareStatus;
import com.example.demo.model.eval.EvalCompatibilityReason;
import com.example.demo.model.eval.EvalCompatibilityStatus;
import com.example.demo.model.eval.EvalDataset;
import com.example.demo.model.eval.EvalDatasetDetail;
import com.example.demo.model.eval.EvalDatasetKind;
import com.example.demo.model.eval.EvalDatasetSummary;
import com.example.demo.model.eval.EvalExecutionConfig;
import com.example.demo.model.eval.EvalExpectedMode;
import com.example.demo.model.eval.EvalLifecycleStatus;
import com.example.demo.model.eval.EvalModuleStatusResponse;
import com.example.demo.model.eval.EvalReviewStatus;
import com.example.demo.model.eval.EvalRun;
import com.example.demo.model.eval.EvalRunCompare;
import com.example.demo.model.eval.EvalRunItemArtifact;
import com.example.demo.model.eval.EvalRunItemArtifactType;
import com.example.demo.model.eval.EvalRunKind;
import com.example.demo.model.eval.EvalRunStatus;
import com.example.demo.model.eval.EvalSeverity;
import com.example.demo.model.eval.EvalRuntimeStateSnapshot;
import com.example.demo.model.eval.RetrievalEvalMetric;
import com.example.demo.model.eval.RetrievalEvalMetricStatus;
import com.example.demo.model.eval.RetrievalEvalPreviewRequest;
import com.example.demo.model.eval.RetrievalEvalPreviewResponse;
import com.example.demo.model.eval.RetrievalEvalReproducibilityStatus;
import com.example.demo.model.eval.RetrievalEvalStageTrace;
import com.example.demo.model.eval.RetrievalEvalTrace;
import com.example.demo.model.eval.ResolveEvalExecutionConfigRequest;
import com.example.demo.model.eval.ResolvedEvalExecutionConfig;
import com.example.demo.service.eval.CorpusSnapshotCatalogService;
import com.example.demo.service.eval.CorpusSnapshotService;
import com.example.demo.service.eval.EvalComparisonService;
import com.example.demo.service.eval.EvalCatalogService;
import com.example.demo.service.eval.EvalChatRunReconciler;
import com.example.demo.service.eval.EvalDatasetLifecycleService;
import com.example.demo.service.eval.EvalE2ERunService;
import com.example.demo.service.eval.EvalExecutionConfigService;
import com.example.demo.service.eval.EvalModuleStatusService;
import com.example.demo.service.eval.EvalRunCatalogService;
import com.example.demo.service.eval.RetrievalEvalPreviewService;
import com.example.demo.service.eval.RetrievalEvalRunService;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EvalControllerContractTest {

    private MockMvc mockMvc;
    private EvalModuleStatusService statusService;
    private EvalCatalogService catalogService;
    private EvalDatasetLifecycleService lifecycleService;
    private EvalRunCatalogService runCatalogService;
    private CorpusSnapshotCatalogService snapshotCatalogService;
    private CorpusSnapshotService snapshotService;
    private EvalExecutionConfigService executionConfigService;
    private EvalComparisonService comparisonService;
    private RetrievalEvalPreviewService retrievalPreviewService;
    private RetrievalEvalRunService retrievalRunService;
    private EvalE2ERunService e2eRunService;
    private EvalChatRunReconciler e2eReconciler;

    @BeforeEach
    void setUp() {
        statusService = mock(EvalModuleStatusService.class);
        catalogService = mock(EvalCatalogService.class);
        lifecycleService = mock(EvalDatasetLifecycleService.class);
        runCatalogService = mock(EvalRunCatalogService.class);
        snapshotCatalogService = mock(CorpusSnapshotCatalogService.class);
        snapshotService = mock(CorpusSnapshotService.class);
        executionConfigService = mock(EvalExecutionConfigService.class);
        comparisonService = mock(EvalComparisonService.class);
        retrievalPreviewService = mock(RetrievalEvalPreviewService.class);
        retrievalRunService = mock(RetrievalEvalRunService.class);
        e2eRunService = mock(EvalE2ERunService.class);
        e2eReconciler = mock(EvalChatRunReconciler.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new EvalController(
                statusService,
                catalogService,
                lifecycleService,
                runCatalogService,
                snapshotCatalogService,
                snapshotService,
                executionConfigService,
                comparisonService,
                retrievalPreviewService,
                retrievalRunService,
                e2eRunService,
                e2eReconciler
            ))
            .setControllerAdvice(new ApiExceptionHandler(new MaterialProperties()))
            .setMessageConverters(new MappingJackson2HttpMessageConverter(JsonMapper.builder()
                .findAndAddModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build()))
            .build();
    }

    @Test
    void returnsEvalModuleStatus() throws Exception {
        when(statusService.getStatus()).thenReturn(new EvalModuleStatusResponse(
            true,
            true,
            true,
            List.of("READ_STATUS", "LIST_DATASETS"),
            List.of("GET /api/evals/status"),
            Instant.parse("2026-05-10T00:00:00Z")
        ));

        mockMvc.perform(get("/api/evals/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.storageReady").value(true))
            .andExpect(jsonPath("$.schemaReady").value(true))
            .andExpect(jsonPath("$.readOnly").value(true))
            .andExpect(jsonPath("$.capabilities[0]").value("READ_STATUS"))
            .andExpect(jsonPath("$.endpoints[0]").value("GET /api/evals/status"));

        verify(statusService).getStatus();
    }

    @Test
    void listEndpointsReturnEmptyArrays() throws Exception {
        when(catalogService.listDatasets()).thenReturn(List.of());
        when(runCatalogService.listRuns()).thenReturn(List.of());
        when(snapshotCatalogService.listSnapshots()).thenReturn(List.of());

        mockMvc.perform(get("/api/evals/datasets"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/api/evals/runs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/api/evals/snapshots"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());

        verify(catalogService).listDatasets();
        verify(runCatalogService).listRuns();
        verify(snapshotCatalogService).listSnapshots();
    }

    @Test
    void returnsDatasetDetailWithCasesAndReviews() throws Exception {
        String datasetId = UUID.randomUUID().toString();
        String caseId = UUID.randomUUID().toString();
        when(catalogService.getDataset(datasetId)).thenReturn(new EvalDatasetDetail(
            dataset(datasetId),
            List.of(evalCase(datasetId, caseId)),
            List.of(new EvalCaseReview(
                UUID.randomUUID().toString(),
                caseId,
                EvalReviewStatus.APPROVED,
                "reviewer",
                "looks good",
                Instant.parse("2026-05-10T00:00:00Z"),
                Instant.parse("2026-05-10T00:00:00Z")
            ))
        ));

        mockMvc.perform(get("/api/evals/datasets/{id}", datasetId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dataset.id").value(datasetId))
            .andExpect(jsonPath("$.dataset.kind").value("GOLDEN"))
            .andExpect(jsonPath("$.cases[0].caseKey").value("case-1"))
            .andExpect(jsonPath("$.cases[0].origin.sourceType").value("manual"))
            .andExpect(jsonPath("$.reviews[0].status").value("APPROVED"));

        verify(catalogService).getDataset(datasetId);
    }

    @Test
    void returnsRunsAndSnapshotsThroughCatalogServices() throws Exception {
        String runId = UUID.randomUUID().toString();
        String snapshotId = UUID.randomUUID().toString();
        when(runCatalogService.getRun(runId)).thenReturn(new EvalRun(
            runId,
            UUID.randomUUID().toString(),
            snapshotId,
            EvalRunStatus.COMPLETED,
            null,
            "cfg",
            Instant.parse("2026-05-10T00:00:00Z"),
            Instant.parse("2026-05-10T00:00:01Z"),
            Map.of("passed", 1),
            List.of(),
            Instant.parse("2026-05-10T00:00:00Z"),
            Instant.parse("2026-05-10T00:00:01Z")
        ));
        when(snapshotCatalogService.getSnapshotDetail(snapshotId)).thenReturn(new CorpusSnapshotDetail(new CorpusSnapshot(
            snapshotId,
            "snapshot-1",
            EvalLifecycleStatus.ACTIVE,
            Instant.parse("2026-05-10T00:00:00Z"),
            "materials",
            "search",
            "config",
            Map.of("referenceInstant", "2026-05-10T00:00:00Z"),
            1,
            1,
            0,
            1,
            Map.of("source", "test"),
            List.of(),
            Instant.parse("2026-05-10T00:00:00Z"),
            Instant.parse("2026-05-10T00:00:00Z")
        ), Map.of("referenceInstant", "2026-05-10T00:00:00Z"), Map.of("materialSetHash", "materials")));

        mockMvc.perform(get("/api/evals/runs/{id}", runId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(runId))
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.summary.passed").value(1));
        mockMvc.perform(get("/api/evals/snapshots/{id}", snapshotId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.snapshot.id").value(snapshotId))
            .andExpect(jsonPath("$.snapshot.snapshotKey").value("snapshot-1"))
            .andExpect(jsonPath("$.summary.materialSetHash").value("materials"));

        verify(runCatalogService).getRun(runId);
        verify(snapshotCatalogService).getSnapshotDetail(snapshotId);
    }

    @Test
    void exposesSnapshotConfigAndComparePhaseTwoEndpoints() throws Exception {
        String snapshotId = UUID.randomUUID().toString();
        String baselineRunId = UUID.randomUUID().toString();
        String candidateRunId = UUID.randomUUID().toString();
        CorpusSnapshotDetail snapshotDetail = new CorpusSnapshotDetail(new CorpusSnapshot(
            snapshotId,
            "snapshot-phase-2",
            EvalLifecycleStatus.ACTIVE,
            Instant.parse("2026-05-10T00:00:00Z"),
            "materials",
            "search",
            "config",
            Map.of("revisionPins", Map.of()),
            1,
            1,
            0,
            1,
            Map.of(),
            List.of(),
            Instant.parse("2026-05-10T00:00:00Z"),
            Instant.parse("2026-05-10T00:00:00Z")
        ), Map.of("revisionPins", Map.of()), Map.of("totalItemCount", 1));
        when(snapshotService.createSnapshot(any(CreateCorpusSnapshotRequest.class))).thenReturn(snapshotDetail);
        when(snapshotCatalogService.getSnapshotItems(snapshotId, 0, 25)).thenReturn(List.of(new CorpusSnapshotItemDetail(
            UUID.randomUUID().toString(),
            snapshotId,
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            "Material",
            "source-key",
            "ACTIVE",
            1,
            "v1",
            "READY",
            "DOC-1",
            java.time.LocalDate.parse("2026-05-10"),
            "POLICY",
            "ACTIVE",
            "general",
            "project",
            "RU",
            "content",
            "metadata",
            "structured-v1",
            2,
            "chunks",
            Map.of(),
            Instant.parse("2026-05-10T00:00:00Z"),
            Instant.parse("2026-05-10T00:00:00Z"),
            Instant.parse("2026-05-10T00:00:00Z")
        )));
        when(executionConfigService.resolve(any(ResolveEvalExecutionConfigRequest.class))).thenReturn(new ResolvedEvalExecutionConfig(
            new EvalExecutionConfig(
                "commit",
                "v1",
                snapshotId,
                "config",
                "production-current",
                null,
                Instant.parse("2026-05-10T00:00:00Z"),
                Map.of()
            ),
            "config",
            new EvalRuntimeStateSnapshot(Map.of("searchSync", Map.of("enabled", false)), "search", Instant.parse("2026-05-10T00:00:00Z"))
        ));
        when(comparisonService.createCompare(any(EvalCompareRequest.class))).thenReturn(new EvalRunCompare(
            UUID.randomUUID().toString(),
            baselineRunId,
            candidateRunId,
            EvalCompareStatus.COMPATIBLE,
            EvalCompatibilityStatus.COMPATIBLE,
            List.of(new EvalCompatibilityReason("compatible", "Runs are comparable", EvalCompatibilityStatus.COMPATIBLE, Map.of())),
            null,
            "v1",
            "v1",
            snapshotId,
            snapshotId,
            "materials",
            "materials",
            "search",
            "search",
            "config",
            "config",
            Map.of(
                "overallVerdict", "PASS",
                "metricSummary", Map.of(
                    "answer_correctness",
                    Map.of(
                        "metric", "answer_correctness",
                        "baselineValue", 1.0d,
                        "candidateValue", 1.0d,
                        "delta", 0.0d,
                        "direction", "HIGHER_IS_BETTER",
                        "verdict", "UNCHANGED",
                        "sampleSize", 1,
                        "nonScorableCount", 0
                    )
                )
            ),
            Instant.parse("2026-05-10T00:00:00Z"),
            Instant.parse("2026-05-10T00:00:00Z")
        ));

        mockMvc.perform(post("/api/evals/snapshots")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"snapshotKey\":\"snapshot-phase-2\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.snapshot.id").value(snapshotId))
            .andExpect(jsonPath("$.summary.totalItemCount").value(1));
        mockMvc.perform(get("/api/evals/snapshots/{id}/items?offset=0&limit=25", snapshotId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].sourceKey").value("source-key"))
            .andExpect(jsonPath("$[0].chunkSetHash").value("chunks"));
        mockMvc.perform(post("/api/evals/execution-configs/resolve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"corpusSnapshotId\":\"" + snapshotId + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.configHash").value("config"))
            .andExpect(jsonPath("$.runtimeState.searchStateHash").value("search"));
        mockMvc.perform(post("/api/evals/compares")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"baselineRunId\":\"" + baselineRunId + "\",\"candidateRunId\":\"" + candidateRunId + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.compatibilityStatus").value("COMPATIBLE"))
            .andExpect(jsonPath("$.baselineMaterialSetHash").value("materials"))
            .andExpect(jsonPath("$.summary.overallVerdict").value("PASS"))
            .andExpect(jsonPath("$.summary.metricSummary.answer_correctness.verdict").value("UNCHANGED"));

        verify(snapshotService).createSnapshot(any(CreateCorpusSnapshotRequest.class));
        verify(snapshotCatalogService).getSnapshotItems(snapshotId, 0, 25);
        verify(executionConfigService).resolve(any(ResolveEvalExecutionConfigRequest.class));
        verify(comparisonService).createCompare(any(EvalCompareRequest.class));
    }

    @Test
    void exposesRetrievalPreviewAndRunEndpoints() throws Exception {
        String runId = UUID.randomUUID().toString();
        String datasetId = UUID.randomUUID().toString();
        String snapshotId = UUID.randomUUID().toString();
        when(retrievalPreviewService.preview(any(RetrievalEvalPreviewRequest.class))).thenReturn(new RetrievalEvalPreviewResponse(
            "What is the approved limit?",
            null,
            null,
            "retrieval-config",
            RetrievalEvalReproducibilityStatus.UNPINNED,
            new RetrievalEvalTrace(
                null,
                null,
                null,
                1,
                1,
                1,
                1,
                1,
                "supported",
                "hybrid-rerank-v1",
                null,
                List.of("metadata-filters-v1"),
                List.of(),
                Instant.parse("2026-05-10T00:00:00Z"),
                null,
                null,
                null,
                "retrieval-config",
                "postgres",
                "nomic-embed-text",
                "structured-v1"
            ),
            new RetrievalEvalStageTrace(List.of(), List.of(), List.of(), List.of(), List.of()),
            List.of(new RetrievalEvalMetric(
                "doc_hit_rate@5",
                5,
                RetrievalEvalMetricStatus.SCORED,
                1.0,
                null
            )),
            List.of("Snapshot pins were not fully provided; preview is not reproducible.")
        ));
        when(retrievalRunService.createRun(any(CreateRetrievalEvalRunRequest.class))).thenReturn(new EvalRun(
            runId,
            datasetId,
            snapshotId,
            EvalRunKind.RETRIEVAL_ONLY,
            EvalRunStatus.COMPLETED,
            new EvalExecutionConfig(
                "commit",
                "v1",
                snapshotId,
                "retrieval-config",
                "production-current",
                null,
                Instant.parse("2026-05-10T00:00:00Z"),
                Map.of()
            ),
            "retrieval-config",
            Instant.parse("2026-05-10T00:00:00Z"),
            Instant.parse("2026-05-10T00:00:01Z"),
            Map.of("total", 1, "passed", 1),
            List.of(),
            Instant.parse("2026-05-10T00:00:00Z"),
            Instant.parse("2026-05-10T00:00:01Z")
        ));

        mockMvc.perform(post("/api/evals/preview/retrieval")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"What is the approved limit?\",\"limit\":5}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reproducibilityStatus").value("UNPINNED"))
            .andExpect(jsonPath("$.trace.retrievalConfigHash").value("retrieval-config"))
            .andExpect(jsonPath("$.metrics[0].name").value("doc_hit_rate@5"));
        mockMvc.perform(post("/api/evals/runs/retrieval")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"datasetId\":\"" + datasetId + "\",\"corpusSnapshotId\":\"" + snapshotId + "\",\"executionConfigHash\":\"retrieval-config\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(runId))
            .andExpect(jsonPath("$.runKind").value("RETRIEVAL_ONLY"))
            .andExpect(jsonPath("$.summary.passed").value(1));

        verify(retrievalPreviewService).preview(any(RetrievalEvalPreviewRequest.class));
        verify(retrievalRunService).createRun(any(CreateRetrievalEvalRunRequest.class));
    }

    @Test
    void exposesE2ERunReconcileAndArtifactEndpoints() throws Exception {
        String runId = UUID.randomUUID().toString();
        String datasetId = UUID.randomUUID().toString();
        String snapshotId = UUID.randomUUID().toString();
        String itemId = UUID.randomUUID().toString();
        EvalRun e2eRun = new EvalRun(
            runId,
            datasetId,
            snapshotId,
            EvalRunKind.E2E,
            EvalRunStatus.RUNNING,
            new EvalExecutionConfig(
                "commit",
                "v1",
                snapshotId,
                "config",
                "prompt-v1",
                null,
                Instant.parse("2026-05-10T00:00:00Z"),
                Map.of()
            ),
            "config",
            Instant.parse("2026-05-10T00:00:00Z"),
            null,
            Map.of("totalItems", 1, "openItems", 1),
            List.of(),
            Instant.parse("2026-05-10T00:00:00Z"),
            Instant.parse("2026-05-10T00:00:00Z")
        );
        when(e2eRunService.createRun(any(CreateE2EEvalRunRequest.class))).thenReturn(e2eRun);
        when(e2eReconciler.reconcileRun(runId)).thenReturn(new EvalRun(
            runId,
            datasetId,
            snapshotId,
            EvalRunKind.E2E,
            EvalRunStatus.COMPLETED,
            e2eRun.executionConfig(),
            "config",
            Instant.parse("2026-05-10T00:00:00Z"),
            Instant.parse("2026-05-10T00:00:01Z"),
            Map.of("totalItems", 1, "passedItems", 1),
            List.of(),
            Instant.parse("2026-05-10T00:00:00Z"),
            Instant.parse("2026-05-10T00:00:01Z")
        ));
        when(runCatalogService.getRunItemArtifacts(runId, itemId)).thenReturn(List.of(new EvalRunItemArtifact(
            itemId,
            EvalRunItemArtifactType.STRUCTURED_OUTPUT,
            Map.of("answer", "10"),
            Instant.parse("2026-05-10T00:00:01Z"),
            Instant.parse("2026-05-10T00:00:01Z")
        )));

        mockMvc.perform(post("/api/evals/runs/e2e")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"datasetId\":\"" + datasetId + "\",\"corpusSnapshotId\":\"" + snapshotId + "\"}"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.id").value(runId))
            .andExpect(jsonPath("$.runKind").value("E2E"))
            .andExpect(jsonPath("$.summary.totalItems").value(1));
        mockMvc.perform(post("/api/evals/runs/{id}/reconcile", runId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.summary.passedItems").value(1));
        mockMvc.perform(get("/api/evals/runs/{id}/items/{itemId}/artifacts", runId, itemId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].artifactType").value("STRUCTURED_OUTPUT"))
            .andExpect(jsonPath("$[0].payload.answer").value("10"));

        verify(e2eRunService).createRun(any(CreateE2EEvalRunRequest.class));
        verify(e2eReconciler).reconcileRun(runId);
        verify(runCatalogService).getRunItemArtifacts(runId, itemId);
    }

    @Test
    void unknownDatasetUsesExistingApiErrorShape() throws Exception {
        String datasetId = UUID.randomUUID().toString();
        when(catalogService.getDataset(datasetId)).thenThrow(new ApplicationException(
            ErrorType.NOT_FOUND,
            "eval_dataset.not_found",
            "Eval dataset '" + datasetId + "' does not exist"
        ));

        mockMvc.perform(get("/api/evals/datasets/{id}", datasetId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("eval_dataset.not_found"))
            .andExpect(jsonPath("$.message").value("Eval dataset '" + datasetId + "' does not exist"))
            .andExpect(jsonPath("$.requestId").exists());
    }

    @Test
    void listsDatasetSummaries() throws Exception {
        String datasetId = UUID.randomUUID().toString();
        when(catalogService.listDatasets()).thenReturn(List.of(new EvalDatasetSummary(
            datasetId,
            "golden-core",
            EvalDatasetKind.GOLDEN,
            "v1",
            EvalLifecycleStatus.ACTIVE,
            "Golden core",
            "Core regression set",
            List.of("core"),
            2,
            Instant.parse("2026-05-10T00:00:00Z"),
            Instant.parse("2026-05-10T00:00:00Z")
        )));

        mockMvc.perform(get("/api/evals/datasets"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(datasetId))
            .andExpect(jsonPath("$[0].caseCount").value(2))
            .andExpect(jsonPath("$[0].tags[0]").value("core"));
    }

    private EvalDataset dataset(String datasetId) {
        return new EvalDataset(
            datasetId,
            "golden-core",
            EvalDatasetKind.GOLDEN,
            "v1",
            EvalLifecycleStatus.ACTIVE,
            "Golden core",
            "Core regression set",
            List.of("core"),
            Instant.parse("2026-05-10T00:00:00Z"),
            Instant.parse("2026-05-10T00:00:00Z")
        );
    }

    private EvalCase evalCase(String datasetId, String caseId) {
        return new EvalCase(
            caseId,
            datasetId,
            "case-1",
            1,
            EvalCaseType.EXACT_FACT,
            EvalExpectedMode.ANSWER,
            EvalSeverity.BLOCKER,
            "What is the approved limit?",
            Map.of("workspaceKey", "general"),
            Map.of("versionLabel", "v1"),
            Map.of("facts", List.of("limit is 10")),
            Map.of("accepted", List.of("10")),
            Map.of("documents", List.of("doc-1")),
            new EvalCaseOrigin("manual", "seed", null, Map.of()),
            Instant.parse("2026-05-10T00:00:00Z"),
            Instant.parse("2026-05-10T00:00:00Z")
        );
    }
}
