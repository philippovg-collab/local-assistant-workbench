package com.example.demo.service.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.demo.model.ChatRunSubmissionResponse;
import com.example.demo.model.eval.CreateE2EEvalRunRequest;
import com.example.demo.model.eval.EvalCase;
import com.example.demo.model.eval.EvalCaseOrigin;
import com.example.demo.model.eval.EvalCaseRevision;
import com.example.demo.model.eval.EvalCaseSeverity;
import com.example.demo.model.eval.EvalCaseType;
import com.example.demo.model.eval.EvalDatasetVersion;
import com.example.demo.model.eval.EvalDatasetVersionCaseRef;
import com.example.demo.model.eval.EvalExecutionConfig;
import com.example.demo.model.eval.EvalExpectedMode;
import com.example.demo.model.eval.EvalJudgeMode;
import com.example.demo.model.eval.EvalReviewStatus;
import com.example.demo.model.eval.EvalRun;
import com.example.demo.model.eval.ResolvedEvalExecutionConfig;
import com.example.demo.service.ChatRunSubmissionCoordinator;
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

class EvalE2ERunServiceTest {

    private final EvalDatasetVersionCaseResolver caseResolver = mock(EvalDatasetVersionCaseResolver.class);
    private final EvalRunRepository runRepository = mock(EvalRunRepository.class);
    private final EvalSnapshotConsistencyService snapshotConsistencyService = mock(EvalSnapshotConsistencyService.class);
    private final ChatRunSubmissionCoordinator chatRunSubmissionCoordinator = mock(ChatRunSubmissionCoordinator.class);
    private final EvalE2ERunService service = new EvalE2ERunService(
        caseResolver,
        runRepository,
        snapshotConsistencyService,
        new EvalChatRunRequestFactory(JsonMapper.builder().findAndAddModules().build()),
        chatRunSubmissionCoordinator,
        JsonMapper.builder().findAndAddModules().build(),
        Clock.fixed(Instant.parse("2026-05-10T00:00:00Z"), ZoneOffset.UTC)
    );

    @Test
    void submittedItemStoresPinnedRevisionMetadataFromSelectedVersion() {
        EvalCase evalCase = evalCase();
        ResolvedCases resolvedCases = resolvedCases(evalCase, "case-content-hash");
        CreateE2EEvalRunRequest request = request();
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
        when(chatRunSubmissionCoordinator.submit(any())).thenReturn(new ChatRunSubmissionResponse(
            "11111111-1111-1111-1111-111111111111",
            "QUEUED",
            Instant.parse("2026-05-10T00:00:00Z"),
            "/status",
            "/trace",
            "/result"
        ));

        EvalRun run = service.createRun(request);

        assertEquals("v1", run.summary().get("datasetVersion"));
        assertEquals("version-id", run.summary().get("datasetVersionId"));
        assertEquals(1, run.summary().get("selectedCaseCount"));
        assertEquals(3, run.items().getFirst().caseRevision());
        assertEquals("case-key", run.items().getFirst().artifact().get("caseKey"));
        assertEquals("case-content-hash", run.items().getFirst().artifact().get("caseContentHash"));
        assertEquals("v1", run.items().getFirst().artifact().get("datasetVersion"));
        assertEquals("CASE_REVISION", run.items().getFirst().artifact().get("caseSnapshotSource"));
    }

    private CreateE2EEvalRunRequest request() {
        return new CreateE2EEvalRunRequest(
            "dataset-id",
            "v1",
            List.of(),
            "snapshot-id",
            "config-hash",
            Instant.parse("2026-05-10T00:00:00Z"),
            "model",
            null,
            EvalJudgeMode.DETERMINISTIC_ONLY,
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
            List.of(new ResolvedCase(ref, new EvalCaseRevision(
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

    private EvalCase evalCase() {
        return new EvalCase(
            "case-id",
            "dataset-id",
            "case-key",
            3,
            EvalCaseType.EXACT_FACT,
            EvalExpectedMode.ANSWER,
            EvalCaseSeverity.BLOCKER,
            "What is the pinned answer?",
            Map.of("workspaceKey", "general"),
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
