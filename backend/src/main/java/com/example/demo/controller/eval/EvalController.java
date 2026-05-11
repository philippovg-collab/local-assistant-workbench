package com.example.demo.controller.eval;

import com.example.demo.model.eval.CorpusSnapshot;
import com.example.demo.model.eval.CorpusSnapshotDetail;
import com.example.demo.model.eval.CorpusSnapshotItemDetail;
import com.example.demo.model.eval.CreateEvalCandidateFromChatRunRequest;
import com.example.demo.model.eval.CreateEvalCaseRequest;
import com.example.demo.model.eval.CreateEvalCaseReviewRequest;
import com.example.demo.model.eval.CreateCorpusSnapshotRequest;
import com.example.demo.model.eval.CreateE2EEvalRunRequest;
import com.example.demo.model.eval.CreateEvalDatasetRequest;
import com.example.demo.model.eval.CreateEvalDatasetVersionRequest;
import com.example.demo.model.eval.CreateRetrievalEvalRunRequest;
import com.example.demo.model.eval.EvalCase;
import com.example.demo.model.eval.EvalCasePromotion;
import com.example.demo.model.eval.EvalCaseRevision;
import com.example.demo.model.eval.EvalDataset;
import com.example.demo.model.eval.EvalDatasetDetail;
import com.example.demo.model.eval.EvalDatasetVersion;
import com.example.demo.model.eval.EvalDatasetSummary;
import com.example.demo.model.eval.EvalCompareRequest;
import com.example.demo.model.eval.EvalRunCompare;
import com.example.demo.model.eval.EvalModuleStatusResponse;
import com.example.demo.model.eval.EvalRun;
import com.example.demo.model.eval.EvalRunItemArtifact;
import com.example.demo.model.eval.RetrievalEvalPreviewRequest;
import com.example.demo.model.eval.RetrievalEvalPreviewResponse;
import com.example.demo.model.eval.PromoteEvalCaseRequest;
import com.example.demo.model.eval.ResolveEvalExecutionConfigRequest;
import com.example.demo.model.eval.ResolvedEvalExecutionConfig;
import com.example.demo.model.eval.SubmitEvalCaseReviewRequest;
import com.example.demo.model.eval.UpdateEvalCaseRequest;
import com.example.demo.model.eval.UpdateEvalDatasetRequest;
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
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/evals")
public class EvalController {

    private final EvalModuleStatusService statusService;
    private final EvalCatalogService catalogService;
    private final EvalDatasetLifecycleService lifecycleService;
    private final EvalRunCatalogService runCatalogService;
    private final CorpusSnapshotCatalogService snapshotCatalogService;
    private final CorpusSnapshotService snapshotService;
    private final EvalExecutionConfigService executionConfigService;
    private final EvalComparisonService comparisonService;
    private final RetrievalEvalPreviewService retrievalPreviewService;
    private final RetrievalEvalRunService retrievalRunService;
    private final EvalE2ERunService e2eRunService;
    private final EvalChatRunReconciler e2eReconciler;

    public EvalController(
        EvalModuleStatusService statusService,
        EvalCatalogService catalogService,
        EvalDatasetLifecycleService lifecycleService,
        EvalRunCatalogService runCatalogService,
        CorpusSnapshotCatalogService snapshotCatalogService,
        CorpusSnapshotService snapshotService,
        EvalExecutionConfigService executionConfigService,
        EvalComparisonService comparisonService,
        RetrievalEvalPreviewService retrievalPreviewService,
        RetrievalEvalRunService retrievalRunService,
        EvalE2ERunService e2eRunService,
        EvalChatRunReconciler e2eReconciler
    ) {
        this.statusService = statusService;
        this.catalogService = catalogService;
        this.lifecycleService = lifecycleService;
        this.runCatalogService = runCatalogService;
        this.snapshotCatalogService = snapshotCatalogService;
        this.snapshotService = snapshotService;
        this.executionConfigService = executionConfigService;
        this.comparisonService = comparisonService;
        this.retrievalPreviewService = retrievalPreviewService;
        this.retrievalRunService = retrievalRunService;
        this.e2eRunService = e2eRunService;
        this.e2eReconciler = e2eReconciler;
    }

    @GetMapping("/status")
    public EvalModuleStatusResponse status() {
        return statusService.getStatus();
    }

    @GetMapping("/datasets")
    public List<EvalDatasetSummary> listDatasets() {
        return catalogService.listDatasets();
    }

    @GetMapping("/datasets/{id}")
    public EvalDatasetDetail getDataset(@PathVariable String id) {
        return catalogService.getDataset(id);
    }

    @PostMapping("/datasets")
    public EvalDataset createDataset(@Valid @RequestBody CreateEvalDatasetRequest request) {
        return lifecycleService.createDataset(request);
    }

    @PatchMapping("/datasets/{id}")
    public EvalDataset updateDataset(
        @PathVariable String id,
        @Valid @RequestBody UpdateEvalDatasetRequest request
    ) {
        return lifecycleService.updateDataset(id, request);
    }

    @PostMapping("/datasets/{id}/archive")
    public EvalDataset archiveDataset(@PathVariable String id) {
        return lifecycleService.archiveDataset(id);
    }

