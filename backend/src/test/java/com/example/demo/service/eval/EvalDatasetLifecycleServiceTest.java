package com.example.demo.service.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.error.ApplicationException;
import com.example.demo.model.EvidenceLocator;
import com.example.demo.model.eval.CreateEvalDatasetVersionRequest;
import com.example.demo.model.eval.EvalCase;
import com.example.demo.model.eval.EvalCaseOrigin;
import com.example.demo.model.eval.EvalCaseRevision;
import com.example.demo.model.eval.EvalCaseSeverity;
import com.example.demo.model.eval.EvalCaseType;
import com.example.demo.model.eval.EvalDataset;
import com.example.demo.model.eval.EvalDatasetKind;
import com.example.demo.model.eval.EvalDatasetVersion;
import com.example.demo.model.eval.EvalExpectedMode;
import com.example.demo.model.eval.EvalLifecycleStatus;
import com.example.demo.model.eval.EvalReviewStatus;
import com.example.demo.model.eval.PromoteEvalCaseRequest;
import com.example.demo.model.eval.UpdateEvalCaseRequest;
import com.example.demo.service.eval.port.EvalDatasetRepository;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EvalDatasetLifecycleServiceTest {

    private final EvalDatasetRepository repository = mock(EvalDatasetRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-05-10T00:00:00Z"), ZoneOffset.UTC);
    private final EvalHashService hashService = new EvalHashService(JsonMapper.builder().findAndAddModules().build());
    private final EvalDatasetLifecycleValidator validator = new EvalDatasetLifecycleValidator(
        repository,
        new EvalCaseValidationService()
    );
    private final EvalDatasetVersioningService versioningService = new EvalDatasetVersioningService(
        repository,
        validator,
        hashService,
        clock
    );
    private final EvalDatasetMutationService datasetMutationService = new EvalDatasetMutationService(
        repository,
        validator,
        versioningService,
        clock
    );
    private final EvalCaseRevisionService revisionService = new EvalCaseRevisionService(
        repository,
        hashService,
        validator,
        clock
    );
    private final EvalCaseMutationService caseMutationService = new EvalCaseMutationService(
        repository,
        validator,
        datasetMutationService,
        revisionService,
        clock
    );
    private final EvalDatasetLifecycleService service = new EvalDatasetLifecycleService(
        datasetMutationService,
        versioningService,
        caseMutationService,
        new EvalCaseReviewWorkflow(repository, validator, revisionService, clock),
        new EvalCasePromotionService(repository, validator, versioningService, revisionService, clock)
    );

    @Test
    void createsDatasetVersionFromActiveCaseRevisionsAndAdvancesDatasetVersion() {
        EvalDataset dataset = dataset("dataset-id", EvalDatasetKind.GOLDEN, "v1");
        EvalCase evalCase = caseWithStatus(EvalReviewStatus.APPROVED);
        when(repository.findDatasetById("dataset-id")).thenReturn(Optional.of(dataset));
        when(repository.findDatasetVersions("dataset-id")).thenReturn(List.of(new EvalDatasetVersion(
            "version-1",
            "dataset-id",
            "v1",
            "hash",
            0,
            List.of(),
            "tester",
            null,
            Instant.parse("2026-05-10T00:00:00Z")
        )));
        when(repository.findCasesByDatasetId("dataset-id")).thenReturn(List.of(evalCase));
        when(repository.findCaseRevisionsByRefs(any())).thenReturn(List.of(revision(evalCase, "case-hash-1")));
        when(repository.saveDatasetVersion(any(EvalDatasetVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.saveDataset(any(EvalDataset.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EvalDatasetVersion version = service.createDatasetVersion("dataset-id", new CreateEvalDatasetVersionRequest(null, "release"));

        assertEquals("v2", version.version());
        assertEquals(1, version.caseCount());
        assertFalse(version.datasetHash().isBlank());
        assertEquals("case-1", version.caseRevisionRefs().getFirst().get("caseKey"));
        assertEquals("case-hash-1", version.caseRevisionRefs().getFirst().get("contentHash"));
        verify(repository).saveDataset(any(EvalDataset.class));
    }

    @Test
    void goldenVersionRejectsUnapprovedCases() {
        for (EvalReviewStatus status : List.of(EvalReviewStatus.DRAFT, EvalReviewStatus.READY_FOR_REVIEW, EvalReviewStatus.REJECTED)) {
            EvalDataset dataset = dataset("dataset-id-" + status.name(), EvalDatasetKind.GOLDEN, "v1");
            EvalCase evalCase = caseWithStatus(status);
            when(repository.findDatasetById(dataset.id())).thenReturn(Optional.of(dataset));
            when(repository.findCasesByDatasetId(dataset.id())).thenReturn(List.of(evalCase));

            ApplicationException exception = assertThrows(ApplicationException.class, () ->
                service.createDatasetVersion(dataset.id(), new CreateEvalDatasetVersionRequest("v2", "release"))
            );

            assertEquals("eval_dataset_version.unapproved_cases", exception.getCode());
        }
    }

    @Test
    void smokeVersionRejectsUnapprovedCases() {
        EvalDataset dataset = dataset("dataset-id", EvalDatasetKind.SMOKE, "v1");
        when(repository.findDatasetById("dataset-id")).thenReturn(Optional.of(dataset));
        when(repository.findCasesByDatasetId("dataset-id")).thenReturn(List.of(caseWithStatus(EvalReviewStatus.DRAFT)));

        ApplicationException exception = assertThrows(ApplicationException.class, () ->
            service.createDatasetVersion("dataset-id", new CreateEvalDatasetVersionRequest("v2", "release"))
        );

        assertEquals("eval_dataset_version.unapproved_cases", exception.getCode());
    }

    @Test
    void candidateVersionAllowsDraftCase() {
        EvalDataset dataset = dataset("dataset-id", EvalDatasetKind.CANDIDATE, "v1");
        EvalCase draftCase = caseWithStatus(EvalReviewStatus.DRAFT);
        when(repository.findDatasetById("dataset-id")).thenReturn(Optional.of(dataset));
        when(repository.findCasesByDatasetId("dataset-id")).thenReturn(List.of(draftCase));
        when(repository.findCaseRevisionsByRefs(any())).thenReturn(List.of(revision(draftCase, "draft-hash")));
        when(repository.saveDatasetVersion(any(EvalDatasetVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.saveDataset(any(EvalDataset.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EvalDatasetVersion version = service.createDatasetVersion("dataset-id", new CreateEvalDatasetVersionRequest("v2", "candidate"));

        assertEquals(1, version.caseCount());
        assertEquals(EvalReviewStatus.DRAFT.name(), version.caseRevisionRefs().getFirst().get("reviewStatus"));
        assertEquals("draft-hash", version.caseRevisionRefs().getFirst().get("contentHash"));
    }

    @Test
    void editingCaseCreatesDraftRevision() {
        EvalCase current = caseWithStatus(EvalReviewStatus.APPROVED);
        when(repository.findCaseById("case-id")).thenReturn(Optional.of(current));
        when(repository.findDatasetById("dataset-id")).thenReturn(Optional.of(dataset("dataset-id", EvalDatasetKind.GOLDEN, "v1")));
        when(repository.saveCase(any(EvalCase.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repository.saveCaseRevision(any(EvalCaseRevision.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EvalCase updated = service.updateCase("case-id", new UpdateEvalCaseRequest(
            null,
            null,
            null,
            "Updated question?",
            null,
            null,
            null,
            List.of("ten"),
            null,
            null,
            null,
            null,
            null
        ));

        assertEquals(2, updated.revision());
        assertEquals(EvalReviewStatus.DRAFT, updated.reviewStatus());
        assertEquals(List.of("ten"), updated.acceptedAnswers());
    }

    @Test
    void promotionRequiresApprovedCandidate() {
        EvalCase draftCandidate = caseWithStatus(EvalReviewStatus.DRAFT);
        when(repository.findCaseById("case-id")).thenReturn(Optional.of(draftCandidate));
        when(repository.findDatasetById("dataset-id")).thenReturn(Optional.of(dataset("dataset-id", EvalDatasetKind.CANDIDATE, "v1")));
        when(repository.findDatasetById("target-id")).thenReturn(Optional.of(dataset("target-id", EvalDatasetKind.GOLDEN, "v1")));

        ApplicationException exception = assertThrows(ApplicationException.class, () ->
            service.promoteCase("case-id", new PromoteEvalCaseRequest("target-id", null, null))
        );

        assertEquals("eval.promotion_requires_approved_candidate", exception.getCode());
    }

    @Test
    void approvingCaseValidatesGoldMarkup() {
        EvalCase incomplete = new EvalCase(
            "case-id",
            "dataset-id",
            "case-1",
            1,
            EvalCaseType.EXACT_FACT,
            EvalExpectedMode.ANSWER,
            EvalCaseSeverity.BLOCKER,
            "What is the limit?",
            Map.of(),
            Map.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            new EvalCaseOrigin("manual", "seed", null, Map.of()),
            EvalReviewStatus.READY_FOR_REVIEW,
            true,
            Instant.parse("2026-05-10T00:00:00Z"),
            Instant.parse("2026-05-10T00:00:00Z")
        );
        when(repository.findCaseById("case-id")).thenReturn(Optional.of(incomplete));

        ApplicationException exception = assertThrows(ApplicationException.class, () ->
            service.reviewCase("case-id", new com.example.demo.model.eval.CreateEvalCaseReviewRequest(EvalReviewStatus.APPROVED, "ok"))
        );

        assertEquals("eval_case.validation_failed", exception.getCode());
    }

    private EvalDataset dataset(String id, EvalDatasetKind kind, String version) {
        return new EvalDataset(
            id,
            kind.name().toLowerCase() + "-core",
            kind,
            version,
            EvalLifecycleStatus.ACTIVE,
            kind.name() + " core",
            null,
            List.of("core"),
            Instant.parse("2026-05-10T00:00:00Z"),
            Instant.parse("2026-05-10T00:00:00Z")
        );
    }

    private EvalCase caseWithStatus(EvalReviewStatus status) {
        return new EvalCase(
            "case-id",
            "dataset-id",
            "case-1",
            1,
            EvalCaseType.EXACT_FACT,
            EvalExpectedMode.ANSWER,
            EvalCaseSeverity.BLOCKER,
            "What is the limit?",
            Map.of(),
            Map.of("versionLabel", "v1"),
            List.of("limit is 10"),
            List.of("10"),
            List.of(new EvidenceLocator(
                "source-DOC-1",
                "material-DOC-1",
                "DOC-1",
                "v1",
                null,
                1,
                0,
                1,
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null
            )),
            List.of(),
            List.of(),
            List.of("core"),
            new EvalCaseOrigin("manual", "seed", null, Map.of()),
            status,
            true,
            Instant.parse("2026-05-10T00:00:00Z"),
            Instant.parse("2026-05-10T00:00:00Z")
        );
    }

    private EvalCaseRevision revision(EvalCase evalCase, String contentHash) {
        return new EvalCaseRevision(
            "revision-" + evalCase.id(),
            evalCase,
            contentHash,
            "tester",
            Instant.parse("2026-05-10T00:00:00Z")
        );
    }
}
