package com.example.demo.service;

import com.example.demo.service.material.ChunkProfile;
import com.example.demo.service.material.DocumentBlock;
import com.example.demo.service.material.DocumentBlockConfidence;
import com.example.demo.service.material.DocumentBlockType;
import com.example.demo.service.material.DocumentParseResult;
import com.example.demo.service.material.DocumentParserProfile;
import com.example.demo.service.material.MaterialLineageIdentity;
import com.example.demo.service.material.MaterialMetadataHints;
import com.example.demo.service.material.StoredMaterialChunk;
import com.example.demo.service.material.StoredMaterialRecord;
import com.example.demo.service.material.StoredMaterialSegment;
import com.example.demo.service.material.port.DocumentTextExtractor;
import com.example.demo.service.material.port.MaterialCatalogRepository;
import com.example.demo.service.material.port.MaterialLineageRepository;

import com.example.demo.api.ApiException;
import com.example.demo.api.InputLimits;
import com.example.demo.config.MaterialProperties;
import com.example.demo.config.RolloutProperties;
import com.example.demo.model.MaterialIndexingStatus;
import com.example.demo.model.MaterialMetadataInput;
import com.example.demo.model.MaterialMetadataSnapshot;
import com.example.demo.model.MaterialSummary;
import com.example.demo.model.MaterialVersionState;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MaterialIngestionService {

    private static final Logger logger = LoggerFactory.getLogger(MaterialIngestionService.class);
    private static final String SUPERSEDE_REASON_NEW_ACTIVE_VERSION = "material.superseded_by_new_active_version";
    private static final String SUPERSEDE_REASON_REACTIVATED_VERSION = "material.superseded_by_reactivated_version";

    private enum DuplicateContentBehavior {
        REUSE_OR_REACTIVATE,
        REJECT,
        ALLOW_NEW_VERSION
    }

    private record PersistMaterialResult(
        MaterialSummary summary,
        boolean autoTagEligible
    ) {
    }

    private record AutoTaggingContext(
        String materialId,
        String title,
        String sourceType,
        String originalFileName,
        String mediaType,
        String contentText,
        MaterialMetadataHints parserHints
    ) {
        AutoTaggingContext {
            parserHints = parserHints == null ? MaterialMetadataHints.empty() : parserHints;
        }
    }

    private final MaterialCatalogRepository catalogRepository;
    private final MaterialLineageRepository lineageRepository;
    private final DocumentTextExtractor extractor;
    private final MaterialProperties properties;
    private final MaterialContentSupport contentSupport;
    private final MaterialMetadataResolver metadataResolver;
    private final MaterialSearchSyncLifecycleService lifecycleService;
    private final MaterialIndexingService indexingService;
    private final AfterCommitExecutor afterCommitExecutor;
    private final RolloutProperties rolloutProperties;
    private final MaterialAutoTaggingService autoTaggingService;
    private final TransactionTemplate persistenceTransactionTemplate;
    private final Executor autoTaggingExecutor;

    @Autowired
    public MaterialIngestionService(
        MaterialCatalogRepository catalogRepository,
        MaterialLineageRepository lineageRepository,
        DocumentTextExtractor extractor,
        MaterialProperties properties,
        MaterialContentSupport contentSupport,
        MaterialMetadataResolver metadataResolver,
        MaterialSearchSyncLifecycleService lifecycleService,
        MaterialIndexingService indexingService,
        AfterCommitExecutor afterCommitExecutor,
        RolloutProperties rolloutProperties,
        MaterialAutoTaggingService autoTaggingService,
        PlatformTransactionManager transactionManager,
        @Qualifier("materialAutoTaggingExecutor") Executor autoTaggingExecutor
    ) {
        this.catalogRepository = catalogRepository;
        this.lineageRepository = lineageRepository;
        this.extractor = extractor;
        this.properties = properties;
        this.contentSupport = contentSupport;
        this.metadataResolver = metadataResolver;
        this.lifecycleService = lifecycleService;
        this.indexingService = indexingService;
        this.afterCommitExecutor = afterCommitExecutor;
        this.rolloutProperties = rolloutProperties == null ? new RolloutProperties() : rolloutProperties;
        this.autoTaggingService = autoTaggingService;
        this.persistenceTransactionTemplate = transactionManager == null ? null : new TransactionTemplate(transactionManager);
        this.autoTaggingExecutor = autoTaggingExecutor == null ? Runnable::run : autoTaggingExecutor;
    }

    public MaterialIngestionService(
        MaterialCatalogRepository catalogRepository,
        MaterialLineageRepository lineageRepository,
        DocumentTextExtractor extractor,
        MaterialProperties properties,
        MaterialContentSupport contentSupport,
        MaterialMetadataResolver metadataResolver,
        MaterialSearchSyncLifecycleService lifecycleService,
        MaterialIndexingService indexingService,
        AfterCommitExecutor afterCommitExecutor,
        RolloutProperties rolloutProperties,
        MaterialAutoTaggingService autoTaggingService
    ) {
        this(
            catalogRepository,
            lineageRepository,
            extractor,
            properties,
            contentSupport,
            metadataResolver,
            lifecycleService,
            indexingService,
            afterCommitExecutor,
            rolloutProperties,
            autoTaggingService,
            null,
            Runnable::run
        );
    }

    public MaterialIngestionService(
        MaterialCatalogRepository catalogRepository,
        MaterialLineageRepository lineageRepository,
        DocumentTextExtractor extractor,
        MaterialProperties properties,
        MaterialContentSupport contentSupport,
        MaterialMetadataResolver metadataResolver,
        MaterialSearchSyncLifecycleService lifecycleService,
        MaterialIndexingService indexingService,
        AfterCommitExecutor afterCommitExecutor,
        RolloutProperties rolloutProperties
    ) {
        this(
            catalogRepository,
            lineageRepository,
            extractor,
            properties,
            contentSupport,
            metadataResolver,
            lifecycleService,
            indexingService,
            afterCommitExecutor,
            rolloutProperties,
            null,
            null,
            Runnable::run
        );
    }

    public MaterialIngestionService(
        MaterialCatalogRepository catalogRepository,
        MaterialLineageRepository lineageRepository,
        DocumentTextExtractor extractor,
        MaterialProperties properties,
        MaterialContentSupport contentSupport,
        MaterialMetadataResolver metadataResolver,
        MaterialSearchSyncLifecycleService lifecycleService,
        MaterialIndexingService indexingService,
        AfterCommitExecutor afterCommitExecutor
    ) {
        this(
            catalogRepository,
            lineageRepository,
            extractor,
            properties,
            contentSupport,
            metadataResolver,
            lifecycleService,
            indexingService,
            afterCommitExecutor,
            RolloutProperties.enabledForTests(),
            null,
            null,
            Runnable::run
        );
    }

    public MaterialSummary saveText(String title, String content, MaterialMetadataInput metadataInput) {
        InputLimits.validateMaterialMetadata(metadataInput);
        if (!StringUtils.hasText(content)) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "material.empty_text",
                "Material text is empty"
            );
        }

        String resolvedTitle = StringUtils.hasText(title) ? title.trim() : "Text material";
        String lineageTitle = StringUtils.hasText(title) ? title.trim() : null;
        DocumentParseResult parseResult = new DocumentParseResult(
            List.of(new DocumentBlock(
                0,
                DocumentBlockType.NARRATIVE,
                content,
                null,
                "direct-text",
                false,
                DocumentBlockConfidence.HIGH,
                null
            )),
            MaterialMetadataHints.empty(),
            List.of(),
            null,
            "direct-text",
            false,
            DocumentParserProfile.RICH_TEXT
        );
        try {
            String contentText = contentSupport.joinBlocks(parseResult);
            PersistMaterialResult result = persistMaterial(
                resolvedTitle,
                "text",
                null,
                "text/plain",
                lineageTitle,
                resolveMetadata(
                    metadataInput,
                    resolvedTitle,
                    "text",
                    null,
                    "text/plain",
                    contentSupport.headerTextForHints(parseResult),
                    contentText,
                    parseResult.metadataHints()
                ),
                parseResult
            );
            scheduleAutoTagging(result, new AutoTaggingContext(
                result.summary().id(),
                resolvedTitle,
                "text",
                null,
                "text/plain",
                contentText,
                parseResult.metadataHints()
            ));
            return result.summary();
        } catch (ApiException exception) {
            logKnownMaterialFailure("persist", "text", resolvedTitle, null, "text/plain", exception);
            throw exception;
        } catch (RuntimeException exception) {
            logUnexpectedMaterialFailure("persist", "text", resolvedTitle, null, "text/plain", exception);
            throw exception;
        }
    }

    public MaterialSummary saveUpload(String title, MultipartFile file, MaterialMetadataInput metadataInput) {
        return saveUploadInternal(
            title,
            file,
            metadataInput,
            null,
            null,
            true,
            DuplicateContentBehavior.REUSE_OR_REACTIVATE
        );
    }

    public MaterialSummary saveUploadVersion(
        String materialId,
        String title,
        MultipartFile file,
        MaterialMetadataInput metadataInput
    ) {
        StoredMaterialRecord targetRecord = catalogRepository.findById(materialId).orElseThrow(() -> new ApiException(
            HttpStatus.NOT_FOUND,
            "material.not_found",
            "Material '" + materialId + "' does not exist"
        ));
        if (targetRecord.versionState() != MaterialVersionState.ACTIVE) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                "material.version_upload_requires_active_version",
                "Only ACTIVE material versions can receive controlled replacement uploads"
            );
        }
        if (!StringUtils.hasText(targetRecord.sourceKey())) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                "material.version_upload_missing_lineage",
                "Material '" + materialId + "' does not have a lineage source key"
            );
        }

        MaterialMetadataInput effectiveMetadataInput = targetRecord.metadata()
            .toEditableInputPreservingStoredFields(metadataInput);
        return saveUploadInternal(
            title,
            file,
            effectiveMetadataInput,
            targetRecord.title(),
            targetRecord.sourceKey(),
            false,
            DuplicateContentBehavior.REJECT
        );
    }

    public MaterialSummary editMaterial(
        String materialId,
        String title,
        String content,
        MaterialMetadataInput metadataInput
    ) {
        InputLimits.validateMaterialMetadata(metadataInput);
        StoredMaterialRecord targetRecord = catalogRepository.findById(materialId).orElseThrow(() -> new ApiException(
            HttpStatus.NOT_FOUND,
            "material.not_found",
            "Material '" + materialId + "' does not exist"
        ));
        if (targetRecord.versionState() != MaterialVersionState.ACTIVE) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                "material.edit_requires_active_version",
                "Only ACTIVE material versions can be edited"
            );
        }
        if (!StringUtils.hasText(targetRecord.sourceKey())) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                "material.edit_missing_lineage",
                "Material '" + materialId + "' does not have a lineage source key"
            );
        }
        if (!StringUtils.hasText(content)) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "material.empty_text",
                "Material text is empty"
            );
        }

        String resolvedTitle = StringUtils.hasText(title) ? title.trim() : targetRecord.title();
        DocumentParseResult parseResult = new DocumentParseResult(
            List.of(new DocumentBlock(
                0,
                DocumentBlockType.NARRATIVE,
                content,
                null,
                "manual-edit",
                false,
                DocumentBlockConfidence.HIGH,
                null
            )),
            MaterialMetadataHints.empty(),
            List.of(),
            null,
            "manual-edit",
            false,
            DocumentParserProfile.RICH_TEXT
        );
        String contentText = contentSupport.joinBlocks(parseResult);
        MaterialMetadataSnapshot metadata = rolloutProperties.isMetadataV1()
            ? resolveMetadata(
                targetRecord.metadata().toEditableInputPreservingStoredFields(metadataInput),
                resolvedTitle,
                targetRecord.sourceType(),
                targetRecord.originalFileName(),
                targetRecord.mediaType(),
                contentSupport.headerTextForHints(parseResult),
                contentText,
                parseResult.metadataHints()
            )
            : targetRecord.metadata();

        try {
            PersistMaterialResult result = persistMaterial(
                resolvedTitle,
                targetRecord.sourceType(),
                targetRecord.originalFileName(),
                targetRecord.mediaType(),
                null,
                metadata,
                parseResult,
                targetRecord.sourceKey(),
                false,
                DuplicateContentBehavior.ALLOW_NEW_VERSION
            );
            scheduleAutoTagging(result, new AutoTaggingContext(
                result.summary().id(),
                resolvedTitle,
                targetRecord.sourceType(),
                targetRecord.originalFileName(),
                targetRecord.mediaType(),
                contentText,
                parseResult.metadataHints()
            ));
            return result.summary();
        } catch (ApiException exception) {
            logKnownMaterialFailure(
                "edit",
                targetRecord.sourceType(),
                resolvedTitle,
                targetRecord.originalFileName(),
                targetRecord.mediaType(),
                exception
            );
            throw exception;
        } catch (RuntimeException exception) {
            logUnexpectedMaterialFailure(
                "edit",
                targetRecord.sourceType(),
                resolvedTitle,
                targetRecord.originalFileName(),
                targetRecord.mediaType(),
                exception
            );
            throw exception;
        }
    }

    private MaterialSummary saveUploadInternal(
        String title,
        MultipartFile file,
        MaterialMetadataInput metadataInput,
        String fallbackTitle,
        String forcedSourceKey,
        boolean inheritManualTagsWhenEmpty,
        DuplicateContentBehavior duplicateContentBehavior
    ) {
        InputLimits.validateMaterialMetadata(metadataInput);
        if (file == null || file.isEmpty()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "material.empty_upload",
                "Upload is empty"
            );
        }

        if (file.getSize() > properties.getMaxUploadBytes()) {
            throw new ApiException(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "material.upload_too_large",
                "Uploaded file exceeds the configured size limit"
            );
        }

        String originalFileName = file.getOriginalFilename();
        String resolvedTitle = StringUtils.hasText(title)
            ? title.trim()
            : StringUtils.hasText(fallbackTitle)
                ? fallbackTitle.trim()
                : StringUtils.hasText(originalFileName) ? originalFileName.trim() : "Uploaded material";
        String lineageTitle = StringUtils.hasText(title) ? title.trim() : null;
        String mediaType = file.getContentType();

        try {
            byte[] fileBytes = file.getBytes();
            DocumentParseResult document;
            try {
                document = extractor.extract(originalFileName, mediaType, fileBytes);
            } catch (ApiException exception) {
                logKnownMaterialFailure("extract", "file", resolvedTitle, originalFileName, mediaType, exception);
                throw exception;
            } catch (RuntimeException exception) {
                logUnexpectedMaterialFailure("extract", "file", resolvedTitle, originalFileName, mediaType, exception);
                throw exception;
            }

            try {
                String contentText = contentSupport.joinBlocks(document);
                PersistMaterialResult result = persistMaterial(
                    resolvedTitle,
                    "file",
                    originalFileName,
                    mediaType,
                    lineageTitle,
                    resolveMetadata(
                        metadataInput,
                        resolvedTitle,
                        "file",
                        originalFileName,
                        mediaType,
                        contentSupport.headerTextForHints(document),
                        contentText,
                        document.metadataHints()
                    ),
                    document,
                    forcedSourceKey,
                    inheritManualTagsWhenEmpty,
                    duplicateContentBehavior
                );
                scheduleAutoTagging(result, new AutoTaggingContext(
                    result.summary().id(),
                    resolvedTitle,
                    "file",
                    originalFileName,
                    mediaType,
                    contentText,
                    document.metadataHints()
                ));
                return result.summary();
            } catch (ApiException exception) {
                logKnownMaterialFailure("persist", "file", resolvedTitle, originalFileName, mediaType, exception);
                throw exception;
            } catch (RuntimeException exception) {
                logUnexpectedMaterialFailure("persist", "file", resolvedTitle, originalFileName, mediaType, exception);
                throw exception;
            }
        } catch (IOException exception) {
            logger.error(
                "Material upload failed at stage=receive sourceType=file title={} originalFileName={} mediaType={} cause={}",
                resolvedTitle,
                originalFileName,
                mediaType,
                exception.getMessage(),
                exception
            );
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "material.upload_read_failed",
                "Unable to read uploaded file bytes",
                exception
            );
        }
    }

    @Transactional
    public boolean importLegacyRecord(StoredMaterialRecord legacyRecord) {
        if (legacyRecord == null) {
            return false;
        }

        StoredMaterialRecord normalizedRecord = contentSupport.normalizeLegacyRecord(legacyRecord);
        if (!StringUtils.hasText(normalizedRecord.content())) {
            return false;
        }

        MaterialLineageIdentity lineageIdentity = contentSupport.buildLineageIdentity(normalizedRecord);
        String sourceKey = lineageRepository.resolveSourceKey(lineageIdentity);
        StoredMaterialRecord resolvedRecord = normalizedRecord.withSourceKey(sourceKey);
        lineageRepository.lockLineage(sourceKey);
        if (catalogRepository.findBySourceKeyAndContentHash(sourceKey, resolvedRecord.contentHash()).isPresent()) {
            return false;
        }

        List<StoredMaterialSegment> legacySegments = contentSupport.pseudoSegmentsFromChunks(
            resolvedRecord.chunks(),
            resolvedRecord.extractor(),
            Boolean.TRUE.equals(resolvedRecord.ocrUsed())
        );
        boolean imported = lifecycleService.importLegacyRecord(
            resolvedRecord,
            ChunkProfile.FIXED_V1.propertyValue(),
            legacySegments,
            SUPERSEDE_REASON_NEW_ACTIVE_VERSION
        );
        if (imported) {
            afterCommitExecutor.afterCommit(indexingService::requestProcessing);
        }
        return imported;
    }

    private PersistMaterialResult persistMaterial(
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
            true,
            DuplicateContentBehavior.REUSE_OR_REACTIVATE
        );
    }

    private PersistMaterialResult persistMaterial(
        String title,
        String sourceType,
        String originalFileName,
        String mediaType,
        String lineageTitle,
        MaterialMetadataSnapshot metadata,
        DocumentParseResult document,
        String forcedSourceKey,
        boolean inheritManualTagsWhenEmpty,
        DuplicateContentBehavior duplicateContentBehavior
    ) {
        List<StoredMaterialSegment> normalizedSegments = contentSupport.toStoredSegments(document);
        String storedContent = contentSupport.joinBlocks(document);
        if (!StringUtils.hasText(storedContent)) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "material.empty_content",
                "Material content is empty after normalization"
            );
        }

        if (storedContent.length() > properties.getMaxTextChars()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "material.content_too_large",
                "Material content exceeds the configured text limit"
            );
        }

        String normalizedContent = contentSupport.normalizeForHash(storedContent);
        String contentHash = contentSupport.sha256(normalizedContent);
        MaterialLineageIdentity lineageIdentity = StringUtils.hasText(forcedSourceKey)
            ? null
            : contentSupport.buildLineageIdentity(
                sourceType,
                lineageTitle,
                originalFileName,
                storedContent
            );
        ChunkProfile chunkProfile = contentSupport.configuredChunkProfile(rolloutProperties.isStructuredV1());
        List<StoredMaterialChunk> initialChunks = contentSupport.buildChunks(document, chunkProfile);
        List<StoredMaterialChunk> rawChunks = initialChunks.isEmpty()
            ? contentSupport.buildChunks(normalizedSegments, chunkProfile)
            : initialChunks;
        return inPersistenceTransaction(() -> persistPreparedMaterial(
            title,
            sourceType,
            originalFileName,
            mediaType,
            storedContent,
            normalizedContent,
            contentHash,
            forcedSourceKey,
            lineageIdentity,
            metadata,
            document,
            chunkProfile,
            rawChunks,
            normalizedSegments,
            inheritManualTagsWhenEmpty,
            duplicateContentBehavior
        ));
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
        MaterialMetadataSnapshot metadata,
        DocumentParseResult document,
        ChunkProfile chunkProfile,
        List<StoredMaterialChunk> rawChunks,
        List<StoredMaterialSegment> normalizedSegments,
        boolean inheritManualTagsWhenEmpty,
        DuplicateContentBehavior duplicateContentBehavior
    ) {
        String sourceKey = StringUtils.hasText(forcedSourceKey)
            ? forcedSourceKey.trim()
            : lineageRepository.resolveSourceKey(Objects.requireNonNull(lineageIdentity, "lineageIdentity"));
        lineageRepository.lockLineage(sourceKey);

        java.util.Optional<StoredMaterialRecord> existingRecord =
            catalogRepository.findBySourceKeyAndContentHash(sourceKey, contentHash);
        if (existingRecord.isPresent()) {
            if (duplicateContentBehavior == DuplicateContentBehavior.REJECT) {
                throw new ApiException(
                    HttpStatus.CONFLICT,
                    "material.version_duplicate_content",
                    "This content already exists in the selected material lineage"
                );
            }
            if (duplicateContentBehavior == DuplicateContentBehavior.ALLOW_NEW_VERSION) {
                return saveNewMaterial(
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
                );
            }
            return handleExistingMaterial(existingRecord.get(), sourceKey);
        }

        MaterialMetadataSnapshot metadataForNewMaterial = inheritManualTagsWhenEmpty
            ? inheritManualTagsIfNeeded(metadata, sourceKey)
            : metadata;
        return saveNewMaterial(
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
        );
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
            now
            ,
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
        return new PersistMaterialResult(contentSupport.toSummary(savedRecord), savedRecord.id().equals(record.id()));
    }

    private MaterialMetadataSnapshot resolveMetadata(
        MaterialMetadataInput metadataInput,
        String title,
        String sourceType,
        String originalFileName,
        String mediaType,
        String headerText,
        String contentText,
        MaterialMetadataHints parserHints
    ) {
        if (!rolloutProperties.isMetadataV1()) {
            return MaterialMetadataSnapshot.empty();
        }
        return metadataResolver.resolve(
            metadataInput,
            title,
            sourceType,
            originalFileName,
            mediaType,
            headerText,
            parserHints == null ? MaterialMetadataHints.empty() : parserHints
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
            return new PersistMaterialResult(contentSupport.toSummary(requeuedRecord), false);
        }

        return new PersistMaterialResult(contentSupport.toSummary(resolvedRecord), false);
    }

    private PersistMaterialResult inPersistenceTransaction(java.util.function.Supplier<PersistMaterialResult> action) {
        if (persistenceTransactionTemplate == null) {
            return action.get();
        }
        return persistenceTransactionTemplate.execute(status -> action.get());
    }

    private void scheduleAutoTagging(PersistMaterialResult result, AutoTaggingContext context) {
        if (!rolloutProperties.isMetadataV1()
            || autoTaggingService == null
            || result == null
            || !result.autoTagEligible()
            || context == null) {
            return;
        }

        try {
            autoTaggingExecutor.execute(() -> applyAutoTagsBestEffort(context));
        } catch (RejectedExecutionException exception) {
            logger.warn(
                "Material auto-tagging executor rejected enrichment task; material remains saved: materialId={}",
                context.materialId(),
                exception
            );
        }
    }

    private void applyAutoTagsBestEffort(AutoTaggingContext context) {
        try {
            StoredMaterialRecord record = catalogRepository.findById(context.materialId()).orElse(null);
            if (record == null || record.versionState() != MaterialVersionState.ACTIVE) {
                return;
            }

            MaterialMetadataSnapshot currentMetadata = record.metadata();
            List<String> llmTags = autoTaggingService.suggestTags(new MaterialAutoTaggingService.TaggingRequest(
                context.title(),
                context.sourceType(),
                context.originalFileName(),
                context.mediaType(),
                context.contentText(),
                currentMetadata.manualTags(),
                context.parserHints()
            ));
            if (llmTags.isEmpty()) {
                return;
            }

            StoredMaterialRecord latestRecord = catalogRepository.findById(context.materialId()).orElse(null);
            if (latestRecord == null || latestRecord.versionState() != MaterialVersionState.ACTIVE) {
                return;
            }

            MaterialMetadataSnapshot latestMetadata = latestRecord.metadata();
            MaterialMetadataSnapshot enrichedMetadata = latestMetadata.withInferredAutoTags(
                llmTags,
                MaterialAutoTaggingService.LLM_TAG_CONFIDENCE
            );
            if (latestMetadata.effectiveTags().equals(enrichedMetadata.effectiveTags())
                && latestMetadata.autoTags().equals(enrichedMetadata.autoTags())) {
                return;
            }

            lifecycleService.updateMetadataAndMarkIndexingPending(
                latestRecord.id(),
                enrichedMetadata,
                "material.auto_tags_updated",
                "Material auto-tags were enriched after save.",
                Instant.now()
            );
            indexingService.requestProcessing();
        } catch (RuntimeException exception) {
            logger.warn(
                "Material auto-tagging failed after save; material remains saved: materialId={} cause={}",
                context.materialId(),
                exception.getMessage(),
                exception
            );
        }
    }

    private void logKnownMaterialFailure(
        String stage,
        String sourceType,
        String title,
        String originalFileName,
        String mediaType,
        ApiException exception
    ) {
        logger.warn(
            "Material processing failed at stage={} sourceType={} title={} originalFileName={} mediaType={} code={} status={} message={}",
            stage,
            sourceType,
            title,
            originalFileName,
            mediaType,
            exception.getCode(),
            exception.getStatus().value(),
            exception.getMessage()
        );
    }

    private void logUnexpectedMaterialFailure(
        String stage,
        String sourceType,
        String title,
        String originalFileName,
        String mediaType,
        RuntimeException exception
    ) {
        logger.error(
            "Material processing failed unexpectedly at stage={} sourceType={} title={} originalFileName={} mediaType={} cause={}",
            stage,
            sourceType,
            title,
            originalFileName,
            mediaType,
            exception.getMessage(),
            exception
        );
    }
}
