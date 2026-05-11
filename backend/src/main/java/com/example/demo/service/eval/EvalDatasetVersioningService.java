package com.example.demo.service.eval;

import com.example.demo.config.RequestContext;
import com.example.demo.error.ApplicationException;
import com.example.demo.error.ErrorType;
import com.example.demo.model.eval.CreateEvalDatasetVersionRequest;
import com.example.demo.model.eval.EvalCase;
import com.example.demo.model.eval.EvalCaseRevision;
import com.example.demo.model.eval.EvalDataset;
import com.example.demo.model.eval.EvalDatasetVersion;
import com.example.demo.model.eval.EvalDatasetVersionCaseRef;
import com.example.demo.service.eval.port.EvalDatasetRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class EvalDatasetVersioningService {

    private final EvalDatasetRepository repository;
    private final EvalDatasetLifecycleValidator validator;
    private final EvalHashService hashService;
    private final Clock clock;

    public EvalDatasetVersioningService(
        EvalDatasetRepository repository,
        EvalDatasetLifecycleValidator validator,
        EvalHashService hashService,
        Clock clock
    ) {
        this.repository = repository;
        this.validator = validator;
        this.hashService = hashService;
        this.clock = clock;
    }

    EvalDatasetVersion createDatasetVersion(String datasetId, CreateEvalDatasetVersionRequest request) {
        EvalDataset dataset = validator.requireDataset(datasetId);
        String version = StringUtils.hasText(request.version())
            ? request.version().trim()
            : nextDatasetVersion(dataset);
        List<EvalCase> activeCases = repository.findCasesByDatasetId(datasetId).stream()
            .filter(EvalCase::active)
            .toList();
        validator.ensureReleaseVersionAllowed(dataset, activeCases);
        List<Map<String, Object>> refs = caseRevisionRefs(activeCases);
        String hash = hashService.hash(Map.of(
            "datasetId", dataset.id(),
            "datasetKey", dataset.datasetKey(),
            "kind", dataset.kind(),
            "version", version,
            "caseRevisionRefs", refs
        ));
        EvalDatasetVersion saved = repository.saveDatasetVersion(new EvalDatasetVersion(
            UUID.randomUUID().toString(),
            dataset.id(),
            version,
            hash,
            refs.size(),
            refs,
            actor(),
            validator.normalizeOptional(request.note()),
            clock.instant()
        ));
        if (!version.equals(dataset.version())) {
            repository.saveDataset(new EvalDataset(
                dataset.id(),
                dataset.datasetKey(),
                dataset.kind(),
                version,
                dataset.status(),
                dataset.name(),
                dataset.description(),
                dataset.tags(),
                dataset.createdAt(),
                clock.instant()
            ));
        }
        return saved;
    }

    List<EvalDatasetVersion> listDatasetVersions(String datasetId) {
        validator.requireDataset(datasetId);
        return repository.findDatasetVersions(datasetId);
    }

    private List<Map<String, Object>> caseRevisionRefs(List<EvalCase> cases) {
        List<EvalDatasetVersionCaseRef> preliminaryRefs = cases.stream()
            .map(evalCase -> new EvalDatasetVersionCaseRef(
                evalCase.id(),
                evalCase.caseKey(),
                evalCase.revision(),
                evalCase.reviewStatus(),
                null
            ))
            .toList();
        Map<String, EvalCaseRevision> revisionsByKey = new LinkedHashMap<>();
        for (EvalCaseRevision revision : repository.findCaseRevisionsByRefs(preliminaryRefs)) {
            revisionsByKey.put(revision.caseSnapshot().id() + ":" + revision.caseSnapshot().revision(), revision);
        }
        List<Map<String, Object>> refs = new ArrayList<>();
        for (EvalCase evalCase : cases) {
            EvalCaseRevision revision = revisionsByKey.get(evalCase.id() + ":" + evalCase.revision());
            if (revision == null) {
                throw new ApplicationException(
                    ErrorType.CONFLICT,
                    "eval_dataset_version.case_revision_missing",
                    "Cannot create dataset version because case revision '" + evalCase.id() + "#" + evalCase.revision() + "' is missing"
                );
            }
            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("caseId", evalCase.id());
            ref.put("caseKey", evalCase.caseKey());
            ref.put("revision", evalCase.revision());
            ref.put("reviewStatus", evalCase.reviewStatus().name());
            ref.put("contentHash", revision.contentHash());
            ref.put("severity", evalCase.severity().name());
            refs.add(ref);
        }
        return List.copyOf(refs);
    }

    private String nextDatasetVersion(EvalDataset dataset) {
        if (repository.findDatasetVersions(dataset.id()).isEmpty()) {
            return dataset.version();
        }
        return nextVersion(dataset.version());
    }

    private String nextVersion(String currentVersion) {
        if (StringUtils.hasText(currentVersion) && currentVersion.matches("v\\d+")) {
            int current = Integer.parseInt(currentVersion.substring(1));
            return "v" + (current + 1);
        }
        return "v" + clock.instant().getEpochSecond();
    }

    private String actor() {
        return RequestContext.currentActor();
    }
}
