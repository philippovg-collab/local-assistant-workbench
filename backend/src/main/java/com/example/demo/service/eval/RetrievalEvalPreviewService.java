package com.example.demo.service.eval;

import com.example.demo.error.ApplicationException;
import com.example.demo.error.ErrorType;
import com.example.demo.model.EvidenceLocator;
import com.example.demo.model.KnowledgeScope;
import com.example.demo.model.MaterialMetadataSnapshot;
import com.example.demo.model.RetrievalDebug;
import com.example.demo.model.RetrievalFilters;
import com.example.demo.model.eval.CorpusSnapshot;
import com.example.demo.model.eval.EvalCase;
import com.example.demo.model.eval.EvalCaseRevision;
import com.example.demo.model.eval.EvalDatasetDetail;
import com.example.demo.model.eval.EvalDatasetVersion;
import com.example.demo.model.eval.EvalDatasetVersionCaseRef;
import com.example.demo.model.eval.RetrievalEvalCandidate;
import com.example.demo.model.eval.RetrievalEvalCaseRef;
import com.example.demo.model.eval.RetrievalEvalMetric;
import com.example.demo.model.eval.RetrievalEvalPreviewRequest;
import com.example.demo.model.eval.RetrievalEvalPreviewResponse;
import com.example.demo.model.eval.RetrievalEvalReproducibilityStatus;
import com.example.demo.model.eval.RetrievalEvalSnapshotRef;
import com.example.demo.model.eval.RetrievalEvalStageTrace;
import com.example.demo.model.eval.RetrievalEvalTrace;
import com.example.demo.service.HybridChunkRanker;
import com.example.demo.service.MaterialRetrievalService;
import com.example.demo.service.RetrievalExecutionRequest;
import com.example.demo.service.RetrievalSearchExecution;
import com.example.demo.service.eval.EvalSnapshotConsistencyService.SnapshotConsistencyResult;
import com.example.demo.service.eval.port.EvalDatasetRepository;
import com.example.demo.service.material.MaterialChunkSearchMatch;
import com.example.demo.service.material.StoredMaterialChunk;
import com.example.demo.service.material.StoredMaterialRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RetrievalEvalPreviewService {

    private final MaterialRetrievalService retrievalService;
    private final EvalDatasetRepository datasetRepository;
    private final EvalSnapshotConsistencyService snapshotConsistencyService;
    private final RetrievalScoringService scoringService;
    private final ObjectMapper objectMapper;

    public RetrievalEvalPreviewService(
        MaterialRetrievalService retrievalService,
        EvalDatasetRepository datasetRepository,
        EvalSnapshotConsistencyService snapshotConsistencyService,
        RetrievalScoringService scoringService,
        ObjectMapper objectMapper
    ) {
        this.retrievalService = retrievalService;
        this.datasetRepository = datasetRepository;
        this.snapshotConsistencyService = snapshotConsistencyService;
        this.scoringService = scoringService;
        this.objectMapper = objectMapper;
    }

    public RetrievalEvalPreviewResponse preview(RetrievalEvalPreviewRequest request) {
        return preview(request, null);
    }

    RetrievalEvalPreviewResponse preview(RetrievalEvalPreviewRequest request, String datasetVersion) {
        RetrievalEvalPreviewRequest safeRequest = request == null
            ? new RetrievalEvalPreviewRequest(null, null, null, null, null, null, null, null, null, null, null, null, null, null)
            : request;
        EvalCase evalCase = resolveCase(safeRequest);
        String query = firstNonBlank(safeRequest.query(), evalCase == null ? null : evalCase.question());
        if (!StringUtils.hasText(query)) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
                "eval.retrieval_preview.invalid_query",
                "Field 'query' is required unless a dataset case with a question is selected"
            );
        }

        KnowledgeScope knowledgeScope = knowledgeScope(safeRequest, evalCase);
        RetrievalFilters filters = retrievalFilters(safeRequest, evalCase);
        SnapshotPins pins = snapshotPins(safeRequest, firstNonBlank(datasetVersion, safeRequest.datasetVersion()));
        Instant referenceInstant = firstNonNull(
            safeRequest.referenceInstant(),
            pins.snapshot() == null ? null : pins.snapshot().referenceInstant()
        );
        RetrievalSearchExecution execution = retrievalService.executeRetrieval(new RetrievalExecutionRequest(
            query,
            knowledgeScope,
            filters,
            safeRequest.dismissedRetrievalHintKeys(),
            safeRequest.limit(),
            false,
            referenceInstant,
            pins.materialIds()
        ));
        RetrievalEvalStageTrace stages = stages(execution, safeRequest.includeCandidatesOrDefault());
        List<RetrievalEvalMetric> metrics = evalCase == null
            ? List.of()
            : scoringService.score(evalCase, stages.finalChunks(), execution, metricK(safeRequest, execution));
        String configHash = firstNonBlank(
            pins.configHash(),
            safeRequest.executionConfigHash(),
            execution.retrievalDebug().retrievalConfigHash()
        );
        return new RetrievalEvalPreviewResponse(
            query,
            caseRef(evalCase),
            snapshotRef(pins.snapshot()),
            configHash,
            pins.reproducibilityStatus(),
            trace(execution),
            stages,
            metrics,
            pins.warnings()
        );
    }

    RetrievalEvalStageTrace stages(RetrievalSearchExecution execution, boolean includeCandidates) {
        List<RetrievalEvalCandidate> finalChunks = rankedCandidates("FINAL", execution.rankedMatches(), execution, true);
        if (!includeCandidates) {
            return new RetrievalEvalStageTrace(List.of(), List.of(), List.of(), List.of(), finalChunks);
        }
        return new RetrievalEvalStageTrace(
            matchCandidates("SEMANTIC", execution.semanticMatches(), execution),
            matchCandidates("LEXICAL", execution.lexicalSearchResult().matches(), execution),
            rankedCandidates("FUSED", execution.fusedMatches(), execution, false),
            rankedCandidates("PRE_RERANK", execution.preRerankMatches(), execution, false),
            finalChunks
        );
    }

    private SnapshotPins snapshotPins(RetrievalEvalPreviewRequest request, String datasetVersion) {
        if (!StringUtils.hasText(request.corpusSnapshotId())) {
            return new SnapshotPins(
                null,
                null,
                null,
                RetrievalEvalReproducibilityStatus.UNPINNED,
                List.of("Preview is unpinned because no corpusSnapshotId was provided")
            );
        }
        CorpusSnapshot snapshot = snapshotConsistencyService.getSnapshot(request.corpusSnapshotId());
        if (StringUtils.hasText(request.executionConfigHash()) && request.referenceInstant() != null) {
            SnapshotConsistencyResult consistency = snapshotConsistencyService.validatePersistentRun(
                request.datasetId(),
                datasetVersion,
                request.corpusSnapshotId(),
                request.executionConfigHash(),
                request.referenceInstant()
            );
            return new SnapshotPins(
                consistency.snapshot(),
                consistency.materialIds(),
                consistency.resolvedConfig().configHash(),
                RetrievalEvalReproducibilityStatus.PINNED,
                List.of()
            );
        }
        return new SnapshotPins(
            snapshot,
            snapshotConsistencyService.materialIds(snapshot),
            null,
            RetrievalEvalReproducibilityStatus.UNPINNED,
            List.of("Preview is unpinned because executionConfigHash or referenceInstant was not provided")
        );
    }

    private EvalCase resolveCase(RetrievalEvalPreviewRequest request) {
        if (!StringUtils.hasText(request.datasetId())) {
            return null;
        }
        if (StringUtils.hasText(request.datasetVersion())) {
            return resolveVersionedCase(request);
        }
        EvalDatasetDetail detail = datasetRepository.findDatasetDetail(request.datasetId())
            .orElseThrow(() -> new ApplicationException(
                ErrorType.NOT_FOUND,
                "eval_dataset.not_found",
                "Eval dataset '" + request.datasetId() + "' does not exist"
            ));
        if (!StringUtils.hasText(request.caseId())) {
            return null;
        }
        if (request.caseRevision() != null && isUuid(request.caseId())) {
            return datasetRepository.findCaseRevision(request.caseId(), request.caseRevision())
                .map(com.example.demo.model.eval.EvalCaseRevision::caseSnapshot)
                .filter(evalCase -> request.datasetId().equals(evalCase.datasetId()))
                .orElseThrow(() -> new ApplicationException(
                    ErrorType.NOT_FOUND,
                    "eval_case.not_found",
                    "Eval case '" + request.caseId() + "' revision '" + request.caseRevision()
                        + "' does not exist in dataset '" + request.datasetId() + "'"
                ));
        }
        return detail.cases().stream()
            .filter(evalCase -> request.caseId().equals(evalCase.id()) || request.caseId().equals(evalCase.caseKey()))
            .filter(evalCase -> request.caseRevision() == null || request.caseRevision() == evalCase.revision())
            .findFirst()
            .orElseThrow(() -> new ApplicationException(
                ErrorType.NOT_FOUND,
                "eval_case.not_found",
                "Eval case '" + request.caseId() + "' does not exist in dataset '" + request.datasetId() + "'"
            ));
    }

    private EvalCase resolveVersionedCase(RetrievalEvalPreviewRequest request) {
        EvalDatasetVersion version = datasetRepository.findDatasetVersion(request.datasetId(), request.datasetVersion())
            .orElseThrow(() -> new ApplicationException(
                ErrorType.NOT_FOUND,
                "eval_dataset_version.not_found",
                "Eval dataset version '" + request.datasetVersion() + "' does not exist for dataset '" + request.datasetId() + "'"
            ));
        if (!StringUtils.hasText(request.caseId())) {
            return null;
        }
        EvalDatasetVersionCaseRef ref = version.caseRevisionRefs().stream()
            .map(EvalDatasetVersionCaseRef::fromMap)
            .filter(candidate -> matchesCase(candidate, request.caseId(), request.caseRevision()))
            .findFirst()
            .orElseThrow(() -> new ApplicationException(
                ErrorType.INVALID_REQUEST,
                "eval_preview.case_not_in_dataset_version",
                "Eval case '" + request.caseId() + "' revision '" + request.caseRevision()
                    + "' is not present in dataset version '" + request.datasetVersion() + "'"
            ));
        EvalCaseRevision revision = datasetRepository.findCaseRevision(ref.caseId(), ref.revision())
            .orElseThrow(() -> new ApplicationException(
                ErrorType.CONFLICT,
                "eval_preview.case_revision_missing",
                "Dataset version '" + request.datasetVersion() + "' points to missing eval case revision '"
                    + ref.caseId() + "#" + ref.revision() + "'"
            ));
        EvalCase snapshot = revision.caseSnapshot();
        if (snapshot == null
            || !request.datasetId().equals(snapshot.datasetId())
            || !ref.caseId().equals(snapshot.id())
            || ref.revision() != snapshot.revision()) {
            throw new ApplicationException(
                ErrorType.CONFLICT,
                "eval_preview.case_revision_mismatch",
                "Dataset version '" + request.datasetVersion() + "' points to a mismatched eval case revision"
            );
        }
        return snapshot;
    }

    private boolean matchesCase(EvalDatasetVersionCaseRef ref, String caseId, Integer caseRevision) {
        boolean caseMatches = caseId.equals(ref.caseId()) || caseId.equals(ref.caseKey());
        boolean revisionMatches = caseRevision == null || caseRevision == ref.revision();
        return caseMatches && revisionMatches;
    }

    private KnowledgeScope knowledgeScope(RetrievalEvalPreviewRequest request, EvalCase evalCase) {
        if (!KnowledgeScope.empty().equals(request.knowledgeScope())) {
            return request.knowledgeScope();
        }
        if (evalCase == null || evalCase.knowledgeScope().isEmpty()) {
            return KnowledgeScope.empty();
        }
        return objectMapper.convertValue(evalCase.knowledgeScope(), KnowledgeScope.class);
    }

    private RetrievalFilters retrievalFilters(RetrievalEvalPreviewRequest request, EvalCase evalCase) {
        if (!request.retrievalFilters().isEmpty()) {
            return request.retrievalFilters();
        }
        if (evalCase == null || evalCase.retrievalFilters().isEmpty()) {
            return RetrievalFilters.empty();
        }
        return objectMapper.convertValue(evalCase.retrievalFilters(), RetrievalFilters.class);
    }

    private List<RetrievalEvalCandidate> matchCandidates(
        String stage,
        List<MaterialChunkSearchMatch> matches,
        RetrievalSearchExecution execution
    ) {
        List<RetrievalEvalCandidate> candidates = new ArrayList<>();
        for (int index = 0; index < matches.size(); index++) {
            candidates.add(candidate(stage, index + 1, matches.get(index), null, execution, false));
        }
        return List.copyOf(candidates);
    }

    private List<RetrievalEvalCandidate> rankedCandidates(
        String stage,
        List<HybridChunkRanker.RankedChunk> rankedChunks,
        RetrievalSearchExecution execution,
        boolean finalStage
    ) {
        List<RetrievalEvalCandidate> candidates = new ArrayList<>();
        for (int index = 0; index < rankedChunks.size(); index++) {
            HybridChunkRanker.RankedChunk rankedChunk = rankedChunks.get(index);
            candidates.add(candidate(stage, index + 1, rankedChunk.match(), rankedChunk, execution, finalStage));
        }
        return List.copyOf(candidates);
    }

    private RetrievalEvalCandidate candidate(
        String stage,
        int rank,
        MaterialChunkSearchMatch match,
        HybridChunkRanker.RankedChunk rankedChunk,
        RetrievalSearchExecution execution,
        boolean finalStage
    ) {
        StoredMaterialRecord record = execution.recordsById().get(match.materialId());
        StoredMaterialChunk chunk = chunkFor(match, execution.chunksByMaterialId());
        Integer score = rankedChunk == null ? null : rankedChunk.score();
        return new RetrievalEvalCandidate(
            stage,
            rank,
            match.materialId(),
            match.chunkIndex(),
            match.title(),
            excerpt(match.chunkText()),
            chunk == null ? match.page() : chunk.page(),
            match.chunkType(),
            match.semanticDistance(),
            match.lexicalScore(),
            finalStage ? null : score,
            finalStage ? score : null,
            rankedChunk == null ? null : rankedChunk.scoreBreakdown(),
            evidenceLocator(record, match, chunk)
        );
    }

    private RetrievalEvalTrace trace(RetrievalSearchExecution execution) {
        RetrievalDebug debug = execution.retrievalDebug();
        return new RetrievalEvalTrace(
            debug.queryHints(),
            debug.manualFilters(),
            debug.effectiveFilters(),
            debug.semanticCandidateCount(),
            debug.lexicalCandidateCount(),
            execution.fusedMatches().size(),
            execution.preRerankMatches().size(),
            debug.finalChunkCount(),
            debug.supportVerdict(),
            debug.relevanceProfile(),
            debug.activeRolloutFlags(),
            debug.appliedCapabilities(),
            debug.suppressedCapabilities(),
            debug.referenceInstant(),
            debug.effectiveDate(),
            debug.uploadedAfterInclusive(),
            debug.uploadedBeforeExclusive(),
            debug.retrievalConfigHash(),
            debug.lexicalProvider(),
            debug.embeddingModel(),
            debug.chunkProfile()
        );
    }

    private RetrievalEvalCaseRef caseRef(EvalCase evalCase) {
        if (evalCase == null) {
            return null;
        }
        return new RetrievalEvalCaseRef(evalCase.datasetId(), evalCase.id(), evalCase.caseKey(), evalCase.revision());
    }

    private RetrievalEvalSnapshotRef snapshotRef(CorpusSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        return new RetrievalEvalSnapshotRef(
            snapshot.id(),
            snapshot.materialSetHash(),
            snapshot.searchStateHash(),
            snapshot.referenceInstant()
        );
    }

    private EvidenceLocator evidenceLocator(
        StoredMaterialRecord record,
        MaterialChunkSearchMatch match,
        StoredMaterialChunk chunk
    ) {
        MaterialMetadataSnapshot metadata = record == null ? MaterialMetadataSnapshot.empty() : record.metadata();
        return new EvidenceLocator(
            record == null ? null : record.sourceKey(),
            match.materialId(),
            metadata.documentNumber(),
            metadata.versionLabel(),
            record == null ? null : record.versionState(),
            record == null ? null : record.lineageVersion(),
            match.chunkIndex(),
            chunk == null ? match.page() : chunk.page(),
            chunk == null ? List.of() : chunk.sectionPath(),
            chunk == null ? List.of() : chunk.headingTrail(),
            chunk == null ? null : chunk.tableId(),
            chunk == null ? null : chunk.slideId(),
            null,
            null,
            null,
            null
        );
    }

    private StoredMaterialChunk chunkFor(
        MaterialChunkSearchMatch match,
        Map<String, List<StoredMaterialChunk>> chunksByMaterialId
    ) {
        if (match == null || chunksByMaterialId == null || chunksByMaterialId.isEmpty()) {
            return null;
        }
        return chunksByMaterialId.getOrDefault(match.materialId(), List.of()).stream()
            .filter(chunk -> chunk.index() == match.chunkIndex())
            .findFirst()
            .orElse(null);
    }

    private int metricK(RetrievalEvalPreviewRequest request, RetrievalSearchExecution execution) {
        if (request.limit() != null && request.limit() > 0) {
            return request.limit();
        }
        return Math.max(1, execution.rankedMatches().size());
    }

    private String excerpt(String text) {
        if (text == null || text.length() <= 360) {
            return text;
        }
        return text.substring(0, 357) + "...";
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private record SnapshotPins(
        CorpusSnapshot snapshot,
        Set<String> materialIds,
        String configHash,
        RetrievalEvalReproducibilityStatus reproducibilityStatus,
        List<String> warnings
    ) {
        private SnapshotPins {
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }
}
