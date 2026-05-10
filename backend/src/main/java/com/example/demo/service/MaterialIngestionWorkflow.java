package com.example.demo.service;

import com.example.demo.config.MaterialProperties;
import com.example.demo.config.RolloutProperties;
import com.example.demo.error.ApplicationException;
import com.example.demo.error.CodedException;
import com.example.demo.error.ErrorType;
import com.example.demo.model.DocumentBlockConfidence;
import com.example.demo.model.DocumentBlockType;
import com.example.demo.model.MaterialMetadataInput;
import com.example.demo.model.MaterialMetadataSnapshot;
import com.example.demo.model.MaterialSummary;
import com.example.demo.model.MaterialVersionState;
import com.example.demo.service.MaterialPersistenceCoordinator.DuplicateContentBehavior;
import com.example.demo.service.material.ChunkProfile;
import com.example.demo.service.material.DocumentBlock;
import com.example.demo.service.material.DocumentParseResult;
import com.example.demo.service.material.DocumentParserProfile;
import com.example.demo.service.material.MaterialLineageIdentity;
import com.example.demo.service.material.MaterialMetadataHints;
import com.example.demo.service.material.StoredMaterialRecord;
import com.example.demo.service.material.StoredMaterialSegment;
import com.example.demo.service.material.port.DocumentTextExtractor;
import com.example.demo.service.material.port.MaterialCatalogRepository;
import com.example.demo.service.material.port.MaterialLineageRepository;
import com.example.demo.validation.InputLimits;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

final class MaterialIngestionWorkflow {

    private static final Logger logger = LoggerFactory.getLogger(MaterialIngestionWorkflow.class);
    private static final String SUPERSEDE_REASON_NEW_ACTIVE_VERSION = "material.superseded_by_new_active_version";

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
    private final MaterialPersistenceCoordinator persistenceCoordinator;

    MaterialIngestionWorkflow(
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
        MaterialPersistenceCoordinator persistenceCoordinator
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
        this.rolloutProperties = rolloutProperties;
        this.persistenceCoordinator = persistenceCoordinator;
    }

    MaterialSummary saveText(String title, String content, MaterialMetadataInput metadataInput) {
        InputLimits.validateMaterialMetadata(metadataInput);
        if (!StringUtils.hasText(content)) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
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
            return persistenceCoordinator.persistMaterial(
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
        } catch (CodedException exception) {
            logKnownMaterialFailure("persist", "text", resolvedTitle, null, "text/plain", exception);
            throw exception;
        } catch (RuntimeException exception) {
            logUnexpectedMaterialFailure("persist", "text", resolvedTitle, null, "text/plain", exception);
            throw exception;
        }
    }

    MaterialSummary saveUpload(String title, MultipartFile file, MaterialMetadataInput metadataInput) {
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

    MaterialSummary saveUploadVersion(
        String materialId,
        String title,
        MultipartFile file,
        MaterialMetadataInput metadataInput
    ) {
        StoredMaterialRecord targetRecord = catalogRepository.findById(materialId).orElseThrow(() -> new ApplicationException(
            ErrorType.NOT_FOUND,
            "material.not_found",
            "Material '" + materialId + "' does not exist"
        ));
        if (targetRecord.versionState() != MaterialVersionState.ACTIVE) {
            throw new ApplicationException(
                ErrorType.CONFLICT,
                "material.version_upload_requires_active_version",
                "Only ACTIVE material versions can receive controlled replacement uploads"
            );
        }
        if (!StringUtils.hasText(targetRecord.sourceKey())) {
            throw new ApplicationException(
                ErrorType.CONFLICT,
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

    MaterialSummary editMaterial(
        String materialId,
        String title,
        String content,
        MaterialMetadataInput metadataInput
    ) {
        InputLimits.validateMaterialMetadata(metadataInput);
        StoredMaterialRecord targetRecord = catalogRepository.findById(materialId).orElseThrow(() -> new ApplicationException(
            ErrorType.NOT_FOUND,
            "material.not_found",
            "Material '" + materialId + "' does not exist"
        ));
        if (targetRecord.versionState() != MaterialVersionState.ACTIVE) {
            throw new ApplicationException(
                ErrorType.CONFLICT,
                "material.edit_requires_active_version",
                "Only ACTIVE material versions can be edited"
            );
        }
        if (!StringUtils.hasText(targetRecord.sourceKey())) {
            throw new ApplicationException(
                ErrorType.CONFLICT,
                "material.edit_missing_lineage",
                "Material '" + materialId + "' does not have a lineage source key"
            );
        }
        if (!StringUtils.hasText(content)) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
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
            return persistenceCoordinator.persistMaterial(
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
        } catch (CodedException exception) {
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

    boolean importLegacyRecord(StoredMaterialRecord legacyRecord) {
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
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
                "material.empty_upload",
                "Upload is empty"
            );
        }

        if (file.getSize() > properties.getMaxUploadBytes()) {
            throw new ApplicationException(
                ErrorType.PAYLOAD_TOO_LARGE,
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
            } catch (CodedException exception) {
                logKnownMaterialFailure("extract", "file", resolvedTitle, originalFileName, mediaType, exception);
                throw exception;
            } catch (RuntimeException exception) {
                logUnexpectedMaterialFailure("extract", "file", resolvedTitle, originalFileName, mediaType, exception);
                throw exception;
            }

            try {
                String contentText = contentSupport.joinBlocks(document);
                return persistenceCoordinator.persistMaterial(
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
            } catch (CodedException exception) {
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
            throw new ApplicationException(
                ErrorType.INTERNAL,
                "material.upload_read_failed",
                "Unable to read uploaded file bytes",
                exception
            );
        }
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

    private void logKnownMaterialFailure(
        String stage,
        String sourceType,
        String title,
        String originalFileName,
        String mediaType,
        CodedException exception
    ) {
        logger.warn(
            "Material processing failed at stage={} sourceType={} title={} originalFileName={} mediaType={} code={} errorType={} message={}",
            stage,
            sourceType,
            title,
            originalFileName,
            mediaType,
            exception.getCode(),
            exception.getType(),
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
