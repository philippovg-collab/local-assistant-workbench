package com.example.demo.service.eval;

import com.example.demo.model.eval.CorpusSnapshot;
import com.example.demo.model.eval.CorpusSnapshotDetail;
import com.example.demo.model.eval.CorpusSnapshotItem;
import com.example.demo.model.eval.CorpusSnapshotItemDetail;
import com.example.demo.model.eval.CreateCorpusSnapshotRequest;
import com.example.demo.model.eval.EvalLifecycleStatus;
import com.example.demo.model.eval.EvalRuntimeStateSnapshot;
import com.example.demo.service.eval.port.CorpusSnapshotRepository;
import com.example.demo.service.eval.port.EvalCorpusReader;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CorpusSnapshotService {

    private final CorpusSnapshotRepository snapshotRepository;
    private final EvalCorpusReader corpusReader;
    private final EvalRuntimeStateService runtimeStateService;
    private final EvalHashService hashService;
    private final Clock clock;

    public CorpusSnapshotService(
        CorpusSnapshotRepository snapshotRepository,
        EvalCorpusReader corpusReader,
        EvalRuntimeStateService runtimeStateService,
        EvalHashService hashService,
        Clock clock
    ) {
        this.snapshotRepository = snapshotRepository;
        this.corpusReader = corpusReader;
        this.runtimeStateService = runtimeStateService;
        this.hashService = hashService;
        this.clock = clock;
    }

    @Transactional
    public CorpusSnapshotDetail createSnapshot(CreateCorpusSnapshotRequest request) {
        CreateCorpusSnapshotRequest safeRequest = request == null
            ? new CreateCorpusSnapshotRequest(null, null, true, Map.of())
            : request;
        Instant referenceInstant = safeRequest.referenceInstant() == null ? clock.instant() : safeRequest.referenceInstant();
        String snapshotId = UUID.randomUUID().toString();
        EvalRuntimeStateSnapshot runtimeState = runtimeStateService.capture();
        Map<String, Object> revisionPins = corpusReader.readRevisionPins();

        List<CorpusSnapshotItem> items = corpusReader.readSnapshotMaterials(safeRequest.shouldIncludeSuperseded()).stream()
            .sorted(Comparator
                .comparing(EvalCorpusMaterial::sourceKey, Comparator.nullsLast(String::compareTo))
                .thenComparingInt(EvalCorpusMaterial::lineageVersion)
                .thenComparing(EvalCorpusMaterial::materialId))
            .map(material -> snapshotItem(snapshotId, material))
            .toList();

        String materialSetHash = hashService.hash(items.stream().map(this::materialSetHashPayload).toList());
        Map<String, Object> counts = counts(items);
        String configHash = hashService.hash(configHashPayload(
            snapshotId,
            referenceInstant,
            materialSetHash,
            runtimeState.searchStateHash(),
            runtimeState.state(),
            revisionPins
        ));
        Map<String, Object> manifest = manifest(
            referenceInstant,
            materialSetHash,
            runtimeState,
            configHash,
            counts,
            revisionPins,
            safeRequest
        );
        Instant now = clock.instant();
        CorpusSnapshot snapshot = snapshotRepository.saveSnapshot(new CorpusSnapshot(
            snapshotId,
            snapshotKey(safeRequest.snapshotKey(), referenceInstant),
            EvalLifecycleStatus.ACTIVE,
            referenceInstant,
            materialSetHash,
            runtimeState.searchStateHash(),
            configHash,
            manifest,
            number(counts.get("totalItemCount")),
            number(counts.get("activeItemCount")),
            number(counts.get("supersededItemCount")),
            number(counts.get("readyItemCount")),
            safeRequest.metadata(),
            List.of(),
            now,
            now
        ));
        items.forEach(snapshotRepository::saveItem);
        CorpusSnapshot withItems = new CorpusSnapshot(
            snapshot.id(),
            snapshot.snapshotKey(),
            snapshot.status(),
            snapshot.referenceInstant(),
            snapshot.materialSetHash(),
            snapshot.searchStateHash(),
            snapshot.configHash(),
            snapshot.manifest(),
            snapshot.totalItemCount(),
            snapshot.activeItemCount(),
            snapshot.supersededItemCount(),
            snapshot.readyItemCount(),
            snapshot.metadata(),
            items,
            snapshot.createdAt(),
            snapshot.updatedAt()
        );
        return detail(withItems);
    }

    public CorpusSnapshotDetail detail(CorpusSnapshot snapshot) {
        return new CorpusSnapshotDetail(snapshot, snapshot.manifest(), summary(snapshot));
    }

    public List<CorpusSnapshotItemDetail> itemDetails(String snapshotId, int offset, int limit) {
        int safeOffset = Math.max(0, offset);
        int safeLimit = Math.max(1, Math.min(limit, 500));
        return snapshotRepository.findItemsBySnapshotId(snapshotId, safeOffset, safeLimit).stream()
            .map(CorpusSnapshotItemDetail::from)
            .toList();
    }

    private CorpusSnapshotItem snapshotItem(String snapshotId, EvalCorpusMaterial material) {
        Map<String, Object> metadataPayload = metadataHashPayload(material);
        List<Map<String, Object>> chunkPayload = material.chunks().stream()
            .map(this::chunkHashPayload)
            .toList();
        String metadataHash = hashService.hash(metadataPayload);
        String chunkSetHash = hashService.hash(chunkPayload);
        Map<String, Object> itemMetadata = new LinkedHashMap<>(metadataPayload);
        itemMetadata.put("metadataHash", metadataHash);
        itemMetadata.put("chunkSetHash", chunkSetHash);
        itemMetadata.put("chunkCount", material.chunks().size());
        return new CorpusSnapshotItem(
            UUID.randomUUID().toString(),
            snapshotId,
            material.materialId(),
            material.materialId(),
            material.title(),
            material.sourceKey(),
            material.versionState(),
            material.lineageVersion(),
            material.versionLabel(),
            material.indexingStatus(),
            material.documentNumber(),
            material.documentDate(),
            material.documentType(),
            material.documentStatus(),
            material.workspaceKey(),
            material.projectKey(),
            material.languageCode(),
            material.contentHash(),
            metadataHash,
            material.chunkProfile(),
            material.chunks().size(),
            chunkSetHash,
            itemMetadata,
            material.materialCreatedAt(),
            material.materialUpdatedAt(),
            clock.instant()
        );
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

    private Map<String, Object> materialSetHashPayload(CorpusSnapshotItem item) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("materialId", item.materialId());
        payload.put("sourceKey", item.sourceKey());
        payload.put("versionState", item.versionState());
        payload.put("lineageVersion", item.lineageVersion());
        payload.put("versionLabel", item.versionLabel());
        payload.put("indexingStatus", item.indexingStatus());
        payload.put("contentHash", item.contentHash());
        payload.put("metadataHash", item.metadataHash());
        payload.put("chunkProfile", item.chunkProfile());
        payload.put("chunkCount", item.chunkCount());
        payload.put("chunkSetHash", item.chunkSetHash());
        return payload;
    }

    private Map<String, Object> counts(List<CorpusSnapshotItem> items) {
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("totalItemCount", items.size());
        counts.put("activeItemCount", items.stream().filter(item -> "ACTIVE".equals(item.versionState())).count());
        counts.put("supersededItemCount", items.stream().filter(item -> "SUPERSEDED".equals(item.versionState())).count());
        counts.put("readyItemCount", items.stream().filter(item ->
            "READY".equals(item.indexingStatus()) || "PARTIAL_READY".equals(item.indexingStatus())
        ).count());
        return counts;
    }

    private Map<String, Object> configHashPayload(
        String snapshotId,
        Instant referenceInstant,
        String materialSetHash,
        String searchStateHash,
        Map<String, Object> runtimeState,
        Map<String, Object> revisionPins
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("corpusSnapshotId", snapshotId);
        payload.put("referenceInstant", referenceInstant);
        payload.put("materialSetHash", materialSetHash);
        payload.put("searchStateHash", searchStateHash);
        payload.put("promptVersion", EvalExecutionConfigService.DEFAULT_PROMPT_VERSION);
        payload.put("runtimeState", runtimeState);
        payload.put("revisionPins", revisionPins);
        return payload;
    }

    private Map<String, Object> manifest(
        Instant referenceInstant,
        String materialSetHash,
        EvalRuntimeStateSnapshot runtimeState,
        String configHash,
        Map<String, Object> counts,
        Map<String, Object> revisionPins,
        CreateCorpusSnapshotRequest request
    ) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("referenceInstant", referenceInstant);
        manifest.put("materialSetHash", materialSetHash);
        manifest.put("searchStateHash", runtimeState.searchStateHash());
        manifest.put("mappingHash", mappingHash(runtimeState.state()));
        manifest.put("configHash", configHash);
        manifest.put("counts", counts);
        manifest.put("runtimeState", runtimeState.state());
        manifest.put("revisionPins", revisionPins);
        manifest.put("includeSuperseded", request.shouldIncludeSuperseded());
        return manifest;
    }

    private Map<String, Object> summary(CorpusSnapshot snapshot) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalItemCount", snapshot.totalItemCount());
        summary.put("activeItemCount", snapshot.activeItemCount());
        summary.put("supersededItemCount", snapshot.supersededItemCount());
        summary.put("readyItemCount", snapshot.readyItemCount());
        summary.put("materialSetHash", snapshot.materialSetHash());
        summary.put("searchStateHash", snapshot.searchStateHash());
        summary.put("mappingHash", snapshot.manifest().get("mappingHash"));
        summary.put("configHash", snapshot.configHash());
        return summary;
    }

    @SuppressWarnings("unchecked")
    private Object mappingHash(Map<String, Object> runtimeState) {
        Object searchSync = runtimeState.get("searchSync");
        if (searchSync instanceof Map<?, ?> map) {
            return ((Map<String, Object>) map).get("mappingHash");
        }
        return null;
    }

    private String snapshotKey(String requestedKey, Instant referenceInstant) {
        if (requestedKey != null && !requestedKey.isBlank()) {
            return requestedKey.trim();
        }
        return "corpus-" + DateTimeFormatter
            .ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneOffset.UTC)
            .format(referenceInstant);
    }

    private int number(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }
}
