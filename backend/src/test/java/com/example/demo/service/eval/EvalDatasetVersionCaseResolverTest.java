package com.example.demo.service.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.demo.error.ApplicationException;
import com.example.demo.model.eval.EvalCase;
import com.example.demo.model.eval.EvalCaseOrigin;
import com.example.demo.model.eval.EvalCaseRevision;
import com.example.demo.model.eval.EvalCaseSeverity;
import com.example.demo.model.eval.EvalCaseType;
import com.example.demo.model.eval.EvalDatasetVersion;
import com.example.demo.model.eval.EvalExpectedMode;
import com.example.demo.model.eval.EvalReviewStatus;
import com.example.demo.service.eval.EvalDatasetVersionCaseResolver.ResolvedCases;
import com.example.demo.service.eval.port.EvalDatasetRepository;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EvalDatasetVersionCaseResolverTest {

    private final EvalDatasetRepository repository = mock(EvalDatasetRepository.class);
    private final EvalDatasetVersionCaseResolver resolver = new EvalDatasetVersionCaseResolver(
        repository,
        new EvalHashService(JsonMapper.builder().findAndAddModules().build())
    );

    @Test
    void resolvesExactOldRevisionAfterLiveCaseUpdate() {
        EvalCase oldRevision = evalCase("case-a", "case-a-key", 1, "Old question?", EvalReviewStatus.APPROVED, true);
        when(repository.findDatasetVersion("dataset-id", "v1")).thenReturn(Optional.of(version(
            ref("case-a", "case-a-key", 1, EvalReviewStatus.APPROVED, "old-hash")
        )));
        when(repository.findCaseRevisionsByRefs(anyList())).thenReturn(List.of(revision(oldRevision, "old-hash")));

        ResolvedCases resolved = resolver.resolve("dataset-id", "v1", List.of(), 50, true);

        assertEquals(1, resolved.cases().size());
        assertEquals("Old question?", resolved.cases().getFirst().caseSnapshot().question());
        assertEquals(1, resolved.cases().getFirst().caseSnapshot().revision());
        assertEquals("old-hash", resolved.cases().getFirst().contentHash());
    }

    @Test
    void resolvesArchivedLiveCaseWhenPinnedInVersion() {
        EvalCase pinnedApprovedRevision = evalCase("case-a", "case-a-key", 1, "Archived later?", EvalReviewStatus.APPROVED, true);
        when(repository.findDatasetVersion("dataset-id", "v1")).thenReturn(Optional.of(version(
            ref("case-a", "case-a-key", 1, EvalReviewStatus.APPROVED, "hash")
        )));
        when(repository.findCaseRevisionsByRefs(anyList())).thenReturn(List.of(revision(pinnedApprovedRevision, "hash")));

        ResolvedCases resolved = resolver.resolve("dataset-id", "v1", List.of("case-a-key"), 50, true);

        assertEquals("case-a", resolved.cases().getFirst().caseSnapshot().id());
        assertEquals(true, resolved.cases().getFirst().caseSnapshot().active());
    }

    @Test
    void preservesRefOrderAndFiltersByCaseIdOrCaseKey() {
        EvalCase first = evalCase("case-a", "alpha", 1, "Alpha?", EvalReviewStatus.APPROVED, true);
        EvalCase second = evalCase("case-b", "beta", 2, "Beta?", EvalReviewStatus.APPROVED, true);
        when(repository.findDatasetVersion("dataset-id", "v1")).thenReturn(Optional.of(version(
            ref("case-b", "beta", 2, EvalReviewStatus.APPROVED, "hash-b"),
            ref("case-a", "alpha", 1, EvalReviewStatus.APPROVED, "hash-a")
        )));
        when(repository.findCaseRevisionsByRefs(anyList())).thenReturn(List.of(
            revision(first, "hash-a"),
            revision(second, "hash-b")
        ));

        ResolvedCases resolved = resolver.resolve("dataset-id", "v1", List.of("case-a", "beta"), 50, true);

        assertEquals(List.of("case-b", "case-a"), resolved.cases().stream()
            .map(resolvedCase -> resolvedCase.caseSnapshot().id())
            .toList());
    }

    @Test
    void failsWhenRequestedCaseIsNotInVersion() {
        when(repository.findDatasetVersion("dataset-id", "v1")).thenReturn(Optional.of(version(
            ref("case-a", "alpha", 1, EvalReviewStatus.APPROVED, "hash-a")
        )));

        ApplicationException exception = assertThrows(ApplicationException.class, () ->
            resolver.resolve("dataset-id", "v1", List.of("missing-case"), 50, true)
        );

        assertEquals("eval_run.case_not_in_dataset_version", exception.getCode());
    }

    @Test
    void failsWhenPinnedRevisionIsMissing() {
        when(repository.findDatasetVersion("dataset-id", "v1")).thenReturn(Optional.of(version(
            ref("case-a", "alpha", 1, EvalReviewStatus.APPROVED, "hash-a")
        )));
        when(repository.findCaseRevisionsByRefs(anyList())).thenReturn(List.of());

        ApplicationException exception = assertThrows(ApplicationException.class, () ->
            resolver.resolve("dataset-id", "v1", List.of(), 50, true)
        );

        assertEquals("eval_dataset_version.case_revision_missing", exception.getCode());
    }

    @Test
    void rejectsNonApprovedRefsWhenRequestedByCaller() {
        when(repository.findDatasetVersion("dataset-id", "v1")).thenReturn(Optional.of(version(
            ref("case-a", "alpha", 1, EvalReviewStatus.DRAFT, "hash-a")
        )));

        ApplicationException exception = assertThrows(ApplicationException.class, () ->
            resolver.resolve("dataset-id", "v1", List.of(), 50, false)
        );

        assertEquals("eval_dataset_version.unapproved_cases", exception.getCode());
    }

    private EvalDatasetVersion version(Map<String, Object>... refs) {
        return new EvalDatasetVersion(
            "version-id",
            "dataset-id",
            "v1",
            "dataset-hash",
            refs.length,
            List.of(refs),
            "tester",
            null,
            Instant.parse("2026-05-10T00:00:00Z")
        );
    }

    private Map<String, Object> ref(
        String caseId,
        String caseKey,
        int revision,
        EvalReviewStatus reviewStatus,
        String contentHash
    ) {
        return Map.of(
            "caseId", caseId,
            "caseKey", caseKey,
            "revision", revision,
            "reviewStatus", reviewStatus.name(),
            "contentHash", contentHash
        );
    }

    private EvalCaseRevision revision(EvalCase evalCase, String contentHash) {
        return new EvalCaseRevision(
            "revision-" + evalCase.id() + "-" + evalCase.revision(),
            evalCase,
            contentHash,
            "tester",
            Instant.parse("2026-05-10T00:00:00Z")
        );
    }

    private EvalCase evalCase(
        String id,
        String caseKey,
        int revision,
        String question,
        EvalReviewStatus reviewStatus,
        boolean active
    ) {
        return new EvalCase(
            id,
            "dataset-id",
            caseKey,
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
            List.of("core"),
            new EvalCaseOrigin("manual", "seed", null, Map.of()),
            reviewStatus,
            active,
            Instant.parse("2026-05-10T00:00:00Z"),
            Instant.parse("2026-05-10T00:00:00Z")
        );
    }

}
