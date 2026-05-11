package com.example.demo.service.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.error.ApplicationException;
import com.example.demo.model.QualityLayerFlags;
import com.example.demo.model.RetrievalDebug;
import com.example.demo.model.RetrievalFilters;
import com.example.demo.model.RetrievalQueryHints;
import com.example.demo.model.RetrievalTrace;
import com.example.demo.model.eval.CorpusSnapshot;
import com.example.demo.model.eval.EvalCase;
import com.example.demo.model.eval.EvalCaseOrigin;
import com.example.demo.model.eval.EvalCaseRevision;
import com.example.demo.model.eval.EvalCaseSeverity;
import com.example.demo.model.eval.EvalCaseType;
import com.example.demo.model.eval.EvalDataset;
import com.example.demo.model.eval.EvalDatasetDetail;
import com.example.demo.model.eval.EvalDatasetKind;
import com.example.demo.model.eval.EvalDatasetVersion;
import com.example.demo.model.eval.EvalDatasetVersionCaseRef;
import com.example.demo.model.eval.EvalExecutionConfig;
import com.example.demo.model.eval.EvalExpectedMode;
import com.example.demo.model.eval.EvalLifecycleStatus;
import com.example.demo.model.eval.EvalReviewStatus;
import com.example.demo.model.eval.RetrievalEvalMetric;
import com.example.demo.model.eval.RetrievalEvalPreviewRequest;
import com.example.demo.model.eval.RetrievalEvalPreviewResponse;
import com.example.demo.model.eval.ResolvedEvalExecutionConfig;
import com.example.demo.service.MaterialRetrievalService;
import com.example.demo.service.ProductionLexicalSearchRouter;
import com.example.demo.service.RelevanceProfile;
import com.example.demo.service.RetrievalSearchExecution;
import com.example.demo.service.eval.EvalSnapshotConsistencyService.SnapshotConsistencyResult;
import com.example.demo.service.eval.port.EvalDatasetRepository;
import com.example.demo.service.material.LexicalProviderMode;
import com.example.demo.service.material.LexicalProviderType;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RetrievalEvalPreviewServiceTest {

    private static final Instant REFERENCE_INSTANT = Instant.parse("2026-05-10T00:00:00Z");

    private final MaterialRetrievalService retrievalService = mock(MaterialRetrievalService.class);
    private final EvalDatasetRepository datasetRepository = mock(EvalDatasetRepository.class);
    private final EvalSnapshotConsistencyService snapshotConsistencyService = mock(EvalSnapshotConsistencyService.class);
    private final RetrievalScoringService scoringService = mock(RetrievalScoringService.class);
    private final RetrievalEvalPreviewService service = new RetrievalEvalPreviewService(
        retrievalService,
        datasetRepository,
        snapshotConsistencyService,
        scoringService,
        JsonMapper.builder().findAndAddModules().build()
    );

    @Test
    void previewWithDatasetVersionUsesPinnedRevisionAfterLiveCaseUpdated() {
        EvalCase oldCase = evalCase(1, "Old pinned question?");
        EvalCase currentCase = evalCase(2, "Current live question?");
        when(datasetRepository.findDatasetVersion("dataset-id", "v1")).thenReturn(Optional.of(datasetVersion(oldCase)));
        when(datasetRepository.findCaseRevision("case-id", 1)).thenReturn(Optional.of(revision(oldCase, "old-content-hash")));
        when(datasetRepository.findDatasetDetail("dataset-id")).thenReturn(Optional.of(datasetDetail(currentCase)));
        when(retrievalService.executeRetrieval(any())).thenReturn(emptyExecution());
        when(scoringService.score(any(EvalCase.class), anyList(), any(RetrievalSearchExecution.class), anyInt()))
            .thenReturn(List.of(RetrievalEvalMetric.scored("doc_hit_rate@k", 5, 1.0d)));

        RetrievalEvalPreviewResponse response = service.preview(new RetrievalEvalPreviewRequest(
            null,
            "dataset-id",
            "v1",
            "case-id",
            1,
            null,
            null,
            null,
            null,
            List.of(),
            null,
            5,
            true,
            false
        ));

        assertEquals("Old pinned question?", response.query());
        assertEquals(1, response.caseRef().caseRevision());
    }

    @Test
    void persistentPreviewValidatesConfigHashAgainstRequestedDatasetVersion() {
        EvalCase oldCase = evalCase(1, "Old pinned question?");
        CorpusSnapshot snapshot = snapshot();
        when(datasetRepository.findDatasetVersion("dataset-id", "v1")).thenReturn(Optional.of(datasetVersion(oldCase)));
        when(datasetRepository.findCaseRevision("case-id", 1)).thenReturn(Optional.of(revision(oldCase, "old-content-hash")));
        when(snapshotConsistencyService.getSnapshot("snapshot-id")).thenReturn(snapshot);
        when(snapshotConsistencyService.validatePersistentRun(
            "dataset-id",
            "v1",
            "snapshot-id",
            "config-hash",
            REFERENCE_INSTANT
        )).thenReturn(consistency(snapshot));
        when(retrievalService.executeRetrieval(any())).thenReturn(emptyExecution());
        when(scoringService.score(any(EvalCase.class), anyList(), any(RetrievalSearchExecution.class), anyInt()))
            .thenReturn(List.of());

        service.preview(new RetrievalEvalPreviewRequest(
            null,
            "dataset-id",
            "v1",
            "case-id",
            1,
            "snapshot-id",
            "config-hash",
            null,
            null,
            List.of(),
            REFERENCE_INSTANT,
            5,
            true,
            false
        ));

        verify(snapshotConsistencyService).validatePersistentRun(
            "dataset-id",
            "v1",
            "snapshot-id",
            "config-hash",
            REFERENCE_INSTANT
        );
    }

    @Test
    void caseNotInDatasetVersionReturnsInvalidRequest() {
        EvalCase oldCase = evalCase(1, "Old pinned question?");
        when(datasetRepository.findDatasetVersion("dataset-id", "v1")).thenReturn(Optional.of(datasetVersion(oldCase)));

        ApplicationException exception = assertThrows(ApplicationException.class, () -> service.preview(new RetrievalEvalPreviewRequest(
            null,
            "dataset-id",
            "v1",
            "missing-case",
            1,
            null,
            null,
            null,
            null,
            List.of(),
            null,
            5,
            true,
            false
        )));

        assertEquals("eval_preview.case_not_in_dataset_version", exception.getCode());
    }

    @Test
    void standalonePreviewWithoutDatasetVersionUsesCurrentDatasetDetail() {
        EvalCase currentCase = evalCase(2, "Current live question?");
        when(datasetRepository.findDatasetDetail("dataset-id")).thenReturn(Optional.of(datasetDetail(currentCase)));
        when(retrievalService.executeRetrieval(any())).thenReturn(emptyExecution());
        when(scoringService.score(any(EvalCase.class), anyList(), any(RetrievalSearchExecution.class), anyInt()))
            .thenReturn(List.of());

        RetrievalEvalPreviewResponse response = service.preview(new RetrievalEvalPreviewRequest(
            null,
            "dataset-id",
            null,
            "case-id",
            null,
            null,
            null,
            null,
            null,
            List.of(),
            null,
            5,
            true,
            false
        ));

        assertEquals("Current live question?", response.query());
        assertEquals(2, response.caseRef().caseRevision());
    }

    private SnapshotConsistencyResult consistency(CorpusSnapshot snapshot) {
        EvalExecutionConfig config = new EvalExecutionConfig(
            "commit",
            "dataset-id",
            "v1",
            "snapshot-id",
            REFERENCE_INSTANT,
            "material-hash",
            "search-hash",
            "config-hash",
            "prompt-v1",
            "judge-v1",
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of()
        );
        return new SnapshotConsistencyResult(snapshot, new ResolvedEvalExecutionConfig(config, "config-hash", null), Set.of());
    }

    private CorpusSnapshot snapshot() {
        return new CorpusSnapshot(
            "snapshot-id",
            "snapshot-key",
            EvalLifecycleStatus.ACTIVE,
            REFERENCE_INSTANT,
            "material-hash",
            "search-hash",
            "config-hash",
            Map.of(),
            0,
            0,
            0,
            0,
            Map.of(),
            List.of(),
            REFERENCE_INSTANT,
            REFERENCE_INSTANT
        );
    }

    private EvalDatasetVersion datasetVersion(EvalCase evalCase) {
        EvalDatasetVersionCaseRef ref = new EvalDatasetVersionCaseRef(
            evalCase.id(),
            evalCase.caseKey(),
            evalCase.revision(),
            evalCase.reviewStatus(),
            "old-content-hash"
        );
        return new EvalDatasetVersion(
            "version-id",
            "dataset-id",
            "v1",
            "dataset-hash",
            1,
            List.of(ref.toMap()),
            "tester",
            null,
            REFERENCE_INSTANT
        );
    }

    private EvalCaseRevision revision(EvalCase evalCase, String contentHash) {
        return new EvalCaseRevision("revision-id", evalCase, contentHash, "tester", REFERENCE_INSTANT);
    }

    private EvalDatasetDetail datasetDetail(EvalCase evalCase) {
        return new EvalDatasetDetail(
            new EvalDataset(
                "dataset-id",
                "dataset-key",
                EvalDatasetKind.GOLDEN,
                "current",
                EvalLifecycleStatus.ACTIVE,
                "Dataset",
                null,
                List.of(),
                REFERENCE_INSTANT,
                REFERENCE_INSTANT
            ),
            List.of(evalCase),
            List.of()
        );
    }

    private EvalCase evalCase(int revision, String question) {
        return new EvalCase(
            "case-id",
            "dataset-id",
            "case-key",
            revision,
            EvalCaseType.EXACT_FACT,
            EvalExpectedMode.ANSWER,
            EvalCaseSeverity.BLOCKER,
            question,
            Map.of(),
            Map.of(),
            List.of("answer"),
            List.of("answer"),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            new EvalCaseOrigin("manual", "seed", null, Map.of()),
            EvalReviewStatus.APPROVED,
            true,
            REFERENCE_INSTANT,
            REFERENCE_INSTANT
        );
    }

    private RetrievalSearchExecution emptyExecution() {
        RetrievalTrace trace = new RetrievalTrace(0, 0, 0, 0, 0, 0, 0, 0, 0);
        RetrievalDebug debug = new RetrievalDebug(
            RetrievalQueryHints.empty(),
            RetrievalFilters.empty(),
            RetrievalFilters.empty(),
            0,
            0,
            0,
            0,
            "none",
            RelevanceProfile.LEGACY.propertyValue(),
            QualityLayerFlags.none(),
            List.of(),
            List.of(),
            REFERENCE_INSTANT,
            null,
            null,
            null,
            "runtime-config-hash",
            "postgres",
            "test-embedding",
            "default"
        );
        return new RetrievalSearchExecution(
            Set.of(),
            RetrievalFilters.empty(),
            RetrievalFilters.empty(),
            RetrievalQueryHints.empty(),
            0,
            0,
            0,
            0,
            0,
            0,
            List.of(),
            new ProductionLexicalSearchRouter.LexicalSearchResult(
                LexicalProviderMode.POSTGRES,
                LexicalProviderType.POSTGRES,
                false,
                null,
                null,
                null,
                List.of()
            ),
            List.of(),
            List.of(),
            List.of(),
            0,
            List.of(),
            Map.of(),
            Map.of(),
            trace,
            debug,
            RelevanceProfile.LEGACY,
            QualityLayerFlags.none(),
            List.of(),
            List.of()
        );
    }
}
