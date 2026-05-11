package com.example.demo.service.eval;

import com.example.demo.error.ApplicationException;
import com.example.demo.error.ErrorType;
import com.example.demo.model.eval.EvalCase;
import com.example.demo.model.eval.EvalDataset;
import com.example.demo.model.eval.EvalDatasetKind;
import com.example.demo.model.eval.EvalLifecycleStatus;
import com.example.demo.model.eval.EvalReviewStatus;
import com.example.demo.service.eval.port.EvalDatasetRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class EvalDatasetLifecycleValidator {

    private final EvalDatasetRepository repository;
    private final EvalCaseValidationService validationService;

    public EvalDatasetLifecycleValidator(
        EvalDatasetRepository repository,
        EvalCaseValidationService validationService
    ) {
        this.repository = repository;
        this.validationService = validationService;
    }

    EvalDataset requireDataset(String id) {
        return repository.findDatasetById(id)
            .orElseThrow(() -> new ApplicationException(
                ErrorType.NOT_FOUND,
                "eval_dataset.not_found",
                "Eval dataset '" + id + "' does not exist"
            ));
    }

    EvalCase requireCase(String id) {
        return repository.findCaseById(id)
            .orElseThrow(() -> new ApplicationException(
                ErrorType.NOT_FOUND,
                "eval_case.not_found",
                "Eval case '" + id + "' does not exist"
            ));
    }

    void ensureMutableDataset(EvalDataset dataset) {
        if (dataset.status() == EvalLifecycleStatus.ARCHIVED) {
            throw new ApplicationException(
                ErrorType.CONFLICT,
                "eval_dataset.archived",
                "Archived eval datasets cannot be modified"
            );
        }
    }

    void ensureEditableCase(EvalCase evalCase) {
        if (!evalCase.active()) {
            throw new ApplicationException(
                ErrorType.CONFLICT,
                "eval_case.archived",
                "Archived eval cases cannot be edited"
            );
        }
    }

    void ensureReleaseVersionAllowed(EvalDataset dataset, List<EvalCase> activeCases) {
        if (dataset.kind() != EvalDatasetKind.GOLDEN && dataset.kind() != EvalDatasetKind.SMOKE) {
            return;
        }
        if (activeCases.isEmpty() || activeCases.stream().anyMatch(evalCase -> evalCase.reviewStatus() != EvalReviewStatus.APPROVED)) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
                "eval_dataset_version.unapproved_cases",
                "GOLDEN and SMOKE dataset versions require at least one active APPROVED eval case"
            );
        }
    }

    EvalReviewStatus requireReviewDecision(EvalReviewStatus status) {
        if (status != EvalReviewStatus.APPROVED && status != EvalReviewStatus.REJECTED) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
                "eval_case.review_status_invalid",
                "Review status must be APPROVED or REJECTED"
            );
        }
        return status;
    }

    void validateForReview(EvalCase evalCase) {
        validationService.validateForReview(evalCase);
    }

    void ensureCandidatePromotionAllowed(EvalCase source, EvalDataset sourceDataset, EvalDataset targetDataset) {
        if (sourceDataset.kind() != EvalDatasetKind.CANDIDATE
            || source.reviewStatus() != EvalReviewStatus.APPROVED
            || !source.active()) {
            throw new ApplicationException(
                ErrorType.CONFLICT,
                "eval.promotion_requires_approved_candidate",
                "Only an approved active candidate revision can be promoted"
            );
        }
        if (targetDataset.kind() != EvalDatasetKind.GOLDEN && targetDataset.kind() != EvalDatasetKind.SMOKE) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
                "eval.promotion_target_invalid",
                "Promotion target must be a GOLDEN or SMOKE dataset"
            );
        }
    }

    String normalizedRequired(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
                "eval.request_field_required",
                "Field '" + field + "' is required"
            );
        }
        return value.trim();
    }

    String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
