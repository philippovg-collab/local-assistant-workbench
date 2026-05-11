package com.example.demo.service.eval;

import com.example.demo.model.eval.EvalModuleStatusResponse;
import com.example.demo.service.eval.port.CorpusSnapshotRepository;
import com.example.demo.service.eval.port.EvalCompareRepository;
import com.example.demo.service.eval.port.EvalDatasetRepository;
import com.example.demo.service.eval.port.EvalRunRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class EvalModuleStatusService {

    private static final List<String> CAPABILITIES = List.of(
        "READ_STATUS",
        "LIST_DATASETS",
        "READ_DATASET",
        "MANAGE_DATASET_LIFECYCLE",
        "MANAGE_CASE_LIFECYCLE",
        "PROMOTE_EVAL_CANDIDATES",
        "LIST_RUNS",
        "READ_RUN",
        "LIST_SNAPSHOTS",
        "READ_SNAPSHOT",
        "CREATE_SNAPSHOT",
        "READ_SNAPSHOT_ITEMS",
        "RESOLVE_EXECUTION_CONFIG",
        "COMPARE_RUNS_COMPATIBILITY",
        "PREVIEW_RETRIEVAL_EVAL",
        "CREATE_RETRIEVAL_EVAL_RUN",
        "CREATE_E2E_EVAL_RUN",
        "RECONCILE_E2E_EVAL_RUN",
        "READ_E2E_EVAL_ARTIFACTS"
    );

    private static final List<String> ENDPOINTS = List.of(
        "GET /api/evals/status",
        "GET /api/evals/datasets",
        "GET /api/evals/datasets/{id}",
        "POST /api/evals/datasets",
        "PATCH /api/evals/datasets/{id}",
        "POST /api/evals/datasets/{id}/archive",
        "POST /api/evals/datasets/{id}/versions",
        "GET /api/evals/datasets/{id}/versions",
        "POST /api/evals/datasets/{id}/cases",
        "PATCH /api/evals/cases/{id}",
        "GET /api/evals/cases/{id}/revisions",
        "POST /api/evals/cases/{id}/submit-review",
        "POST /api/evals/cases/{id}/reviews",
        "POST /api/evals/cases/{id}/archive",
        "POST /api/evals/candidates/from-chat-run",
        "POST /api/evals/cases/{id}/promote",
        "GET /api/evals/runs",
        "GET /api/evals/runs/{id}",
        "GET /api/evals/snapshots",
        "POST /api/evals/snapshots",
        "GET /api/evals/snapshots/{id}",
        "GET /api/evals/snapshots/{id}/items",
        "POST /api/evals/execution-configs/resolve",
        "POST /api/evals/compares",
        "POST /api/evals/preview/retrieval",
        "POST /api/evals/runs/retrieval",
        "POST /api/evals/runs/e2e",
        "POST /api/evals/runs/{id}/reconcile",
        "GET /api/evals/runs/{id}/items/{itemId}/artifacts"
    );

    private final EvalDatasetRepository datasetRepository;
    private final EvalRunRepository runRepository;
    private final CorpusSnapshotRepository snapshotRepository;
    private final EvalCompareRepository compareRepository;
    private final Clock clock;

    public EvalModuleStatusService(
        EvalDatasetRepository datasetRepository,
        EvalRunRepository runRepository,
        CorpusSnapshotRepository snapshotRepository,
        EvalCompareRepository compareRepository,
        Clock clock
    ) {
        this.datasetRepository = datasetRepository;
        this.runRepository = runRepository;
        this.snapshotRepository = snapshotRepository;
        this.compareRepository = compareRepository;
        this.clock = clock;
    }

    public EvalModuleStatusResponse getStatus() {
        boolean schemaReady = datasetRepository.isStorageReady()
            && runRepository.isStorageReady()
            && snapshotRepository.isStorageReady()
            && compareRepository.isStorageReady();
        return new EvalModuleStatusResponse(
            schemaReady,
            schemaReady,
            true,
            CAPABILITIES,
            ENDPOINTS,
            Instant.now(clock)
        );
    }
}
