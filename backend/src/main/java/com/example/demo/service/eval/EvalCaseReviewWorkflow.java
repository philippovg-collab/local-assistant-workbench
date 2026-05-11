package com.example.demo.service.eval;

import com.example.demo.model.eval.CreateEvalCaseReviewRequest;
import com.example.demo.model.eval.EvalCase;
import com.example.demo.model.eval.EvalReviewStatus;
import com.example.demo.model.eval.SubmitEvalCaseReviewRequest;
import com.example.demo.service.eval.port.EvalDatasetRepository;
import java.time.Clock;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class EvalCaseReviewWorkflow {

    private final EvalDatasetRepository repository;
    private final EvalDatasetLifecycleValidator validator;
    private final EvalCaseRevisionService revisionService;
    private final Clock clock;

    public EvalCaseReviewWorkflow(
        EvalDatasetRepository repository,
        EvalDatasetLifecycleValidator validator,
        EvalCaseRevisionService revisionService,
        Clock clock
    ) {
        this.repository = repository;
        this.validator = validator;
        this.revisionService = revisionService;
        this.clock = clock;
    }

    EvalCase submitCaseReview(String id, SubmitEvalCaseReviewRequest request) {
        EvalCase current = validator.requireCase(id);
        validator.validateForReview(current);
        EvalCase saved = repository.saveCase(revisionService.withReviewStatus(current, EvalReviewStatus.READY_FOR_REVIEW, true));
        revisionService.saveRevision(saved, "submitted for review");
        revisionService.saveReview(saved, EvalReviewStatus.READY_FOR_REVIEW, request == null ? null : request.note(), Map.of("action", "submit-review"));
        return saved;
    }

    EvalCase reviewCase(String id, CreateEvalCaseReviewRequest request) {
        EvalCase current = validator.requireCase(id);
        EvalReviewStatus status = validator.requireReviewDecision(request.status());
        if (status == EvalReviewStatus.APPROVED) {
            validator.validateForReview(current);
        }
        EvalCase saved = repository.saveCase(revisionService.withReviewStatus(current, status, true));
        revisionService.saveRevision(saved, "review " + status.name());
        revisionService.saveReview(saved, status, request.note(), Map.of("action", "review"));
        return saved;
    }

    EvalCase archiveCase(String id) {
        EvalCase current = validator.requireCase(id);
        EvalCase archived = new EvalCase(
            current.id(),
            current.datasetId(),
            current.caseKey(),
            current.revision() + 1,
            current.caseType(),
            current.expectedMode(),
            current.severity(),
            current.question(),
            current.knowledgeScope(),
            current.retrievalFilters(),
            current.goldFacts(),
            current.acceptedAnswers(),
            current.goldEvidenceLocators(),
            current.requiredDocGroups(),
            current.forbiddenDocumentRefs(),
            current.tags(),
            current.origin(),
            EvalReviewStatus.ARCHIVED,
            false,
            current.createdAt(),
            clock.instant()
        );
        EvalCase saved = repository.saveCase(archived);
        revisionService.saveRevision(saved, "case archived");
        revisionService.saveReview(saved, EvalReviewStatus.ARCHIVED, "Archived", Map.of("action", "archive"));
        return saved;
    }
}
