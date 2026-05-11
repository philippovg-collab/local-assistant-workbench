package com.example.demo.service;

import com.example.demo.config.MaterialProperties;
import com.example.demo.config.RolloutProperties;
import com.example.demo.error.ApplicationException;
import com.example.demo.error.ErrorType;
import com.example.demo.model.MaterialIndexingStatus;
import com.example.demo.model.MaterialLineageOverrideInput;
import com.example.demo.model.MaterialMetadataSnapshot;
import com.example.demo.model.MaterialSummary;
import com.example.demo.model.MaterialVersionState;
import com.example.demo.service.material.ChunkProfile;
import com.example.demo.service.material.DocumentParseResult;
import com.example.demo.service.material.MaterialLineageIdentity;
import com.example.demo.service.material.StoredMaterialChunk;
import com.example.demo.service.material.StoredMaterialRecord;
import com.example.demo.service.material.StoredMaterialSegment;
import com.example.demo.service.material.port.MaterialCatalogRepository;
import com.example.demo.service.material.port.MaterialLineageRepository;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

final class MaterialPersistenceCoordinator {

    private static final String SUPERSEDE_REASON_NEW_ACTIVE_VERSION = "material.superseded_by_new_active_version";
    private static final String SUPERSEDE_REASON_REACTIVATED_VERSION = "material.superseded_by_reactivated_version";

    enum DuplicateContentBehavior {
        REUSE_OR_REACTIVATE,
        REJECT,
        ALLOW_NEW_VERSION
    }

    private record PersistMaterialResult(
        MaterialSummary summary,
        boolean autoTagEligible,
        String materialId,
        String contentHash
    ) {
        PersistMaterialResult withSummary(MaterialSummary updatedSummary) {
            return new PersistMaterialResult(updatedSummary, autoTagEligible, materialId, contentHash);
        }
    }

    private final MaterialCatalogRepository catalogRepository;
    private final MaterialLineageRepository lineageRepository;
    private final MaterialProperties properties;
    private final MaterialContentSupport contentSupport;
    private final MaterialSearchSyncLifecycleService lifecycleService;
    private final MaterialIndexingService indexingService;
    private final AfterCommitExecutor afterCommitExecutor;
    private final RolloutProperties rolloutProperties;
    private final StructuredV1ProofService structuredV1ProofService;
    private final MaterialAutoTaggingLifecycleService autoTaggingLifecycleService;
    private final MaterialAutoTaggingWorkerService autoTaggingWorkerService;
    private final TransactionTemplate transactionTemplate;

    MaterialPersistenceCoordinator(
        MaterialCatalogRepository catalogRepository,
        MaterialLineageRepository lineageRepository,
        MaterialProperties properties,
        MaterialContentSupport contentSupport,
        MaterialSearchSyncLifecycleService lifecycleService,
        MaterialIndexingService indexingService,
        AfterCommitExecutor afterCommitExecutor,
        RolloutProperties rolloutProperties,
        StructuredV1ProofService structuredV1ProofService,
        MaterialAutoTaggingLifecycleService autoTaggingLifecycleService,
        MaterialAutoTaggingWorkerService autoTaggingWorkerService,
        PlatformTransactionManager transactionManager
    ) {
        this.catalogRepository = catalogRepository;
        this.lineageRepository = lineageRepository;
        this.properties = properties;
        this.contentSupport = contentSupport;
        this.lifecycleService = lifecycleService;
        this.indexingService = indexingService;
        this.afterCommitExecutor = afterCommitExecutor;
        this.rolloutProperties = rolloutProperties;
        this.structuredV1ProofService = structuredV1ProofService == null
            ? StructuredV1ProofService.allowAllForTests(rolloutProperties)
            : structuredV1ProofService;
        this.autoTaggingLifecycleService = autoTaggingLifecycleService;
        this.autoTaggingWorkerService = autoTaggingWorkerService;
        this.transactionTemplate = transactionManager == null ? null : new TransactionTemplate(transactionManager);
    }

