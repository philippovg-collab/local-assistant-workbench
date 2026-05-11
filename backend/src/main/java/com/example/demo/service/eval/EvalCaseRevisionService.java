package com.example.demo.service.eval;

import com.example.demo.config.RequestContext;
import com.example.demo.model.eval.EvalCase;
import com.example.demo.model.eval.EvalCaseReview;
import com.example.demo.model.eval.EvalCaseRevision;
import com.example.demo.model.eval.EvalReviewStatus;
import com.example.demo.service.eval.port.EvalDatasetRepository;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class EvalCaseRevisionService {

    private final EvalDatasetRepository repository;
    private final EvalHashService hashService;
    private final EvalDatasetLifecycleValidator validator;
    private final Clock clock;

    public EvalCaseRevisionService(
        EvalDatasetRepository repository,
        EvalHashService hashService,
        EvalDatasetLifecycleValidator validator,
        Clock clock
    ) {
        this.repository = repository;
        this.hashService = hashService;
        this.validator = validator;
        this.clock = clock;
    }

    EvalCase withReviewStatus(EvalCase current, EvalReviewStatus status, boolean active) {
        return new EvalCase(
            current.id(),
            current.datasetId(),
            current.caseKey(),
            current.revision(),
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
            status,
            active,
            current.createdAt(),
            clock.instant()
        );
    }

    void saveRevision(EvalCase evalCase, String reason) {
        Map<String, Object> fingerprint = new LinkedHashMap<>();
        fingerprint.put("caseKey", evalCase.caseKey());
        fingerprint.put("revision", evalCase.revision());
        fingerprint.put("caseType", evalCase.caseType());
        fingerprint.put("expectedMode", evalCase.expectedMode());
        fingerprint.put("severity", evalCase.severity());
        fingerprint.put("question", evalCase.question());
        fingerprint.put("knowledgeScope", evalCase.knowledgeScope());
        fingerprint.put("retrievalFilters", evalCase.retrievalFilters());
        fingerprint.put("goldFacts", evalCase.goldFacts());
        fingerprint.put("acceptedAnswers", evalCase.acceptedAnswers());
        fingerprint.put("goldEvidenceLocators", evalCase.goldEvidenceLocators());
        fingerprint.put("requiredDocGroups", evalCase.requiredDocGroups());
        fingerprint.put("forbiddenDocumentRefs", evalCase.forbiddenDocumentRefs());
        fingerprint.put("tags", evalCase.tags());
        fingerprint.put("reviewStatus", evalCase.reviewStatus());
        fingerprint.put("active", evalCase.active());
        repository.saveCaseRevision(new EvalCaseRevision(
            UUID.randomUUID().toString(),
            evalCase,
            hashService.hash(fingerprint),
            actor() + " - " + reason,
            clock.instant()
        ));
    }

    void saveReview(EvalCase evalCase, EvalReviewStatus status, String note, Map<String, Object> metadata) {
        repository.saveReview(new EvalCaseReview(
            UUID.randomUUID().toString(),
            evalCase.id(),
            evalCase.revision(),
            status,
            actor(),
            validator.normalizeOptional(note),
            metadata,
            clock.instant(),
            clock.instant()
        ));
    }

    private String actor() {
        return RequestContext.currentActor();
    }
}
