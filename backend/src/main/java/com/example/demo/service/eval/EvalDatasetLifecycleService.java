package com.example.demo.service.eval;

import com.example.demo.model.eval.CreateEvalCandidateFromChatRunRequest;
import com.example.demo.model.eval.CreateEvalCaseRequest;
import com.example.demo.model.eval.CreateEvalCaseReviewRequest;
import com.example.demo.model.eval.CreateEvalDatasetRequest;
import com.example.demo.model.eval.CreateEvalDatasetVersionRequest;
import com.example.demo.model.eval.EvalCase;
import com.example.demo.model.eval.EvalCasePromotion;
import com.example.demo.model.eval.EvalCaseRevision;
import com.example.demo.model.eval.EvalDataset;
import com.example.demo.model.eval.EvalDatasetVersion;
import com.example.demo.model.eval.PromoteEvalCaseRequest;
import com.example.demo.model.eval.SubmitEvalCaseReviewRequest;
import com.example.demo.model.eval.UpdateEvalCaseRequest;
import com.example.demo.model.eval.UpdateEvalDatasetRequest;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EvalDatasetLifecycleService {

    private final EvalDatasetMutationService datasetMutationService;
    private final EvalDatasetVersioningService versioningService;
    private final EvalCaseMutationService caseMutationService;
    private final EvalCaseReviewWorkflow reviewWorkflow;
    private final EvalCasePromotionService promotionService;

    public EvalDatasetLifecycleService(
        EvalDatasetMutationService datasetMutationService,
        EvalDatasetVersioningService versioningService,
        EvalCaseMutationService caseMutationService,
        EvalCaseReviewWorkflow reviewWorkflow,
        EvalCasePromotionService promotionService
    ) {
        this.datasetMutationService = datasetMutationService;
        this.versioningService = versioningService;
        this.caseMutationService = caseMutationService;
        this.reviewWorkflow = reviewWorkflow;
        this.promotionService = promotionService;
    }

    @Transactional
    public EvalDataset createDataset(CreateEvalDatasetRequest request) {
        return datasetMutationService.createDataset(request);
    }

    @Transactional
    public EvalDataset updateDataset(String id, UpdateEvalDatasetRequest request) {
        return datasetMutationService.updateDataset(id, request);
    }

    @Transactional
    public EvalDataset archiveDataset(String id) {
        return datasetMutationService.archiveDataset(id);
    }

    @Transactional
    public EvalDatasetVersion createDatasetVersion(String datasetId, CreateEvalDatasetVersionRequest request) {
        return versioningService.createDatasetVersion(datasetId, request);
    }

    public List<EvalDatasetVersion> listDatasetVersions(String datasetId) {
        return versioningService.listDatasetVersions(datasetId);
    }

    @Transactional
    public EvalCase createCase(String datasetId, CreateEvalCaseRequest request) {
        return caseMutationService.createCase(datasetId, request);
    }

    @Transactional
    public EvalCase updateCase(String id, UpdateEvalCaseRequest request) {
        return caseMutationService.updateCase(id, request);
    }

    public List<EvalCaseRevision> listCaseRevisions(String id) {
        return caseMutationService.listCaseRevisions(id);
    }

    @Transactional
    public EvalCase submitCaseReview(String id, SubmitEvalCaseReviewRequest request) {
        return reviewWorkflow.submitCaseReview(id, request);
    }

    @Transactional
    public EvalCase reviewCase(String id, CreateEvalCaseReviewRequest request) {
        return reviewWorkflow.reviewCase(id, request);
    }

    @Transactional
    public EvalCase archiveCase(String id) {
        return reviewWorkflow.archiveCase(id);
    }

    @Transactional
    public EvalCase createCandidateFromChatRun(CreateEvalCandidateFromChatRunRequest request) {
        return caseMutationService.createCandidateFromChatRun(request);
    }

    @Transactional
    public EvalCasePromotion promoteCase(String id, PromoteEvalCaseRequest request) {
        return promotionService.promoteCase(id, request);
    }
}
