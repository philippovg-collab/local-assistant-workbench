package com.example.demo.service.eval.port;

import com.example.demo.model.eval.EvalCase;
import com.example.demo.model.eval.EvalCasePromotion;
import com.example.demo.model.eval.EvalCaseReview;
import com.example.demo.model.eval.EvalCaseRevision;
import com.example.demo.model.eval.EvalDataset;
import com.example.demo.model.eval.EvalDatasetDetail;
import com.example.demo.model.eval.EvalDatasetSummary;
import com.example.demo.model.eval.EvalDatasetVersion;
import com.example.demo.model.eval.EvalDatasetVersionCaseRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public interface EvalDatasetRepository {

    List<EvalDatasetSummary> findDatasetSummaries();

    Optional<EvalDataset> findDatasetById(String id);

    Optional<EvalDatasetDetail> findDatasetDetail(String id);

    Optional<EvalCase> findCaseById(String id);

    List<EvalCase> findCasesByDatasetId(String datasetId);

    EvalDataset saveDataset(EvalDataset dataset);

    EvalCase saveCase(EvalCase evalCase);

    EvalCaseReview saveReview(EvalCaseReview review);

    EvalCaseRevision saveCaseRevision(EvalCaseRevision revision);

    List<EvalCaseRevision> findCaseRevisions(String caseId);

    Optional<EvalCaseRevision> findCaseRevision(String caseId, int revision);

    EvalDatasetVersion saveDatasetVersion(EvalDatasetVersion version);

    List<EvalDatasetVersion> findDatasetVersions(String datasetId);

    Optional<EvalDatasetVersion> findDatasetVersion(String datasetId, String version);

    default List<EvalCaseRevision> findCaseRevisionsByRefs(List<EvalDatasetVersionCaseRef> refs) {
        if (refs == null || refs.isEmpty()) {
            return List.of();
        }
        List<EvalCaseRevision> revisions = new ArrayList<>();
        for (EvalDatasetVersionCaseRef ref : refs) {
            findCaseRevision(ref.caseId(), ref.revision()).ifPresent(revisions::add);
        }
        return List.copyOf(revisions);
    }

    EvalCasePromotion savePromotion(EvalCasePromotion promotion);

    boolean isStorageReady();
}
