package com.example.demo.infrastructure.eval;

import com.example.demo.model.eval.EvalCase;
import com.example.demo.model.eval.EvalCasePromotion;
import com.example.demo.model.eval.EvalCaseReview;
import com.example.demo.model.eval.EvalCaseRevision;
import com.example.demo.model.eval.EvalDataset;
import com.example.demo.model.eval.EvalDatasetDetail;
import com.example.demo.model.eval.EvalDatasetSummary;
import com.example.demo.model.eval.EvalDatasetVersion;
import com.example.demo.model.eval.EvalDatasetVersionCaseRef;
import com.example.demo.service.eval.port.EvalDatasetRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresEvalDatasetRepository implements EvalDatasetRepository {

    private final EvalDatasetJdbcDao datasetDao;
    private final EvalCaseJdbcDao caseDao;
    private final EvalDatasetVersionJdbcDao versionDao;
    private final EvalPromotionJdbcDao promotionDao;

    public PostgresEvalDatasetRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        EvalJdbcJsonMapper jsonMapper = new EvalJdbcJsonMapper(objectMapper);
        EvalJdbcRowMappers rowMappers = new EvalJdbcRowMappers(jsonMapper);
        this.caseDao = new EvalCaseJdbcDao(jdbcTemplate, jsonMapper, rowMappers);
        this.datasetDao = new EvalDatasetJdbcDao(jdbcTemplate, jsonMapper, rowMappers, caseDao);
        this.versionDao = new EvalDatasetVersionJdbcDao(jdbcTemplate, jsonMapper, rowMappers);
        this.promotionDao = new EvalPromotionJdbcDao(jdbcTemplate, jsonMapper);
    }

    @Override
    public List<EvalDatasetSummary> findDatasetSummaries() {
        return datasetDao.findDatasetSummaries();
    }

    @Override
    public Optional<EvalDatasetDetail> findDatasetDetail(String id) {
        return datasetDao.findDatasetDetail(id);
    }

    @Override
    public EvalDataset saveDataset(EvalDataset dataset) {
        return datasetDao.saveDataset(dataset);
    }

    @Override
    public EvalCase saveCase(EvalCase evalCase) {
        return caseDao.saveCase(evalCase);
    }

    @Override
    public EvalCaseReview saveReview(EvalCaseReview review) {
        return caseDao.saveReview(review);
    }

    @Override
    public EvalCaseRevision saveCaseRevision(EvalCaseRevision revision) {
        return caseDao.saveCaseRevision(revision);
    }

    @Override
    public List<EvalCaseRevision> findCaseRevisions(String caseId) {
        return caseDao.findCaseRevisions(caseId);
    }

    @Override
    public Optional<EvalCaseRevision> findCaseRevision(String caseId, int revision) {
        return caseDao.findCaseRevision(caseId, revision);
    }

    @Override
    public List<EvalCaseRevision> findCaseRevisionsByRefs(List<EvalDatasetVersionCaseRef> refs) {
        return caseDao.findCaseRevisionsByRefs(refs);
    }

    @Override
    public EvalDatasetVersion saveDatasetVersion(EvalDatasetVersion version) {
        return versionDao.saveDatasetVersion(version);
    }

    @Override
    public List<EvalDatasetVersion> findDatasetVersions(String datasetId) {
        return versionDao.findDatasetVersions(datasetId);
    }

    @Override
    public Optional<EvalDatasetVersion> findDatasetVersion(String datasetId, String version) {
        return versionDao.findDatasetVersion(datasetId, version);
    }

    @Override
    public EvalCasePromotion savePromotion(EvalCasePromotion promotion) {
        return promotionDao.savePromotion(promotion);
    }

    @Override
    public boolean isStorageReady() {
        return datasetDao.isStorageReady();
    }

    @Override
    public Optional<EvalDataset> findDatasetById(String id) {
        return datasetDao.findDatasetById(id);
    }

    @Override
    public Optional<EvalCase> findCaseById(String id) {
        return caseDao.findCaseById(id);
    }

    @Override
    public List<EvalCase> findCasesByDatasetId(String datasetId) {
        return caseDao.findCasesByDatasetId(datasetId);
    }
}
