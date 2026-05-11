package com.example.demo.service.eval;

import com.example.demo.config.RequestContext;
import com.example.demo.error.ApplicationException;
import com.example.demo.error.ErrorType;
import com.example.demo.model.eval.CreateEvalCandidateFromChatRunRequest;
import com.example.demo.model.eval.CreateEvalCaseRequest;
import com.example.demo.model.eval.CreateEvalDatasetRequest;
import com.example.demo.model.eval.EvalCase;
import com.example.demo.model.eval.EvalCaseOrigin;
import com.example.demo.model.eval.EvalCaseRevision;
import com.example.demo.model.eval.EvalCaseSeverity;
import com.example.demo.model.eval.EvalCaseType;
import com.example.demo.model.eval.EvalDataset;
import com.example.demo.model.eval.EvalDatasetKind;
import com.example.demo.model.eval.EvalLifecycleStatus;
import com.example.demo.model.eval.EvalReviewStatus;
import com.example.demo.model.eval.UpdateEvalCaseRequest;
import com.example.demo.service.eval.port.EvalDatasetRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class EvalCaseMutationService {

    private static final String DEFAULT_CANDIDATE_DATASET_KEY = "candidate-failures";

    private final EvalDatasetRepository repository;
    private final EvalDatasetLifecycleValidator validator;
    private final EvalDatasetMutationService datasetMutationService;
    private final EvalCaseRevisionService revisionService;
    private final Clock clock;

    public EvalCaseMutationService(
        EvalDatasetRepository repository,
        EvalDatasetLifecycleValidator validator,
        EvalDatasetMutationService datasetMutationService,
        EvalCaseRevisionService revisionService,
        Clock clock
    ) {
        this.repository = repository;
        this.validator = validator;
        this.datasetMutationService = datasetMutationService;
        this.revisionService = revisionService;
        this.clock = clock;
    }

    EvalCase createCase(String datasetId, CreateEvalCaseRequest request) {
        EvalDataset dataset = validator.requireDataset(datasetId);
        validator.ensureMutableDataset(dataset);
        Instant now = clock.instant();
        EvalCase evalCase = new EvalCase(
            UUID.randomUUID().toString(),
            dataset.id(),
            validator.normalizedRequired(request.caseKey(), "caseKey"),
            1,
            request.caseType(),
            request.expectedMode(),
            request.severity() == null ? EvalCaseSeverity.MEDIUM : request.severity(),
            validator.normalizedRequired(request.question(), "question"),
            request.knowledgeScope(),
            request.retrievalFilters(),
            request.goldFacts(),
            request.acceptedAnswers(),
            request.goldEvidenceLocators(),
            request.requiredDocGroups(),
            request.forbiddenDocumentRefs(),
            request.tags(),
            request.origin() == null ? new EvalCaseOrigin("manual", actor(), null, Map.of()) : request.origin(),
            EvalReviewStatus.DRAFT,
            true,
            now,
            now
        );
        EvalCase saved = repository.saveCase(evalCase);
        revisionService.saveRevision(saved, "case created");
        return saved;
    }

    EvalCase updateCase(String id, UpdateEvalCaseRequest request) {
        EvalCase current = validator.requireCase(id);
        validator.ensureMutableDataset(validator.requireDataset(current.datasetId()));
        validator.ensureEditableCase(current);
        EvalCase updated = new EvalCase(
            current.id(),
            current.datasetId(),
            current.caseKey(),
            current.revision() + 1,
            request.caseType() == null ? current.caseType() : request.caseType(),
            request.expectedMode() == null ? current.expectedMode() : request.expectedMode(),
            request.severity() == null ? current.severity() : request.severity(),
            request.question() == null ? current.question() : validator.normalizedRequired(request.question(), "question"),
            request.knowledgeScope() == null ? current.knowledgeScope() : request.knowledgeScope(),
            request.retrievalFilters() == null ? current.retrievalFilters() : request.retrievalFilters(),
            request.goldFacts() == null ? current.goldFacts() : request.goldFacts(),
            request.acceptedAnswers() == null ? current.acceptedAnswers() : request.acceptedAnswers(),
            request.goldEvidenceLocators() == null ? current.goldEvidenceLocators() : request.goldEvidenceLocators(),
            request.requiredDocGroups() == null ? current.requiredDocGroups() : request.requiredDocGroups(),
            request.forbiddenDocumentRefs() == null ? current.forbiddenDocumentRefs() : request.forbiddenDocumentRefs(),
            request.tags() == null ? current.tags() : request.tags(),
            request.origin() == null ? current.origin() : request.origin(),
            EvalReviewStatus.DRAFT,
            true,
            current.createdAt(),
            clock.instant()
        );
        EvalCase saved = repository.saveCase(updated);
        revisionService.saveRevision(saved, "case edited");
        return saved;
    }

    List<EvalCaseRevision> listCaseRevisions(String id) {
        validator.requireCase(id);
        return repository.findCaseRevisions(id);
    }

    EvalCase createCandidateFromChatRun(CreateEvalCandidateFromChatRunRequest request) {
        EvalDataset dataset = StringUtils.hasText(request.datasetId())
            ? validator.requireDataset(request.datasetId())
            : defaultCandidateDataset();
        if (dataset.kind() != EvalDatasetKind.CANDIDATE) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
                "eval_candidate.dataset_kind_invalid",
                "Candidate cases must be created in a CANDIDATE dataset"
            );
        }
        String caseKey = StringUtils.hasText(request.caseKey())
            ? request.caseKey().trim()
            : "chat-run-" + request.chatRunId().replace("-", "").substring(0, Math.min(12, request.chatRunId().replace("-", "").length()));
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (StringUtils.hasText(request.failureReason())) {
            metadata.put("failureReason", request.failureReason().trim());
        }
        metadata.put("artifacts", request.artifacts());
        metadata.put("originalSources", request.originalSources());
        EvalCaseOrigin origin = new EvalCaseOrigin("chat_run_failure", request.chatRunId(), request.failureReason(), metadata);
        return createCase(dataset.id(), new CreateEvalCaseRequest(
            caseKey,
            EvalCaseType.EXACT_FACT,
            null,
            EvalCaseSeverity.LOW,
            StringUtils.hasText(request.question()) ? request.question() : "Candidate from chat run " + request.chatRunId(),
            Map.of(),
            Map.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            request.tags(),
            origin
        ));
    }

    private EvalDataset defaultCandidateDataset() {
        return repository.findDatasetSummaries().stream()
            .filter(dataset -> dataset.kind() == EvalDatasetKind.CANDIDATE)
            .filter(dataset -> DEFAULT_CANDIDATE_DATASET_KEY.equals(dataset.datasetKey()))
            .filter(dataset -> dataset.status() != EvalLifecycleStatus.ARCHIVED)
            .findFirst()
            .flatMap(summary -> repository.findDatasetById(summary.id()))
            .orElseGet(() -> datasetMutationService.createDataset(new CreateEvalDatasetRequest(
                DEFAULT_CANDIDATE_DATASET_KEY,
                EvalDatasetKind.CANDIDATE,
                "v1",
                "Candidate failures",
                "Candidate eval cases created from failed chat-run evidence",
                List.of("candidate")
            )));
    }

    private String actor() {
        return RequestContext.currentActor();
    }
}
