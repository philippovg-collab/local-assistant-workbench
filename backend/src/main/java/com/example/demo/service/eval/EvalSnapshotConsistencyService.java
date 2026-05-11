package com.example.demo.service.eval;

import com.example.demo.error.ApplicationException;
import com.example.demo.error.ErrorType;
import com.example.demo.model.eval.CorpusSnapshot;
import com.example.demo.model.eval.CorpusSnapshotItem;
import com.example.demo.model.eval.EvalFailureCode;
import com.example.demo.model.eval.EvalLifecycleStatus;
import com.example.demo.model.eval.ResolveEvalExecutionConfigRequest;
import com.example.demo.model.eval.ResolvedEvalExecutionConfig;
import com.example.demo.service.eval.port.CorpusSnapshotRepository;
import com.example.demo.service.eval.port.EvalCorpusReader;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class EvalSnapshotConsistencyService {

    private final CorpusSnapshotRepository snapshotRepository;
    private final EvalCorpusReader corpusReader;
    private final EvalRuntimeStateService runtimeStateService;
    private final EvalExecutionConfigService executionConfigService;
    private final EvalHashService hashService;

    public EvalSnapshotConsistencyService(
        CorpusSnapshotRepository snapshotRepository,
        EvalCorpusReader corpusReader,
        EvalRuntimeStateService runtimeStateService,
        EvalExecutionConfigService executionConfigService,
        EvalHashService hashService
    ) {
        this.snapshotRepository = snapshotRepository;
        this.corpusReader = corpusReader;
        this.runtimeStateService = runtimeStateService;
        this.executionConfigService = executionConfigService;
        this.hashService = hashService;
    }

    public CorpusSnapshot getSnapshot(String snapshotId) {
        if (isBlank(snapshotId)) {
            return null;
        }
        return snapshotRepository.findSnapshot(snapshotId)
            .orElseThrow(() -> new ApplicationException(
                ErrorType.NOT_FOUND,
                "corpus_snapshot.not_found",
                "Corpus snapshot '" + snapshotId + "' does not exist"
            ));
    }

    public SnapshotConsistencyResult validatePersistentRun(
        String datasetId,
        String datasetVersion,
        String corpusSnapshotId,
        String executionConfigHash,
        Instant referenceInstant
    ) {
        if (isBlank(corpusSnapshotId) || isBlank(executionConfigHash) || referenceInstant == null) {
            throw failure(
                EvalFailureCode.CONFIG_MISMATCH,
                ErrorType.INVALID_REQUEST,
                "Persistent retrieval eval runs require corpusSnapshotId, executionConfigHash, and referenceInstant"
            );
        }
        CorpusSnapshot snapshot = getSnapshot(corpusSnapshotId);
        validateSnapshotUsable(snapshot);
        if (!referenceInstant.equals(snapshot.referenceInstant())) {
            throw failure(
                EvalFailureCode.CONFIG_MISMATCH,
                ErrorType.CONFLICT,
                "Request referenceInstant does not match the corpus snapshot referenceInstant"
            );
        }

        String currentMaterialSetHash = currentMaterialSetHash(snapshot);
        if (!snapshot.materialSetHash().equals(currentMaterialSetHash)) {
            throw failure(
                EvalFailureCode.SNAPSHOT_STALE,
                ErrorType.CONFLICT,
                "Current corpus material set no longer matches corpus snapshot '" + corpusSnapshotId + "'"
            );
        }

        String currentSearchStateHash = runtimeStateService.capture().searchStateHash();
        if (!snapshot.searchStateHash().equals(currentSearchStateHash)) {
            throw failure(
                EvalFailureCode.CONFIG_MISMATCH,
                ErrorType.CONFLICT,
                "Current search state no longer matches corpus snapshot '" + corpusSnapshotId + "'"
            );
        }

        ResolvedEvalExecutionConfig resolvedConfig = executionConfigService.resolve(
            new ResolveEvalExecutionConfigRequest(
                null,
                datasetId,
                datasetVersion,
                corpusSnapshotId,
                referenceInstant,
                null,
                null,
                Map.of()
            )
        );
        if (!executionConfigHash.equals(resolvedConfig.configHash())) {
            throw failure(
                EvalFailureCode.CONFIG_MISMATCH,
                ErrorType.CONFLICT,
                "Request executionConfigHash does not match the current resolved eval execution config"
            );
        }
        return new SnapshotConsistencyResult(snapshot, resolvedConfig, materialIds(snapshot));
    }

    public Set<String> materialIds(CorpusSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        List<CorpusSnapshotItem> items = snapshot.items().isEmpty()
            ? snapshotRepository.findItemsBySnapshotId(snapshot.id())
            : snapshot.items();
        LinkedHashSet<String> materialIds = new LinkedHashSet<>();
        items.stream()
            .map(CorpusSnapshotItem::materialId)
            .filter(value -> value != null && !value.isBlank())
            .forEach(materialIds::add);
        return Set.copyOf(materialIds);
    }

    private void validateSnapshotUsable(CorpusSnapshot snapshot) {
        if (snapshot == null || snapshot.status() != EvalLifecycleStatus.ACTIVE) {
            throw failure(
                EvalFailureCode.SNAPSHOT_STALE,
                ErrorType.CONFLICT,
                "Corpus snapshot is not active and cannot be used for a persistent retrieval eval run"
            );
        }
    }

    private String currentMaterialSetHash(CorpusSnapshot snapshot) {
        Object includeSupersededValue = snapshot.manifest().get("includeSuperseded");
        boolean includeSuperseded = includeSupersededValue == null || Boolean.TRUE.equals(includeSupersededValue);
        List<Map<String, Object>> payload = corpusReader.readSnapshotMaterials(includeSuperseded).stream()
            .sorted(Comparator
                .comparing(EvalCorpusMaterial::sourceKey, Comparator.nullsLast(String::compareTo))
                .thenComparingInt(EvalCorpusMaterial::lineageVersion)
                .thenComparing(EvalCorpusMaterial::materialId))
            .map(this::materialSetHashPayload)
            .toList();
        return hashService.hash(payload);
    }

    private Map<String, Object> materialSetHashPayload(EvalCorpusMaterial material) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("materialId", material.materialId());
        payload.put("sourceKey", material.sourceKey());
        payload.put("versionState", material.versionState());
        payload.put("lineageVersion", material.lineageVersion());
        payload.put("versionLabel", material.versionLabel());
        payload.put("indexingStatus", material.indexingStatus());
        payload.put("contentHash", material.contentHash());
        payload.put("metadataHash", hashService.hash(metadataHashPayload(material)));
        payload.put("chunkProfile", material.chunkProfile());
        payload.put("chunkCount", material.chunks().size());
        payload.put("chunkSetHash", hashService.hash(material.chunks().stream().map(this::chunkHashPayload).toList()));
        return payload;
    }

    private Map<String, Object> metadataHashPayload(EvalCorpusMaterial material) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("documentNumber", material.documentNumber());
        payload.put("documentDate", material.documentDate());
        payload.put("documentType", material.documentType());
        payload.put("documentStatus", material.documentStatus());
        payload.put("workspaceKey", material.workspaceKey());
        payload.put("projectKey", material.projectKey());
        payload.put("languageCode", material.languageCode());
        payload.put("versionLabel", material.versionLabel());
        payload.put("metadata", material.metadata());
        return payload;
    }

    private Map<String, Object> chunkHashPayload(EvalCorpusChunk chunk) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chunkIndex", chunk.chunkIndex());
        payload.put("chunkHash", hashService.hash(Map.of("text", chunk.chunkText() == null ? "" : chunk.chunkText())));
        payload.put("page", chunk.page());
        payload.put("chunkType", chunk.chunkType());
        payload.put("sectionPath", chunk.sectionPath());
        payload.put("headingTrail", chunk.headingTrail());
        payload.put("tableId", chunk.tableId());
        payload.put("slideId", chunk.slideId());
        payload.put("parserConfidence", chunk.parserConfidence());
        return payload;
    }

    private ApplicationException failure(EvalFailureCode failureCode, ErrorType errorType, String message) {
        return new ApplicationException(errorType, "eval." + failureCode.name().toLowerCase(), message);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record SnapshotConsistencyResult(
        CorpusSnapshot snapshot,
        ResolvedEvalExecutionConfig resolvedConfig,
        Set<String> materialIds
    ) {
        public SnapshotConsistencyResult {
            materialIds = materialIds == null ? Set.of() : Set.copyOf(materialIds);
        }
    }
}