    MaterialSummary persistMaterial(
        String title,
        String sourceType,
        String originalFileName,
        String mediaType,
        String lineageTitle,
        MaterialMetadataSnapshot metadata,
        DocumentParseResult document
    ) {
        return persistMaterial(
            title,
            sourceType,
            originalFileName,
            mediaType,
            lineageTitle,
            metadata,
            document,
            null,
            null,
            true,
            DuplicateContentBehavior.REUSE_OR_REACTIVATE
        );
    }

    MaterialSummary persistMaterial(
        String title,
        String sourceType,
        String originalFileName,
        String mediaType,
        String lineageTitle,
        MaterialMetadataSnapshot metadata,
        DocumentParseResult document,
        MaterialLineageOverrideInput lineageOverride,
        String forcedSourceKey,
        boolean inheritManualTagsWhenEmpty,
        DuplicateContentBehavior duplicateContentBehavior
    ) {
        List<StoredMaterialSegment> normalizedSegments = contentSupport.toStoredSegments(document);
        String storedContent = contentSupport.joinBlocks(document);
        if (!StringUtils.hasText(storedContent)) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
                "material.empty_content",
                "Material content is empty after normalization"
            );
        }

        if (storedContent.length() > properties.getMaxTextChars()) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
                "material.content_too_large",
                "Material content exceeds the configured text limit"
            );
        }

        structuredV1ProofService.requireWriteAllowed();
        String normalizedContent = contentSupport.normalizeForHash(storedContent);
        String contentHash = contentSupport.sha256(normalizedContent);
        MaterialLineageIdentity lineageIdentity = StringUtils.hasText(forcedSourceKey)
            ? null
            : contentSupport.buildLineageIdentity(sourceType, lineageTitle, originalFileName, storedContent);
        ChunkProfile chunkProfile = contentSupport.configuredChunkProfile(rolloutProperties.isStructuredV1());
        List<StoredMaterialChunk> initialChunks = contentSupport.buildChunks(document, chunkProfile);
        List<StoredMaterialChunk> rawChunks = initialChunks.isEmpty()
            ? contentSupport.buildChunks(normalizedSegments, chunkProfile)
            : initialChunks;
        return inTransaction(() -> persistPreparedMaterial(
            title,
            sourceType,
            originalFileName,
            mediaType,
            storedContent,
            normalizedContent,
            contentHash,
            forcedSourceKey,
            lineageIdentity,
            lineageOverride,
            metadata,
            document,
            chunkProfile,
            rawChunks,
            normalizedSegments,
            inheritManualTagsWhenEmpty,
            duplicateContentBehavior
        )).summary();
    }

    private PersistMaterialResult persistPreparedMaterial(
        String title,
        String sourceType,
        String originalFileName,
        String mediaType,
        String storedContent,
        String normalizedContent,
        String contentHash,
        String forcedSourceKey,
        MaterialLineageIdentity lineageIdentity,
        MaterialLineageOverrideInput lineageOverride,
        MaterialMetadataSnapshot metadata,
        DocumentParseResult document,
        ChunkProfile chunkProfile,
        List<StoredMaterialChunk> rawChunks,
        List<StoredMaterialSegment> normalizedSegments,
        boolean inheritManualTagsWhenEmpty,
        DuplicateContentBehavior duplicateContentBehavior
    ) {
        String sourceKey = resolveSourceKey(forcedSourceKey, lineageIdentity, lineageOverride);
        lineageRepository.lockLineage(sourceKey);

        java.util.Optional<StoredMaterialRecord> existingRecord =
            catalogRepository.findBySourceKeyAndContentHash(sourceKey, contentHash);
        if (existingRecord.isPresent()) {
            if (duplicateContentBehavior == DuplicateContentBehavior.REJECT) {
                throw new ApplicationException(
                    ErrorType.CONFLICT,
                    "material.version_duplicate_content",
                    "This content already exists in the selected material lineage"
                );
            }
            if (duplicateContentBehavior == DuplicateContentBehavior.ALLOW_NEW_VERSION) {
                return withAutoTaggingStatus(saveNewMaterial(
                    title,
                    sourceType,
                    originalFileName,
                    mediaType,
                    storedContent,
                    normalizedContent,
                    contentHash,
                    sourceKey,
                    metadata,
                    document,
                    chunkProfile,
                    rawChunks,
                    normalizedSegments
                ));
            }
            return withAutoTaggingStatus(handleExistingMaterial(existingRecord.get(), sourceKey));
        }

        MaterialMetadataSnapshot metadataForNewMaterial = inheritManualTagsWhenEmpty
            ? inheritManualTagsIfNeeded(metadata, sourceKey)
            : metadata;
        return withAutoTaggingStatus(saveNewMaterial(
            title,
            sourceType,
            originalFileName,
            mediaType,
            storedContent,
            normalizedContent,
            contentHash,
            sourceKey,
            metadataForNewMaterial,
            document,
            chunkProfile,
            rawChunks,
            normalizedSegments
        ));
    }

    private MaterialMetadataSnapshot inheritManualTagsIfNeeded(MaterialMetadataSnapshot metadata, String sourceKey) {
        if (!rolloutProperties.isMetadataV1() || metadata == null || !metadata.manualTags().isEmpty()) {
            return metadata;
        }
        return catalogRepository.findLatestBySourceKeyAndVersionState(sourceKey, MaterialVersionState.ACTIVE)
            .map(StoredMaterialRecord::metadata)
            .map(MaterialMetadataSnapshot::manualTags)
            .filter(tags -> !tags.isEmpty())
            .map(metadata::withManualTags)
            .orElse(metadata);
    }

    private String resolveSourceKey(
        String forcedSourceKey,
        MaterialLineageIdentity lineageIdentity,
        MaterialLineageOverrideInput lineageOverride
    ) {
        if (StringUtils.hasText(forcedSourceKey)) {
            return forcedSourceKey.trim();
        }

        MaterialLineageIdentity naturalIdentity = Objects.requireNonNull(lineageIdentity, "lineageIdentity");
        if (lineageOverride == null || !StringUtils.hasText(lineageOverride.lineageKey())) {
            return lineageRepository.resolveSourceKey(naturalIdentity);
        }

        String lineageKey = contentSupport.normalizeOperatorLineageKey(lineageOverride.lineageKey());
        if (!StringUtils.hasText(lineageKey)) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
                "material.lineage_override_key_required",
                "Lineage override key is required."
            );
        }
        if (!StringUtils.hasText(lineageOverride.reason())) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
                "material.lineage_override_reason_required",
                "Lineage override reason is required."
            );
        }

        String reason = lineageOverride.reason().trim();
        String naturalSourceKey = naturalIdentity.sourceKey();
        boolean naturalLineageExists = !catalogRepository.findAllBySourceKey(naturalSourceKey).isEmpty();
        MaterialLineageIdentity operatorIdentity = contentSupport.buildOperatorLineageIdentity(lineageKey);
        String targetSourceKey = operatorIdentity.sourceKey();
        lineageRepository.lockLineage("operator-override:" + lineageKey);
        lineageRepository.lockLineage(targetSourceKey);

        java.util.Optional<com.example.demo.service.material.MaterialLineageOperatorOverride> existingOverride =
            lineageRepository.findActiveOverride(lineageKey);
        if (existingOverride.isPresent()) {
            String overrideSourceKey = existingOverride.get().sourceKey();
            if (!targetSourceKey.equals(overrideSourceKey)
                || naturalLineageExists && !naturalSourceKey.equals(overrideSourceKey)) {
                throw new ApplicationException(
                    ErrorType.CONFLICT,
                    "material.lineage_override_collision",
                    "Lineage override would merge two existing material lineages."
                );
            }
            boolean overrideLineageExists = !catalogRepository.findAllBySourceKey(overrideSourceKey).isEmpty();
            if (overrideLineageExists && !lineageOverride.confirmed()) {
                throw new ApplicationException(
                    ErrorType.CONFLICT,
                    "material.lineage_override_confirmation_required",
                    "Reusing an existing lineage override requires explicit confirmation."
                );
            }
            return overrideSourceKey;
        }

        boolean overrideLineageExists = !catalogRepository.findAllBySourceKey(targetSourceKey).isEmpty();
        if (overrideLineageExists && !lineageOverride.confirmed()) {
            throw new ApplicationException(
                ErrorType.CONFLICT,
                "material.lineage_override_confirmation_required",
                "Reusing an existing lineage override requires explicit confirmation."
            );
        }
        targetSourceKey = lineageRepository.resolveSourceKey(operatorIdentity);

        return lineageRepository.saveOverride(
            lineageKey,
            targetSourceKey,
            reason,
            "operator",
            Instant.now()
        ).sourceKey();
    }

    private PersistMaterialResult saveNewMaterial(
        String title,
        String sourceType,
        String originalFileName,
        String mediaType,
        String storedContent,
        String normalizedContent,
        String contentHash,
        String sourceKey,
        MaterialMetadataSnapshot metadata,
        DocumentParseResult document,
        ChunkProfile chunkProfile,
        List<StoredMaterialChunk> rawChunks,
        List<StoredMaterialSegment> normalizedSegments
    ) {
        Instant now = Instant.now();
        StoredMaterialRecord record = new StoredMaterialRecord(
            UUID.randomUUID().toString(),
            title,
            sourceType,
            originalFileName,
            mediaType,
            storedContent,
            normalizedContent,
            contentHash,
            sourceKey,
            contentSupport.normalizeExtractor(document.extractor()),
            document.ocrUsed(),
            document.pageCount(),
            rawChunks,
            MaterialIndexingStatus.PENDING,
            MaterialVersionState.ACTIVE,
            document.firstWarningCode(),
            document.firstWarningMessage(),
            now,
            now,
            metadata
        );

        StoredMaterialRecord savedRecord = lifecycleService.saveNewActiveMaterial(
            record,
            chunkProfile.propertyValue(),
            rawChunks,
            normalizedSegments,
            SUPERSEDE_REASON_NEW_ACTIVE_VERSION,
            now
        );
        if (savedRecord.id().equals(record.id())) {
            afterCommitExecutor.afterCommit(indexingService::requestProcessing);
        }
        return new PersistMaterialResult(
            contentSupport.toSummary(savedRecord),
            savedRecord.id().equals(record.id()),
            savedRecord.id(),
            savedRecord.contentHash()
        );
    }

    private PersistMaterialResult handleExistingMaterial(StoredMaterialRecord existingRecord, String sourceKey) {
        Instant now = Instant.now();
        StoredMaterialRecord resolvedRecord = existingRecord;

        if (existingRecord.sourceKey().equals(sourceKey)
            && existingRecord.versionState() == MaterialVersionState.SUPERSEDED) {
            resolvedRecord = lifecycleService.reactivateVersion(
                existingRecord,
                SUPERSEDE_REASON_REACTIVATED_VERSION,
                now
            );
        }

        if (resolvedRecord.status() == MaterialIndexingStatus.FAILED) {
            StoredMaterialRecord requeuedRecord = lifecycleService.markIndexingPending(
                resolvedRecord.id(),
                null,
                null,
                now
            );
            afterCommitExecutor.afterCommit(indexingService::requestProcessing);
            return new PersistMaterialResult(
                contentSupport.toSummary(requeuedRecord),
                false,
                requeuedRecord.id(),
                requeuedRecord.contentHash()
            );
        }

        return new PersistMaterialResult(
            contentSupport.toSummary(resolvedRecord),
            false,
            resolvedRecord.id(),
            resolvedRecord.contentHash()
        );
    }

    private PersistMaterialResult inTransaction(java.util.function.Supplier<PersistMaterialResult> action) {
        if (transactionTemplate == null) {
            return action.get();
        }
        return transactionTemplate.execute(status -> action.get());
    }

    private PersistMaterialResult withAutoTaggingStatus(PersistMaterialResult result) {
        if (result == null || autoTaggingLifecycleService == null || !rolloutProperties.isMetadataV1()) {
            return result;
        }
        if (!result.autoTagEligible()) {
            return result.withSummary(result.summary().withEnrichmentStatus(
                autoTaggingLifecycleService.statusForMaterial(result.materialId())
            ));
        }

        MaterialSummary summary = result.summary().withEnrichmentStatus(
            autoTaggingLifecycleService.enqueue(result.materialId(), result.contentHash(), Instant.now())
        );
        if (autoTaggingWorkerService != null) {
            afterCommitExecutor.afterCommit(autoTaggingWorkerService::requestProcessing);
        }
        return result.withSummary(summary);
    }
}
