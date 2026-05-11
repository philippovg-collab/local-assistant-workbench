package com.example.demo.service.eval;

import com.example.demo.error.ApplicationException;
import com.example.demo.error.ErrorType;
import com.example.demo.model.eval.CorpusSnapshot;
import com.example.demo.model.eval.EvalExecutionConfig;
import com.example.demo.model.eval.EvalRuntimeStateSnapshot;
import com.example.demo.model.eval.ResolveEvalExecutionConfigRequest;
import com.example.demo.model.eval.ResolvedEvalExecutionConfig;
import com.example.demo.service.eval.port.CorpusSnapshotRepository;
import com.example.demo.service.eval.port.EvalDatasetRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class EvalExecutionConfigService {

    public static final String DEFAULT_PROMPT_VERSION = "production-current";

    private final EvalRuntimeStateService runtimeStateService;
    private final CorpusSnapshotRepository snapshotRepository;
    private final EvalDatasetRepository datasetRepository;
    private final EvalHashService hashService;

    public EvalExecutionConfigService(
        EvalRuntimeStateService runtimeStateService,
        CorpusSnapshotRepository snapshotRepository,
        EvalDatasetRepository datasetRepository,
        EvalHashService hashService
    ) {
        this.runtimeStateService = runtimeStateService;
        this.snapshotRepository = snapshotRepository;
        this.datasetRepository = datasetRepository;
        this.hashService = hashService;
    }

    public ResolvedEvalExecutionConfig resolve(ResolveEvalExecutionConfigRequest request) {
        ResolveEvalExecutionConfigRequest safeRequest = request == null
            ? new ResolveEvalExecutionConfigRequest(null, null, null, null, null, null, null, Map.of())
            : request;
        EvalRuntimeStateSnapshot runtimeState = runtimeStateService.capture();
        CorpusSnapshot snapshot = snapshot(safeRequest.corpusSnapshotId());
        String datasetVersion = datasetVersion(safeRequest.datasetId(), safeRequest.datasetVersion());
        Instant referenceInstant = firstNonNull(
            safeRequest.referenceInstant(),
            snapshot == null ? null : snapshot.referenceInstant(),
            runtimeState.capturedAt()
        );
        Map<String, Object> revisionPins = revisionPins(snapshot);

        Map<String, Object> providerPins = new LinkedHashMap<>();
        providerPins.put("chatProvider", mapAt(runtimeState.state(), "chatProvider"));
        providerPins.put("embeddingProvider", mapAt(runtimeState.state(), "embeddingProvider"));
        Map<String, Object> retrievalLimits = mapAt(runtimeState.state(), "rag");
        Map<String, Object> rolloutFlags = mapAt(runtimeState.state(), "rolloutFlags");
        Map<String, Object> searchSync = mapAt(runtimeState.state(), "searchSync");
        Map<String, Object> options = optionsWithSearchSync(safeRequest.options(), searchSync);
        String materialSetHash = snapshot == null ? null : snapshot.materialSetHash();
        String searchStateHash = snapshot == null ? runtimeState.searchStateHash() : snapshot.searchStateHash();

        Map<String, Object> hashPayload = configPayload(
            safeRequest.gitCommitSha(),
            safeRequest.datasetId(),
            datasetVersion,
            safeRequest.corpusSnapshotId(),
            referenceInstant,
            materialSetHash,
            searchStateHash,
            promptVersion(safeRequest.promptVersion()),
            safeRequest.judgePromptVersion(),
            providerPins,
            retrievalLimits,
            rolloutFlags,
            revisionPins,
            searchSync,
            options
        );
        String configHash = hashService.hash(hashPayload);
        EvalExecutionConfig config = new EvalExecutionConfig(
            safeRequest.gitCommitSha(),
            safeRequest.datasetId(),
            datasetVersion,
            safeRequest.corpusSnapshotId(),
            referenceInstant,
            materialSetHash,
            searchStateHash,
            configHash,
            promptVersion(safeRequest.promptVersion()),
            safeRequest.judgePromptVersion(),
            providerPins,
            retrievalLimits,
            rolloutFlags,
            revisionPins,
            options
        );
        return new ResolvedEvalExecutionConfig(config, configHash, runtimeState);
    }

    private CorpusSnapshot snapshot(String snapshotId) {
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

    private String datasetVersion(String datasetId, String requestedVersion) {
        if (!isBlank(requestedVersion) || isBlank(datasetId)) {
            return requestedVersion;
        }
        return datasetRepository.findDatasetDetail(datasetId)
            .map(detail -> detail.dataset().version())
            .orElseThrow(() -> new ApplicationException(
                ErrorType.NOT_FOUND,
                "eval_dataset.not_found",
                "Eval dataset '" + datasetId + "' does not exist"
            ));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> revisionPins(CorpusSnapshot snapshot) {
        if (snapshot == null) {
            return Map.of();
        }
        Object pins = snapshot.manifest().get("revisionPins");
        if (pins instanceof Map<?, ?> rawMap) {
            return (Map<String, Object>) rawMap;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapAt(Map<String, Object> root, String key) {
        Object value = root.get(key);
        if (value instanceof Map<?, ?> rawMap) {
            return (Map<String, Object>) rawMap;
        }
        return Map.of();
    }

    private Map<String, Object> configPayload(
        String gitCommitSha,
        String datasetId,
        String datasetVersion,
        String corpusSnapshotId,
        Instant referenceInstant,
        String materialSetHash,
        String searchStateHash,
        String promptVersion,
        String judgePromptVersion,
        Map<String, Object> providerPins,
        Map<String, Object> retrievalLimits,
        Map<String, Object> rolloutFlags,
        Map<String, Object> revisionPins,
        Map<String, Object> searchSync,
        Map<String, Object> options
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("gitCommitSha", gitCommitSha);
        payload.put("datasetId", datasetId);
        payload.put("datasetVersion", datasetVersion);
        payload.put("corpusSnapshotId", corpusSnapshotId);
        payload.put("referenceInstant", referenceInstant);
        payload.put("materialSetHash", materialSetHash);
        payload.put("searchStateHash", searchStateHash);
        payload.put("promptVersion", promptVersion);
        payload.put("judgePromptVersion", judgePromptVersion);
        payload.put("providerPins", providerPins);
        payload.put("retrievalLimits", retrievalLimits);
        payload.put("rolloutFlags", rolloutFlags);
        payload.put("mappingHash", searchSync.get("mappingHash"));
        payload.put("searchSync", searchSync);
        payload.put("revisionPins", revisionPins);
        payload.put("options", options == null ? Map.of() : options);
        return payload;
    }

    private Map<String, Object> optionsWithSearchSync(Map<String, Object> requestedOptions, Map<String, Object> searchSync) {
        Map<String, Object> options = new LinkedHashMap<>(requestedOptions == null ? Map.of() : requestedOptions);
        options.putIfAbsent("mappingHash", searchSync.get("mappingHash"));
        options.putIfAbsent("searchSync", searchSync);
        return options;
    }

    private String promptVersion(String promptVersion) {
        return isBlank(promptVersion) ? DEFAULT_PROMPT_VERSION : promptVersion.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
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
}
