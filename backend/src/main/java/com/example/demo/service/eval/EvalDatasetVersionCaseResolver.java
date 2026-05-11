package com.example.demo.service.eval;

import com.example.demo.error.ApplicationException;
import com.example.demo.error.ErrorType;
import com.example.demo.model.eval.EvalCase;
import com.example.demo.model.eval.EvalCaseRevision;
import com.example.demo.model.eval.EvalDataset;
import com.example.demo.model.eval.EvalDatasetVersion;
import com.example.demo.model.eval.EvalDatasetVersionCaseRef;
import com.example.demo.model.eval.EvalReviewStatus;
import com.example.demo.service.eval.port.EvalDatasetRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class EvalDatasetVersionCaseResolver {

    private final EvalDatasetRepository repository;
    private final EvalHashService hashService;

    public EvalDatasetVersionCaseResolver(EvalDatasetRepository repository, EvalHashService hashService) {
        this.repository = repository;
        this.hashService = hashService;
    }

    public ResolvedCases resolve(
        String datasetId,
        String datasetVersion,
        List<String> caseIds,
        Integer limit
    ) {
        return resolve(datasetId, datasetVersion, caseIds, limit, true);
    }

    public ResolvedCases resolve(
        String datasetId,
        String datasetVersion,
        List<String> caseIds,
        Integer limit,
        boolean allowNonApproved
    ) {
        String normalizedDatasetId = requiredDatasetId(datasetId);
        String normalizedDatasetVersion = StringUtils.hasText(datasetVersion)
            ? datasetVersion.trim()
            : currentDatasetVersion(normalizedDatasetId);
        EvalDatasetVersion version = repository.findDatasetVersion(normalizedDatasetId, normalizedDatasetVersion)
            .orElseThrow(() -> new ApplicationException(
                ErrorType.NOT_FOUND,
                "eval_dataset_version.not_found",
                "Eval dataset version '" + normalizedDatasetVersion + "' does not exist for dataset '" + normalizedDatasetId + "'"
            ));

        List<EvalDatasetVersionCaseRef> refs = refs(version);
        if (!allowNonApproved && refs.stream().anyMatch(ref -> ref.reviewStatus() != EvalReviewStatus.APPROVED)) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
                "eval_dataset_version.unapproved_cases",
                "Dataset version contains non-approved eval cases"
            );
        }

        List<EvalDatasetVersionCaseRef> matchingRefs = filterRefs(refs, requestedCaseIds(caseIds));
        List<EvalDatasetVersionCaseRef> limitedRefs = applyLimit(matchingRefs, limit);
        List<ResolvedCase> resolvedCases = resolveRevisions(normalizedDatasetId, limitedRefs);
        String caseRevisionRefHash = hashService.hash(resolvedCases.stream()
            .map(resolvedCase -> resolvedCase.ref().toMap())
            .toList());
        return new ResolvedCases(version, caseRevisionRefHash, resolvedCases);
    }

    private String requiredDatasetId(String datasetId) {
        if (!StringUtils.hasText(datasetId)) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
                "eval_dataset.dataset_required",
                "Field 'datasetId' is required"
            );
        }
        return datasetId.trim();
    }

    private String currentDatasetVersion(String datasetId) {
        EvalDataset dataset = repository.findDatasetById(datasetId)
            .orElseThrow(() -> new ApplicationException(
                ErrorType.NOT_FOUND,
                "eval_dataset.not_found",
                "Eval dataset '" + datasetId + "' does not exist"
            ));
        return dataset.version();
    }

    private List<EvalDatasetVersionCaseRef> refs(EvalDatasetVersion version) {
        List<EvalDatasetVersionCaseRef> refs = new ArrayList<>();
        for (Map<String, Object> rawRef : version.caseRevisionRefs()) {
            try {
                refs.add(EvalDatasetVersionCaseRef.fromMap(rawRef));
            } catch (RuntimeException exception) {
                throw new ApplicationException(
                    ErrorType.CONFLICT,
                    "eval_dataset_version.invalid_case_ref",
                    "Dataset version '" + version.version() + "' contains an invalid case revision reference",
                    exception
                );
            }
        }
        return List.copyOf(refs);
    }

    private Set<String> requestedCaseIds(List<String> caseIds) {
        if (caseIds == null || caseIds.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> requested = new LinkedHashSet<>();
        caseIds.stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .forEach(requested::add);
        return requested;
    }

    private List<EvalDatasetVersionCaseRef> filterRefs(
        List<EvalDatasetVersionCaseRef> refs,
        Set<String> requestedCaseIds
    ) {
        if (requestedCaseIds.isEmpty()) {
            return refs;
        }
        List<EvalDatasetVersionCaseRef> matchingRefs = refs.stream()
            .filter(ref -> requestedCaseIds.contains(ref.caseId()) || requestedCaseIds.contains(ref.caseKey()))
            .toList();
        LinkedHashSet<String> missing = new LinkedHashSet<>(requestedCaseIds);
        for (EvalDatasetVersionCaseRef ref : matchingRefs) {
            missing.remove(ref.caseId());
            missing.remove(ref.caseKey());
        }
        if (!missing.isEmpty()) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
                "eval_run.case_not_in_dataset_version",
                "Requested eval case is not present in the selected dataset version: " + String.join(", ", missing)
            );
        }
        return matchingRefs;
    }

    private List<EvalDatasetVersionCaseRef> applyLimit(List<EvalDatasetVersionCaseRef> refs, Integer limit) {
        if (limit == null || limit < 1 || refs.size() <= limit) {
            return refs;
        }
        return refs.subList(0, limit);
    }

    private List<ResolvedCase> resolveRevisions(String datasetId, List<EvalDatasetVersionCaseRef> refs) {
        if (refs.isEmpty()) {
            return List.of();
        }
        Map<String, EvalCaseRevision> revisionsByKey = new LinkedHashMap<>();
        for (EvalCaseRevision revision : repository.findCaseRevisionsByRefs(refs)) {
            revisionsByKey.put(key(revision.caseSnapshot().id(), revision.caseSnapshot().revision()), revision);
        }
        List<ResolvedCase> resolvedCases = new ArrayList<>();
        for (EvalDatasetVersionCaseRef ref : refs) {
            EvalCaseRevision revision = revisionsByKey.get(key(ref.caseId(), ref.revision()));
            if (revision == null) {
                throw new ApplicationException(
                    ErrorType.CONFLICT,
                    "eval_dataset_version.case_revision_missing",
                    "Dataset version points to missing eval case revision '" + ref.caseId() + "#" + ref.revision() + "'"
                );
            }
            EvalCase caseSnapshot = revision.caseSnapshot();
            if (!datasetId.equals(caseSnapshot.datasetId())
                || !ref.caseId().equals(caseSnapshot.id())
                || ref.revision() != caseSnapshot.revision()) {
                throw new ApplicationException(
                    ErrorType.CONFLICT,
                    "eval_dataset_version.case_revision_mismatch",
                    "Dataset version points to an eval case revision that does not match its pinned reference"
                );
            }
            resolvedCases.add(new ResolvedCase(ref.withContentHash(contentHash(ref, revision)), revision));
        }
        return List.copyOf(resolvedCases);
    }

    private String contentHash(EvalDatasetVersionCaseRef ref, EvalCaseRevision revision) {
        return StringUtils.hasText(ref.contentHash()) ? ref.contentHash() : revision.contentHash();
    }

    private String key(String caseId, int revision) {
        return caseId + ":" + revision;
    }

    public record ResolvedCases(
        EvalDatasetVersion datasetVersion,
        String caseRevisionRefHash,
        List<ResolvedCase> cases
    ) {
        public ResolvedCases {
            cases = cases == null ? List.of() : List.copyOf(cases);
        }
    }

    public record ResolvedCase(
        EvalDatasetVersionCaseRef ref,
        EvalCaseRevision revision
    ) {
        public EvalCase caseSnapshot() {
            return revision.caseSnapshot();
        }

        public String contentHash() {
            return StringUtils.hasText(ref.contentHash()) ? ref.contentHash() : revision.contentHash();
        }
    }
}