    @PostMapping("/datasets/{id}/versions")
    public EvalDatasetVersion createDatasetVersion(
        @PathVariable String id,
        @Valid @RequestBody(required = false) CreateEvalDatasetVersionRequest request
    ) {
        return lifecycleService.createDatasetVersion(
            id,
            request == null ? new CreateEvalDatasetVersionRequest(null, null) : request
        );
    }

    @GetMapping("/datasets/{id}/versions")
    public List<EvalDatasetVersion> listDatasetVersions(@PathVariable String id) {
        return lifecycleService.listDatasetVersions(id);
    }

    @PostMapping("/datasets/{id}/cases")
    public EvalCase createCase(
        @PathVariable String id,
        @Valid @RequestBody CreateEvalCaseRequest request
    ) {
        return lifecycleService.createCase(id, request);
    }

    @PatchMapping("/cases/{id}")
    public EvalCase updateCase(
        @PathVariable String id,
        @Valid @RequestBody UpdateEvalCaseRequest request
    ) {
        return lifecycleService.updateCase(id, request);
    }

    @GetMapping("/cases/{id}/revisions")
    public List<EvalCaseRevision> listCaseRevisions(@PathVariable String id) {
        return lifecycleService.listCaseRevisions(id);
    }

    @PostMapping("/cases/{id}/submit-review")
    public EvalCase submitCaseReview(
        @PathVariable String id,
        @Valid @RequestBody(required = false) SubmitEvalCaseReviewRequest request
    ) {
        return lifecycleService.submitCaseReview(
            id,
            request == null ? new SubmitEvalCaseReviewRequest(null) : request
        );
    }

    @PostMapping("/cases/{id}/reviews")
    public EvalCase reviewCase(
        @PathVariable String id,
        @Valid @RequestBody CreateEvalCaseReviewRequest request
    ) {
        return lifecycleService.reviewCase(id, request);
    }

    @PostMapping("/cases/{id}/archive")
    public EvalCase archiveCase(@PathVariable String id) {
        return lifecycleService.archiveCase(id);
    }

    @PostMapping("/candidates/from-chat-run")
    public EvalCase createCandidateFromChatRun(
        @Valid @RequestBody CreateEvalCandidateFromChatRunRequest request
    ) {
        return lifecycleService.createCandidateFromChatRun(request);
    }

    @PostMapping("/cases/{id}/promote")
    public EvalCasePromotion promoteCase(
        @PathVariable String id,
        @Valid @RequestBody PromoteEvalCaseRequest request
    ) {
        return lifecycleService.promoteCase(id, request);
    }

    @GetMapping("/runs")
    public List<EvalRun> listRuns() {
        return runCatalogService.listRuns();
    }

    @GetMapping("/runs/{id}")
    public EvalRun getRun(@PathVariable String id) {
        return runCatalogService.getRun(id);
    }

    @GetMapping("/runs/{id}/items/{itemId}/artifacts")
    public List<EvalRunItemArtifact> getRunItemArtifacts(
        @PathVariable String id,
        @PathVariable String itemId
    ) {
        return runCatalogService.getRunItemArtifacts(id, itemId);
    }

    @GetMapping("/snapshots")
    public List<CorpusSnapshot> listSnapshots() {
        return snapshotCatalogService.listSnapshots();
    }

    @PostMapping("/snapshots")
    public CorpusSnapshotDetail createSnapshot(@RequestBody(required = false) CreateCorpusSnapshotRequest request) {
        return snapshotService.createSnapshot(request);
    }

    @GetMapping("/snapshots/{id}")
    public CorpusSnapshotDetail getSnapshot(@PathVariable String id) {
        return snapshotCatalogService.getSnapshotDetail(id);
    }

    @GetMapping("/snapshots/{id}/items")
    public List<CorpusSnapshotItemDetail> getSnapshotItems(
        @PathVariable String id,
        @RequestParam(defaultValue = "0") int offset,
        @RequestParam(defaultValue = "100") int limit
    ) {
        return snapshotCatalogService.getSnapshotItems(id, offset, limit);
    }

    @PostMapping("/execution-configs/resolve")
    public ResolvedEvalExecutionConfig resolveExecutionConfig(
        @RequestBody(required = false) ResolveEvalExecutionConfigRequest request
    ) {
        return executionConfigService.resolve(request);
    }

    @PostMapping("/compares")
    public EvalRunCompare createCompare(@RequestBody EvalCompareRequest request) {
        return comparisonService.createCompare(request);
    }

    @PostMapping("/preview/retrieval")
    public RetrievalEvalPreviewResponse previewRetrieval(
        @Valid @RequestBody(required = false) RetrievalEvalPreviewRequest request
    ) {
        return retrievalPreviewService.preview(request);
    }

    @PostMapping("/runs/retrieval")
    public EvalRun createRetrievalRun(@Valid @RequestBody CreateRetrievalEvalRunRequest request) {
        return retrievalRunService.createRun(request);
    }

    @PostMapping("/runs/e2e")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public EvalRun createE2ERun(@Valid @RequestBody CreateE2EEvalRunRequest request) {
        return e2eRunService.createRun(request);
    }

    @PostMapping("/runs/{id}/reconcile")
    public EvalRun reconcileE2ERun(@PathVariable String id) {
        return e2eReconciler.reconcileRun(id);
    }
}
