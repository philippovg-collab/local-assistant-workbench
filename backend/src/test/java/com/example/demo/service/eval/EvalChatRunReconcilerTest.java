package com.example.demo.service.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.config.EvalProperties;
import com.example.demo.model.AnswerMode;
import com.example.demo.model.ChatExecutionResponse;
import com.example.demo.model.ChatMode;
import com.example.demo.model.ChatRunStatusResponse;
import com.example.demo.model.ChatRunTraceDetail;
import com.example.demo.model.eval.EvalCase;
import com.example.demo.model.eval.EvalCaseOrigin;
import com.example.demo.model.eval.EvalCaseRevision;
import com.example.demo.model.eval.EvalCaseSeverity;
import com.example.demo.model.eval.EvalCaseType;
import com.example.demo.model.eval.EvalE2EScoreSummary;
import com.example.demo.model.eval.EvalExpectedMode;
import com.example.demo.model.eval.EvalFailureCode;
import com.example.demo.model.eval.EvalReviewStatus;
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
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class EvalChatRunReconcilerTest {

    private static final Instant NOW = Instant.parse("2026-05-10T00:00:00Z");

    @Test
    void scoresPinnedRevisionAndSavesCaseSnapshotBeforeScoring() {
        RecordingRunRepository runRepository = new RecordingRunRepository();
        runRepository.runs.put("run-id", run());
        runRepository.items.put("item-id", runningItem(1));

        EvalCase oldCase = evalCase(1, "Old question?", "old answer");
        EvalCase liveCase = evalCase(2, "Live question?", "live answer");
        EvalDatasetRepository datasetRepository = mock(EvalDatasetRepository.class);
        when(datasetRepository.findCaseRevision("case-id", 1))
            .thenReturn(Optional.of(new EvalCaseRevision("revision-id", oldCase, "old-content-hash", "tester", NOW)));
        when(datasetRepository.findDatasetDetail("dataset-id")).thenReturn(Optional.of(new com.example.demo.model.eval.EvalDatasetDetail(
            null,
            List.of(liveCase),
            List.of()
        )));

        ChatRunQueryService chatRunQueryService = mock(ChatRunQueryService.class);
        when(chatRunQueryService.getStatus("chat-run-id")).thenReturn(completedStatus());
        when(chatRunQueryService.getResult("chat-run-id")).thenReturn(result("""
            {"answer":"old answer","finalMode":"answered","claims":[]}
            """));
        when(chatRunQueryService.getTrace("chat-run-id")).thenReturn(trace(null));

        EvalE2EScoringService scoringService = mock(EvalE2EScoringService.class);
        AtomicReference<EvalCase> scoredCase = new AtomicReference<>();
        when(scoringService.score(any(EvalCase.class), any(EvalStructuredAnswer.class))).thenAnswer(invocation -> {
            assertTrue(runRepository.findArtifact("item-id", EvalRunItemArtifactType.CASE_SNAPSHOT).isPresent());
            scoredCase.set(invocation.getArgument(0, EvalCase.class));
            return new EvalE2EScoreSummary(true, 1, 0, Map.of("accepted_answer_match", 1.0d), Map.of());
        });

        reconciler(runRepository, datasetRepository, chatRunQueryService, scoringService).reconcileRun("run-id");

        assertNotNull(scoredCase.get());
        assertEquals(1, scoredCase.get().revision());
        assertEquals("old answer", scoredCase.get().acceptedAnswers().getFirst());
        EvalRunItem savedItem = runRepository.items.get("item-id");
        assertEquals(EvalRunItemStatus.PASSED, savedItem.status());
        EvalRunItemArtifact snapshot = runRepository.findArtifact("item-id", EvalRunItemArtifactType.CASE_SNAPSHOT).orElseThrow();
        assertEquals("case-id", snapshot.payload().get("caseId"));
        assertEquals(1, snapshot.payload().get("revision"));
        assertEquals("old-content-hash", snapshot.payload().get("contentHash"));
        assertEquals("v1", snapshot.payload().get("datasetVersion"));
        assertEquals("CASE_REVISION", snapshot.payload().get("caseSnapshotSource"));
    }

    @Test
    void missingPinnedRevisionMarksItemError() {
        RecordingRunRepository runRepository = new RecordingRunRepository();
        runRepository.runs.put("run-id", run());
        runRepository.items.put("item-id", runningItem(7));

        EvalDatasetRepository datasetRepository = mock(EvalDatasetRepository.class);
        when(datasetRepository.findCaseRevision("case-id", 7)).thenReturn(Optional.empty());

        ChatRunQueryService chatRunQueryService = mock(ChatRunQueryService.class);
        when(chatRunQueryService.getStatus("chat-run-id")).thenReturn(completedStatus());
        EvalE2EScoringService scoringService = mock(EvalE2EScoringService.class);

        reconciler(runRepository, datasetRepository, chatRunQueryService, scoringService).reconcileRun("run-id");

        EvalRunItem savedItem = runRepository.items.get("item-id");
        assertEquals(EvalRunItemStatus.ERROR, savedItem.status());
        assertEquals(EvalFailureCode.PINNED_CASE_REVISION_MISSING, savedItem.failureCode());
        EvalRunItemArtifact snapshot = runRepository.findArtifact("item-id", EvalRunItemArtifactType.CASE_SNAPSHOT).orElseThrow();
        assertEquals("MISSING", snapshot.payload().get("status"));
        assertEquals(7, snapshot.payload().get("revision"));
        assertEquals("v1", snapshot.payload().get("datasetVersion"));
        verify(chatRunQueryService, never()).getResult("chat-run-id");
    }

    private EvalChatRunReconciler reconciler(
        EvalRunRepository runRepository,
        EvalDatasetRepository datasetRepository,
        ChatRunQueryService chatRunQueryService,
        EvalE2EScoringService scoringService
    ) {
        return new EvalChatRunReconciler(
            runRepository,
            datasetRepository,
            chatRunQueryService,
            unavailableContextProvider(),
            new EvalStructuredOutputParser(JsonMapper.builder().findAndAddModules().build()),
            new EvalCitationResolver(),
            scoringService,
            new EvalCaseRevisionMapper(),
            new EvalProperties(),
            JsonMapper.builder().findAndAddModules().build(),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<ContextAssemblyQueryService> unavailableContextProvider() {
        ObjectProvider<ContextAssemblyQueryService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }

    private EvalRun run() {
        return new EvalRun(
            "run-id",
            "dataset-id",
            "snapshot-id",
            EvalRunKind.E2E,
            EvalRunStatus.RUNNING,
            null,
            "config-hash",
            NOW,
            null,
            Map.of("datasetVersion", "v1"),
            List.of(),
            NOW,
            NOW
        );
    }

    private EvalRunItem runningItem(int revision) {
        return new EvalRunItem(
            "item-id",
            "run-id",
            "case-id",
            revision,
            "chat-run-id",
            null,
            EvalRunItemStatus.RUNNING,
            null,
            null,
            Map.of("caseKey", "case-key", "caseRevision", revision),
            Map.of(),
            Map.of(),
            NOW,
            NOW
        );
    }

    private ChatRunStatusResponse completedStatus() {
        return new ChatRunStatusResponse("chat-run-id", "COMPLETED", NOW, NOW, null, 10L, null, null, null);
    }

    private ChatExecutionResponse result(String answer) {
        return new ChatExecutionResponse(
            ChatMode.RAG,
            "model",
            "prompt",
            answer,
            "READY",
            NOW.toString(),
            null,
            null,
            null,
            AnswerMode.WITH_QUOTES,
            List.of(),
            List.of(),
            null,
            null,
            List.of(),
            "chat-run-id"
        );
    }

    private ChatRunTraceDetail trace(String rawModelAnswer) {
        return new ChatRunTraceDetail(
            "chat-run-id",
            ChatMode.RAG,
            "COMPLETED",
            "model",
            "model",
            AnswerMode.WITH_QUOTES,
            AnswerMode.WITH_QUOTES,
            "READY",
            NOW,
            NOW,
            null,
            10L,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(),
            rawModelAnswer == null
                ? null
                : new com.example.demo.model.ChatRunOutputTrace(rawModelAnswer, rawModelAnswer, List.of(), null, false, false),
            List.of()
        );
    }

    private EvalCase evalCase(int revision, String question, String acceptedAnswer) {
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
            List.of(acceptedAnswer),
            List.of(acceptedAnswer),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            new EvalCaseOrigin("manual", "seed", null, Map.of()),
            EvalReviewStatus.APPROVED,
            true,
            NOW,
            NOW
        );
    }

    private static final class RecordingRunRepository implements EvalRunRepository {

        private final Map<String, EvalRun> runs = new LinkedHashMap<>();
        private final Map<String, EvalRunItem> items = new LinkedHashMap<>();
        private final List<EvalRunItemArtifact> artifacts = new ArrayList<>();

        @Override
        public List<EvalRun> findRuns() {
            return List.copyOf(runs.values());
        }

        @Override
        public Optional<EvalRun> findRun(String id) {
            return Optional.ofNullable(runs.get(id));
        }

        @Override
        public EvalRun saveRun(EvalRun run) {
            runs.put(run.id(), run);
            return run;
        }

        @Override
        public EvalRunItem saveItem(EvalRunItem item) {
            items.put(item.id(), item);
            return item;
        }

        @Override
        public List<EvalRunItem> findItemsByRunId(String runId) {
            return items.values().stream()
                .filter(item -> runId.equals(item.runId()))
                .toList();
        }

        @Override
        public List<EvalRunItem> findOpenE2EItemsByRunId(String runId) {
            return findItemsByRunId(runId).stream()
                .filter(item -> item.status() == EvalRunItemStatus.RUNNING || item.status() == EvalRunItemStatus.PENDING)
                .toList();
        }

        @Override
        public EvalRunItemArtifact saveArtifact(EvalRunItemArtifact artifact) {
            artifacts.removeIf(existing -> existing.runItemId().equals(artifact.runItemId())
                && existing.artifactType() == artifact.artifactType());
            artifacts.add(artifact);
            return artifact;
        }

        @Override
        public Optional<EvalRunItemArtifact> findArtifact(String itemId, EvalRunItemArtifactType artifactType) {
            return artifacts.stream()
                .filter(artifact -> itemId.equals(artifact.runItemId()) && artifact.artifactType() == artifactType)
                .findFirst();
        }

        @Override
        public boolean isStorageReady() {
            return true;
        }
    }
}
