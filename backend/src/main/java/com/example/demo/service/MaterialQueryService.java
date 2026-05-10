package com.example.demo.service;

import com.example.demo.service.material.ChunkProfile;
import com.example.demo.service.material.MaterialFormatRegistry;
import com.example.demo.service.material.OcrCapability;
import com.example.demo.service.material.StoredMaterialChunk;
import com.example.demo.service.material.StoredMaterialRecord;
import com.example.demo.service.material.StoredMaterialSegment;
import com.example.demo.service.material.port.MaterialCatalogRepository;
import com.example.demo.service.material.port.MaterialChunkingRepository;
import com.example.demo.service.material.port.OcrCapabilityProvider;
import com.example.demo.service.MaterialRechunkBatchSupport.NormalizedBatchRequest;
import com.example.demo.service.MaterialRechunkBatchSupport.RechunkBatchCursor;

import com.example.demo.error.ApplicationException;
import com.example.demo.error.ErrorType;
import com.example.demo.config.MaterialProperties;
import com.example.demo.config.RolloutProperties;
import com.example.demo.model.MaterialIndexingStatus;
import com.example.demo.model.MaterialChunkDetail;
import com.example.demo.model.MaterialDetail;
import com.example.demo.model.MaterialEnrichmentStatus;
import com.example.demo.model.MaterialListResponse;
import com.example.demo.model.MaterialLineageResponse;
import com.example.demo.model.MaterialLineageVersion;
import com.example.demo.model.MaterialMetadataSnapshot;
import com.example.demo.model.MaterialPdfUploadPolicyResponse;
import com.example.demo.model.MaterialSummary;
import com.example.demo.model.MaterialUploadPolicyResponse;
import com.example.demo.model.MaterialVersionState;
import com.example.demo.model.RechunkActiveMaterialsBatchRequest;
import com.example.demo.model.RechunkActiveMaterialsBatchResponse;
import com.example.demo.model.RechunkActiveMaterialsResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MaterialQueryService {

    private static final Logger logger = LoggerFactory.getLogger(MaterialQueryService.class);

    private static final String SUPERSEDE_REASON_NEW_ACTIVE_VERSION = "material.superseded_by_new_active_version";
    private static final String SUPERSEDE_REASON_REACTIVATED_VERSION = "material.superseded_by_reactivated_version";
    private static final String SUPERSEDE_REASON_LEGACY_FALLBACK = "material.supersede_reason_legacy_unknown";
    private static final int DEFAULT_MATERIAL_LIST_LIMIT = 100;
    private static final int MAX_MATERIAL_LIST_LIMIT = 500;

    private final MaterialCatalogRepository repository;
    private final MaterialChunkingRepository chunkingRepository;
    private final MaterialProperties properties;
    private final MaterialFormatRegistry formatRegistry;
    private final OcrCapabilityProvider ocrCapabilityProvider;
    private final MaterialContentSupport contentSupport;
    private final MaterialSearchSyncLifecycleService lifecycleService;
    private final MaterialIndexingService indexingService;
    private final AfterCommitExecutor afterCommitExecutor;
    private final RolloutProperties rolloutProperties;
    private final MaterialMetadataResolver metadataResolver;
    private final MaterialAutoTaggingLifecycleService autoTaggingLifecycleService;
    private final MaterialRechunkBatchSupport rechunkBatchSupport = new MaterialRechunkBatchSupport();

    @Autowired
    public MaterialQueryService(
        MaterialCatalogRepository repository,
        MaterialChunkingRepository chunkingRepository,
        MaterialProperties properties,
        MaterialFormatRegistry formatRegistry,
        OcrCapabilityProvider ocrCapabilityProvider,
        MaterialContentSupport contentSupport,
        MaterialSearchSyncLifecycleService lifecycleService,
        MaterialIndexingService indexingService,
        AfterCommitExecutor afterCommitExecutor,
        RolloutProperties rolloutProperties,
        MaterialMetadataResolver metadataResolver,
        MaterialAutoTaggingLifecycleService autoTaggingLifecycleService
    ) {
        this.repository = repository;
        this.chunkingRepository = chunkingRepository;
        this.properties = properties;
        this.formatRegistry = formatRegistry;
        this.ocrCapabilityProvider = ocrCapabilityProvider;
        this.contentSupport = contentSupport;
        this.lifecycleService = lifecycleService;
        this.indexingService = indexingService;
        this.afterCommitExecutor = afterCommitExecutor;
        this.rolloutProperties = rolloutProperties == null ? new RolloutProperties() : rolloutProperties;
        this.metadataResolver = Objects.requireNonNull(metadataResolver, "metadataResolver");
        this.autoTaggingLifecycleService = autoTaggingLifecycleService;
    }

    public MaterialQueryService(
        MaterialCatalogRepository repository,
        MaterialChunkingRepository chunkingRepository,
        MaterialProperties properties,
        MaterialFormatRegistry formatRegistry,
        OcrCapabilityProvider ocrCapabilityProvider,
        MaterialContentSupport contentSupport,
        MaterialSearchSyncLifecycleService lifecycleService,
        MaterialIndexingService indexingService,
        AfterCommitExecutor afterCommitExecutor,
        RolloutProperties rolloutProperties,
        MaterialMetadataResolver metadataResolver
    ) {
        this(
            repository,
            chunkingRepository,
            properties,
            formatRegistry,
            ocrCapabilityProvider,
            contentSupport,
            lifecycleService,
            indexingService,
            afterCommitExecutor,
            rolloutProperties,
            metadataResolver,
            null
        );
    }

    public MaterialQueryService(
        MaterialCatalogRepository repository,
        MaterialChunkingRepository chunkingRepository,
        MaterialProperties properties,
        MaterialFormatRegistry formatRegistry,
        OcrCapabilityProvider ocrCapabilityProvider,
        MaterialContentSupport contentSupport,
        MaterialSearchSyncLifecycleService lifecycleService,
        MaterialIndexingService indexingService,
        AfterCommitExecutor afterCommitExecutor,
        MaterialMetadataResolver metadataResolver
    ) {
        this(
            repository,
            chunkingRepository,
            properties,
            formatRegistry,
            ocrCapabilityProvider,
            contentSupport,
            lifecycleService,
            indexingService,
            afterCommitExecutor,
            RolloutProperties.enabledForTests(),
            metadataResolver,
            null
        );
    }

    public List<MaterialSummary> listSummaries() {
        return listSummaries(0, DEFAULT_MATERIAL_LIST_LIMIT);
    }

    public List<MaterialSummary> listSummaries(Integer offset, Integer limit) {
        int normalizedOffset = normalizeMaterialListOffset(offset);
        int normalizedLimit = normalizeMaterialListLimit(limit);
        return withEnrichmentStatuses(repository.findSummaries(normalizedOffset, normalizedLimit));
    }

    private List<MaterialSummary> withEnrichmentStatuses(List<MaterialSummary> summaries) {
        if (autoTaggingLifecycleService == null || summaries == null || summaries.isEmpty()) {
            return summaries;
        }
        Map<String, MaterialEnrichmentStatus> statuses =
            autoTaggingLifecycleService.statusesForMaterials(summaries.stream().map(MaterialSummary::id).toList());
        return summaries.stream()
            .map(summary -> summary.withEnrichmentStatus(statuses.get(summary.id())))
            .toList();
    }

    private MaterialSummary withEnrichmentStatus(MaterialSummary summary) {
        if (summary == null || autoTaggingLifecycleService == null) {
            return summary;
        }
        return summary.withEnrichmentStatus(autoTaggingLifecycleService.statusForMaterial(summary.id()));
    }

    public MaterialListResponse listSummariesPage(Integer offset, Integer limit) {
        return listSummariesPage(offset, limit, null);
    }

    public MaterialListResponse listSummariesPage(Integer offset, Integer limit, String workspaceKey) {
        int normalizedOffset = normalizeMaterialListOffset(offset);
        int normalizedLimit = normalizeMaterialListLimit(limit);
        String normalizedWorkspaceKey = normalizeOptionalWorkspaceKey(workspaceKey);
        List<MaterialSummary> items = normalizedWorkspaceKey == null
            ? repository.findSummaries(normalizedOffset, normalizedLimit)
            : repository.findSummariesByWorkspace(normalizedWorkspaceKey, normalizedOffset, normalizedLimit);
        items = withEnrichmentStatuses(items);
        int total = normalizedWorkspaceKey == null
            ? repository.countMaterials()
            : repository.countMaterialsByWorkspace(normalizedWorkspaceKey);
        boolean hasMore = normalizedOffset + items.size() < total;
        return new MaterialListResponse(items, total, normalizedOffset, normalizedLimit, hasMore);
    }

    public MaterialUploadPolicyResponse getUploadPolicy() {
        OcrCapability ocrCapability = ocrCapabilityProvider.currentCapability();
        return new MaterialUploadPolicyResponse(
            properties.getMaxUploadBytes(),
            formatRegistry.acceptedExtensions(),
            formatRegistry.acceptedMimeHints(),
            formatRegistry.supportsRichDocuments(),
            new MaterialPdfUploadPolicyResponse(
                formatRegistry.isPdfExtension("pdf"),
                ocrCapability.scannedPdfSupport(),
                ocrCapability.mode(),
                ocrCapability.reasonCode(),
                ocrCapability.reasonMessage(),
                ocrCapability.languages(),
                ocrCapability.maxPages()
            )
        );
    }

    public MaterialSummary reindex(String id) {
        StoredMaterialRecord record = repository.findById(id).orElseThrow(() -> new ApplicationException(
            ErrorType.NOT_FOUND,
            "material.not_found",
            "Material '" + id + "' does not exist"
        ));

        if (record.versionState() != MaterialVersionState.ACTIVE) {
            throw new ApplicationException(
                ErrorType.CONFLICT,
                "material.reindex_requires_active_version",
                "Only ACTIVE material versions can be reindexed"
            );
        }

        if (record.status() != MaterialIndexingStatus.FAILED && record.status() != MaterialIndexingStatus.PARTIAL_READY) {
            throw new ApplicationException(
                ErrorType.CONFLICT,
                "material.reindex_not_allowed_for_status",
                "Manual reindex is only available for FAILED or PARTIAL_READY materials"
            );
        }

        Instant now = Instant.now();
        StoredMaterialRecord recordForReindex = refreshMetadataForReindex(record, now);
        String preservedReasonCode = isPartialWarning(recordForReindex) ? recordForReindex.statusReasonCode() : null;
        String preservedReasonMessage = isPartialWarning(recordForReindex) ? recordForReindex.statusReasonMessage() : null;
        StoredMaterialRecord updatedRecord = lifecycleService.markIndexingPending(
            recordForReindex.id(),
            preservedReasonCode,
            preservedReasonMessage,
            now
        );
        afterCommitExecutor.afterCommit(indexingService::requestProcessing);
        return withEnrichmentStatus(contentSupport.toSummary(updatedRecord));
    }

    private StoredMaterialRecord refreshMetadataForReindex(StoredMaterialRecord record, Instant updatedAt) {
        if (!rolloutProperties.isMetadataV1()) {
            return record;
        }

        MaterialMetadataSnapshot refreshedMetadata = metadataResolver.resolve(
            record.metadata().toEditableInputPreservingStoredFields(),
            record.title(),
            record.sourceType(),
            record.originalFileName(),
            record.mediaType(),
            record.content()
        );
        StoredMaterialRecord updatedRecord = repository.updateMetadata(record.id(), refreshedMetadata, updatedAt);
        return updatedRecord == null ? record : updatedRecord;
    }

    public RechunkActiveMaterialsResponse rechunkActiveMaterials() {
        ensureStructuredRolloutEnabled();
        int totalActive = repository.countActiveMaterials();
        int scheduledCount = 0;
        int alreadyCurrentCount = 0;
        int legacyBestEffortCount = 0;
        RechunkBatchCursor cursor = null;

        while (true) {
            ProcessedBatch batch = processRechunkBatch(
                cursor,
                MaterialRechunkBatchSupport.MAX_RECHUNK_BATCH_LIMIT,
                false,
                totalActive
            );
            scheduledCount += batch.scheduled();
            alreadyCurrentCount += batch.alreadyCurrent();
            legacyBestEffortCount += batch.legacyBestEffort();

            if (batch.nextCursor() == null) {
                break;
            }
            cursor = batch.nextCursor();
        }

        if (scheduledCount > 0) {
            afterCommitExecutor.afterCommit(indexingService::requestProcessing);
        }

        logger.info(
            "ACTIVE backfill completed: totalActive={} scheduled={} alreadyCurrent={} legacyBestEffort={}",
            totalActive,
            scheduledCount,
            alreadyCurrentCount,
            legacyBestEffortCount
        );

        return new RechunkActiveMaterialsResponse(
            totalActive,
            scheduledCount,
            alreadyCurrentCount,
            legacyBestEffortCount
        );
    }

    public RechunkActiveMaterialsBatchResponse rechunkActiveMaterialsBatch(RechunkActiveMaterialsBatchRequest request) {
        ensureStructuredRolloutEnabled();
        NormalizedBatchRequest normalizedRequest = rechunkBatchSupport.normalizeBatchRequest(request);
        int totalActive = repository.countActiveMaterials();
        ProcessedBatch batch = processRechunkBatch(
            normalizedRequest.cursor(),
            normalizedRequest.limit(),
            normalizedRequest.dryRun(),
            totalActive
        );

        if (!normalizedRequest.dryRun() && batch.scheduled() > 0) {
            afterCommitExecutor.afterCommit(indexingService::requestProcessing);
        }

        logger.info(
            "ACTIVE backfill batch: totalActive={} scanned={} scheduled={} alreadyCurrent={} legacyBestEffort={} dryRun={} nextCursor={}",
            batch.totalActive(),
            batch.scanned(),
            batch.scheduled(),
            batch.alreadyCurrent(),
            batch.legacyBestEffort(),
            normalizedRequest.dryRun(),
            rechunkBatchSupport.encodeCursor(batch.nextCursor())
        );

        return new RechunkActiveMaterialsBatchResponse(
            batch.totalActive(),
            batch.scanned(),
            batch.scheduled(),
            batch.alreadyCurrent(),
            batch.legacyBestEffort(),
            rechunkBatchSupport.encodeCursor(batch.nextCursor()),
            batch.materialIds()
        );
    }

    public MaterialLineageResponse getLineage(String id) {
        StoredMaterialRecord requestedRecord = repository.findById(id).orElseThrow(() -> new ApplicationException(
            ErrorType.NOT_FOUND,
            "material.not_found",
            "Material '" + id + "' does not exist"
        ));

        List<StoredMaterialRecord> lineageRecords = repository.findAllBySourceKey(requestedRecord.sourceKey()).stream()
            .sorted(Comparator
                .comparing((StoredMaterialRecord record) -> record.versionState() == MaterialVersionState.ACTIVE ? 0 : 1)
                .thenComparing(StoredMaterialRecord::lineageVersion, Comparator.reverseOrder())
                .thenComparing(StoredMaterialRecord::createdAt, Comparator.reverseOrder()))
            .toList();

        String activeMaterialId = lineageRecords.stream()
            .filter(record -> record.versionState() == MaterialVersionState.ACTIVE)
            .map(StoredMaterialRecord::id)
            .findFirst()
            .orElse(null);

        List<MaterialLineageVersion> versions = lineageRecords.stream()
            .map(record -> contentSupport.toLineageVersion(record, SUPERSEDE_REASON_LEGACY_FALLBACK))
            .toList();

        return new MaterialLineageResponse(requestedRecord.id(), activeMaterialId, versions);
    }

    public MaterialDetail getDetail(String id) {
        StoredMaterialRecord record = repository.findById(id).orElseThrow(() -> new ApplicationException(
            ErrorType.NOT_FOUND,
            "material.not_found",
            "Material '" + id + "' does not exist"
        ));

        List<MaterialChunkDetail> chunks = chunkingRepository.findChunks(id).stream()
            .map(chunk -> new MaterialChunkDetail(
                id + ":" + chunk.index(),
                chunk.index(),
                chunk.text(),
                chunk.page(),
                chunk.extractor(),
                Boolean.TRUE.equals(chunk.ocrUsed())
            ))
            .toList();

        return new MaterialDetail(
            record.id(),
            record.title(),
            record.sourceType(),
            record.originalFileName(),
            record.mediaType(),
            record.content(),
            record.status(),
            record.versionState(),
            record.createdAt(),
            record.updatedAt(),
            record.metadata(),
            chunks,
            autoTaggingLifecycleService == null
                ? null
                : autoTaggingLifecycleService.statusForMaterial(record.id())
        );
    }

    public void delete(String id) {
        lifecycleService.delete(id, Instant.now());
    }

    public void requireMaterialInWorkspace(String id, String workspaceKey) {
        String normalizedWorkspaceKey = normalizeOptionalWorkspaceKey(workspaceKey);
        if (normalizedWorkspaceKey == null) {
            return;
        }
        StoredMaterialRecord record = repository.findById(id).orElseThrow(() -> new ApplicationException(
            ErrorType.NOT_FOUND,
            "material.not_found",
            "Material '" + id + "' does not exist"
        ));
        String recordWorkspaceKey = normalizeOptionalWorkspaceKey(record.metadata().workspaceKey());
        if (!normalizedWorkspaceKey.equals(recordWorkspaceKey)) {
            throw new ApplicationException(
                ErrorType.NOT_FOUND,
                "material.not_found",
                "Material '" + id + "' does not exist"
            );
        }
    }

    public String supersedeReasonForNewActiveVersion() {
        return SUPERSEDE_REASON_NEW_ACTIVE_VERSION;
    }

    public String supersedeReasonForReactivatedVersion() {
        return SUPERSEDE_REASON_REACTIVATED_VERSION;
    }

    private ProcessedBatch processRechunkBatch(RechunkBatchCursor cursor, int limit, boolean dryRun, int totalActive) {
        ChunkProfile targetProfile = contentSupport.configuredChunkProfile(rolloutProperties.isStructuredV1());
        Instant now = Instant.now();
        List<StoredMaterialRecord> batchWindow = repository.findActivePageAfter(
            cursor == null ? null : cursor.createdAt(),
            cursor == null ? null : cursor.id(),
            limit + 1
        );
        boolean hasMore = batchWindow.size() > limit;
        List<StoredMaterialRecord> scannedRecords = hasMore ? batchWindow.subList(0, limit) : batchWindow;

        int scheduledCount = 0;
        int alreadyCurrentCount = 0;
        int legacyBestEffortCount = 0;
        List<String> materialIds = new ArrayList<>();

        for (StoredMaterialRecord record : scannedRecords) {
            RechunkPreparation preparation = prepareRechunk(record, targetProfile);
            if (preparation.alreadyCurrent()) {
                alreadyCurrentCount += 1;
                continue;
            }

            scheduledCount += 1;
            materialIds.add(record.id());
            if (preparation.legacyBestEffort()) {
                legacyBestEffortCount += 1;
            }

            if (!dryRun) {
                String preservedReasonCode = isPartialWarning(record) ? record.statusReasonCode() : null;
                String preservedReasonMessage = isPartialWarning(record) ? record.statusReasonMessage() : null;
                lifecycleService.replaceChunkingAndMarkIndexingPending(
                    record.id(),
                    targetProfile.propertyValue(),
                    preparation.chunks(),
                    preparation.segments(),
                    preservedReasonCode,
                    preservedReasonMessage,
                    now
                );
            }
        }

        RechunkBatchCursor nextCursor = hasMore && !scannedRecords.isEmpty()
            ? new RechunkBatchCursor(scannedRecords.getLast().createdAt(), scannedRecords.getLast().id())
            : null;

        return new ProcessedBatch(
            totalActive,
            scannedRecords.size(),
            scheduledCount,
            alreadyCurrentCount,
            legacyBestEffortCount,
            nextCursor,
            List.copyOf(materialIds)
        );
    }

    private void ensureStructuredRolloutEnabled() {
        if (rolloutProperties.isStructuredV1()) {
            return;
        }
        throw new ApplicationException(
            ErrorType.CONFLICT,
            "material.structured_rollout_disabled",
            "Structured chunking rollout is disabled."
        );
    }

    private int normalizeMaterialListOffset(Integer offset) {
        if (offset == null) {
            return 0;
        }
        if (offset < 0) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
                "material.invalid_list_offset",
                "Material list offset must be zero or greater"
            );
        }
        return offset;
    }

    private int normalizeMaterialListLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_MATERIAL_LIST_LIMIT;
        }
        if (limit <= 0) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
                "material.invalid_list_limit",
                "Material list limit must be greater than zero"
            );
        }
        if (limit > MAX_MATERIAL_LIST_LIMIT) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
                "material.list_limit_too_large",
                "Material list limit must not exceed " + MAX_MATERIAL_LIST_LIMIT
            );
        }
        return limit;
    }

    private String normalizeOptionalWorkspaceKey(String workspaceKey) {
        if (!StringUtils.hasText(workspaceKey)) {
            return null;
        }
        return workspaceKey.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private RechunkPreparation prepareRechunk(StoredMaterialRecord record, ChunkProfile targetProfile) {
        ChunkProfile currentProfile = contentSupport.resolveChunkProfile(chunkingRepository.findChunkProfile(record.id()));
        if (currentProfile == targetProfile) {
            return RechunkPreparation.forAlreadyCurrent();
        }

        List<StoredMaterialSegment> storedSegments = chunkingRepository.findSegments(record.id());
        boolean bestEffortLegacyFile = false;
        if (storedSegments.isEmpty()) {
            List<StoredMaterialChunk> rawChunks = chunkingRepository.findChunks(record.id());
            if ("file".equalsIgnoreCase(record.sourceType())) {
                storedSegments = contentSupport.pseudoSegmentsFromChunks(
                    rawChunks,
                    record.extractor(),
                    Boolean.TRUE.equals(record.ocrUsed())
                );
                if (storedSegments.isEmpty()) {
                    storedSegments = contentSupport.singleSegment(
                        record.content(),
                        record.extractor(),
                        Boolean.TRUE.equals(record.ocrUsed())
                    );
                }
                bestEffortLegacyFile = true;
            } else if (!rawChunks.isEmpty()) {
                storedSegments = contentSupport.pseudoSegmentsFromChunks(
                    rawChunks,
                    record.extractor(),
                    Boolean.TRUE.equals(record.ocrUsed())
                );
            } else {
                storedSegments = contentSupport.singleSegment(
                    record.content(),
                    record.extractor(),
                    Boolean.TRUE.equals(record.ocrUsed())
                );
            }
        }

        return RechunkPreparation.forRechunk(
            contentSupport.buildChunks(storedSegments, targetProfile),
            storedSegments,
            bestEffortLegacyFile
        );
    }

    private boolean isPartialWarning(StoredMaterialRecord record) {
        return record.status() == MaterialIndexingStatus.PARTIAL_READY
            && StringUtils.hasText(record.statusReasonCode())
            && record.statusReasonCode().startsWith("material.partial_");
    }

    private record RechunkPreparation(
        boolean alreadyCurrent,
        List<StoredMaterialChunk> chunks,
        List<StoredMaterialSegment> segments,
        boolean legacyBestEffort
    ) {
        private static RechunkPreparation forAlreadyCurrent() {
            return new RechunkPreparation(true, List.of(), List.of(), false);
        }

        private static RechunkPreparation forRechunk(
            List<StoredMaterialChunk> chunks,
            List<StoredMaterialSegment> segments,
            boolean legacyBestEffort
        ) {
            return new RechunkPreparation(false, chunks, segments, legacyBestEffort);
        }
    }

    private record ProcessedBatch(
        int totalActive,
        int scanned,
        int scheduled,
        int alreadyCurrent,
        int legacyBestEffort,
        RechunkBatchCursor nextCursor,
        List<String> materialIds
    ) {
    }
}
