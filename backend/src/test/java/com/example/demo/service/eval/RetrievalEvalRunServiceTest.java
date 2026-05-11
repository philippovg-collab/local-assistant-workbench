package com.example.demo.service.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.demo.error.ApplicationException;
import com.example.demo.error.ErrorType;
import com.example.demo.model.eval.CreateRetrievalEvalRunRequest;
import com.example.demo.model.eval.EvalCase;
import com.example.demo.model.eval.EvalCaseOrigin;
import com.example.demo.model.eval.EvalCaseRevision;
import com.example.demo.model.eval.EvalCaseSeverity;
import com.example.demo.model.eval.EvalCaseType;
import com.example.demo.model.eval.EvalDatasetVersion;
import com.example.demo.model.eval.EvalDatasetVersionCaseRef;
import com.example.demo.model.eval.EvalExecutionConfig;
import com.example.demo.model.eval.EvalExpectedMode;
import com.example.demo.model.eval.EvalReviewStatus;
import com.example.demo.model.eval.EvalRun;
import com.example.demo.model.eval.RetrievalEvalPreviewRequest;
import com.example.demo.model.eval.RetrievalEvalPreviewResponse;
import com.example.demo.model.eval.RetrievalEvalReproducibilityStatus;
import com.example.demo.model.eval.RetrievalEvalStageTrace;
import com.example.demo.model.eval.ResolvedEvalExecutionConfig;
import com.example.demo.service.eval.EvalDatasetVersionCaseResolver.ResolvedCase;
import com.example.demo.service.eval.EvalDatasetVersionCaseResolver.ResolvedCases;
import com.example.demo.service.eval.EvalSnapshotConsistencyService.SnapshotConsistencyResult;
import com.example.demo.service.eval.port.EvalRunRepository;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RetrievalEvalRunServiceTest {

    private final EvalDatasetVersionCaseResolver caseResolver = mock(EvalDatasetVersionCaseResolver.class);
    private final EvalRunRepository runRepository = mock(EvalRunRepository.class);
    private final EvalSnapshotConsistencyService snapshotConsistencyService = mock(EvalSnapshotConsistencyService.class);
    private final RetrievalEvalPreviewService previewService = mock(RetrievalEvalPreviewService.class);
    private final RetrievalEvalRunService service = new RetrievalEvalRunService(
        caseResolver,
        runRepository,
        snapshotConsistencyService,
        previewService,
        JsonMapper.builder().findAndAddModules().build(),
        Clock.fixed(Instant.parse("2026-05-10T00:00:00Z"), ZoneOffset.UTC)
    );

    @Test
    void runUsesPinnedDatasetVersionCaseRevisionAndMetadata() {
        EvalCase oldCase = evalCase(1, "Old pinned question?");
        ResolvedCases resolvedCases = resolvedCases(oldCase, "case-content-hash");
        CreateRetrievalEvalRunRequest request = request(List.of());
        when(caseResolver.resolve("dataset-id", "v1", List.of(), 50, true)).thenReturn(resolvedCases);
        when(snapshotConsistencyService.validatePersistentRun(
            "dataset-id",
            "v1",
            "snapshot-id",
            "config-hash",
            Instant.parse("2026-05-10T00:00:00Z")
        )).thenReturn(consistency());
        when(runRepository.saveRun(any(EvalRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(runRepository.saveItem(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(previewService.preview(any(RetrievalEvalPreviewRequest.class), eq("v1"))).thenReturn(preview());

        EvalRun run = service.createRun(request);

        assertEquals("v1", run.summary().get("datasetVersion"));
        assertEquals("version-id", run.summary().get("datasetVersionId"));
        assertEquals(1, run.summary().get("selectedCaseCount"));
        assertFalse(String.valueOf(run.summary().get("caseRevisionRefHash")).isBlank());
        assertEquals(1, run.items().getFirst().caseRevision());
        assertEquals("case-content-hash", run.items().getFirst().artifact().get("caseContentHash"));
        assertEquals("v1", run.items().getFirst().artifact().get("datasetVersion"));
        assertEquals("CASE_REVISION", run.items().getFirst().artifact().get("caseSnapshotSource"));
    }

    @Test
    void requestedCaseOutsideDatasetVersionReturnsInvalidRequest() {
        CreateRetrievalEvalRunRequest request = request(List.of("missing-case"));
        when(caseResolver.resolve("dataset-id", "v1", List.of("missing-case"), 50, true)).thenThrow(new ApplicationException(
            ErrorType.INVALID_REQUEST,
            "eval_run.case_not_in_dataset_version",
            "missing"
        ));

        ApplicationException exception = assertThrows(ApplicationException.class, () -> service.createRun(request));

        assertEquals("eval_run.case_not_in_dataset_version", exception.getCode());
    }

    private CreateRetrievalEvalRunRequest request(List<String> caseIds) {
        return new CreateRetrievalEvalRunRequest(
            "dataset-id",
            "v1",
            caseIds,
            "snapshot-id",
            "config-hash",
            Instant.parse("2026-05-10T00:00:00Z"),
            null,
            List.of("phase-1")
        );
    }

    private ResolvedCases resolvedCases(EvalCase evalCase, String contentHash) {
        EvalDatasetVersionCaseRef ref = new EvalDatasetVersionCaseRef(
            evalCase.id(),
            evalCase.caseKey(),
            evalCase.revision(),
            evalCase.reviewStatus(),
            contentHash
        );
        return new ResolvedCases(
            new EvalDatasetVersion(
                "version-id",
                "dataset-id",
                "v1",
                "dataset-hash",
                1,
                List.of(ref.toMap()),
                "tester",
                null,
                Instant.parse("2026-05-10T00:00:00Z")
            ),
            "ref-hash",
            List.of(new ResolvedCase(new EvalDatasetVersionCaseRef(
                evalCase.id(),
                evalCase.caseKey(),
                evalCase.revision(),
                evalCase.reviewStatus(),
                contentHash
            ), new EvalCaseRevision(
                "revision-id",
                evalCase,
                contentHash,
                "tester",
                Instant.parse("2026-05-10T00:00:00Z")
            )))
        );
    }

    private SnapshotConsistencyResult consistency() {
        EvalExecutionConfig config = new EvalExecutionConfig(
            "commit",
            "dataset-id",
            "v1",
            "snapshot-id",
            Instant.parse("2026-05-10T00:00:00Z"),
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
        return new SnapshotConsistencyResult(null, new ResolvedEvalExecutionConfig(config, "config-hash", null), Set.of());
    }

    private RetrievalEvalPreviewResponse preview() {
        return new RetrievalEvalPreviewResponse(
            "Old pinned question?",
            null,
            null,
            "config-hash",
            RetrievalEvalReproducibilityStatus.PINNED,
            null,
            new RetrievalEvalStageTrace(List.of(), List.of(), List.of(), List.of(), List.of()),
            List.of(),
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
            Map.of("versionLabel", "old"),
            List.of("answer"),
            List.of("answer"),
            List.of(),
            List.of(),
            List.of(),
            List.of("core"),
            new EvalCaseOrigin("manual", "seed", null, Map.of()),
            EvalReviewStatus.APPROVED,
            true,
            Instant.parse("2026-05-10T00:00:00Z"),
            Instant.parse("2026-05-10T00:00:00Z")
        );
    }
}
