package com.example.demo.service.eval;

import com.example.demo.config.RequestContext;
import com.example.demo.model.eval.CreateEvalDatasetVersionRequest;
import com.example.demo.model.eval.EvalCase;
import com.example.demo.model.eval.EvalCaseOrigin;
import com.example.demo.model.eval.EvalCasePromotion;
import com.example.demo.model.eval.EvalDataset;
import com.example.demo.model.eval.EvalDatasetVersion;
import com.example.demo.model.eval.EvalReviewStatus;
import com.example.demo.model.eval.PromoteEvalCaseRequest;
import com.example.demo.service.eval.port.EvalDatasetRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class EvalCasePromotionService {

    private final EvalDatasetRepository repository;
    private final EvalDatasetLifecycleValidator validator;
    private final EvalDatasetVersioningService versioningService;
    private final EvalCaseRevisionService revisionService;
    private final Clock clock;

    public EvalCasePromotionService(
        EvalDatasetRepository repository,
        EvalDatasetLifecycleValidator validator,
        EvalDatasetVersioningService versioningService,
        EvalCaseRevisionService revisionService,
        Clock clock
    ) {
        this.repository = repository;
        this.validator = validator;
        this.versioningService = versioningService;
        this.revisionService = revisionService;
        this.clock = clock;
    }

    EvalCasePromotion promoteCase(String id, PromoteEvalCaseRequest request) {
        EvalCase source = validator.requireCase(id);
        EvalDataset sourceDataset = validator.requireDataset(source.datasetId());
        EvalDataset targetDataset = validator.requireDataset(request.targetDatasetId());
        validator.ensureCandidatePromotionAllowed(source, sourceDataset, targetDataset);

        EvalCase target = targetCaseFrom(source, targetDataset);
        EvalCase savedTarget = repository.saveCase(target);
        revisionService.saveRevision(savedTarget, "promoted from " + source.id() + " revision " + source.revision());
        EvalDatasetVersion version = versioningService.createDatasetVersion(targetDataset.id(), new CreateEvalDatasetVersionRequest(
            request.version(),
            request.note()
        ));
        EvalCasePromotion promotion = repository.savePromotion(new EvalCasePromotion(
            UUID.randomUUID().toString(),
            source.id(),
            source.revision(),
            targetDataset.id(),
            savedTarget.id(),
            savedTarget.revision(),
            version.id(),
            actor(),
            validator.normalizeOptional(request.note()),
            Map.of("targetDatasetVersion", version.version()),
            clock.instant()
        ));
        EvalCase promotedSource = revisionService.withReviewStatus(source, EvalReviewStatus.PROMOTED, true);
        repository.saveCase(promotedSource);
        revisionService.saveReview(promotedSource, EvalReviewStatus.PROMOTED, request.note(), Map.of("promotionId", promotion.id()));
        return promotion;
    }

    private EvalCase targetCaseFrom(EvalCase source, EvalDataset targetDataset) {
        EvalCase existing = repository.findCasesByDatasetId(targetDataset.id()).stream()
            .filter(candidate -> source.caseKey().equals(candidate.caseKey()))
            .findFirst()
            .orElse(null);
        Instant now = clock.instant();
        return new EvalCase(
            existing == null ? UUID.randomUUID().toString() : existing.id(),
            targetDataset.id(),
            source.caseKey(),
            existing == null ? 1 : existing.revision() + 1,
            source.caseType(),
            source.expectedMode(),
            source.severity(),
            source.question(),
            source.knowledgeScope(),
            source.retrievalFilters(),
            source.goldFacts(),
            source.acceptedAnswers(),
            source.goldEvidenceLocators(),
            source.requiredDocGroups(),
            source.forbiddenDocumentRefs(),
            source.tags(),
            new EvalCaseOrigin("promotion", source.id(), "Promoted from candidate revision " + source.revision(), Map.of(
                "sourceDatasetId", source.datasetId(),
                "sourceRevision", source.revision()
            )),
            EvalReviewStatus.APPROVED,
            true,
            existing == null ? now : existing.createdAt(),
            now
        );
    }

    private String actor() {
        return RequestContext.currentActor();
    }
}
