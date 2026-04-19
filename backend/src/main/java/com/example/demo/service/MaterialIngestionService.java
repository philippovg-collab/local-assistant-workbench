package com.example.demo.service;

import com.example.demo.api.ApiException;
import com.example.demo.config.MaterialProperties;
import com.example.demo.config.RolloutProperties;
import com.example.demo.infrastructure.material.ChunkProfile;
import com.example.demo.infrastructure.material.DocumentBlock;
import com.example.demo.infrastructure.material.DocumentBlockConfidence;
import com.example.demo.infrastructure.material.DocumentBlockType;
import com.example.demo.infrastructure.material.DocumentParseResult;
import com.example.demo.infrastructure.material.DocumentParserProfile;
import com.example.demo.infrastructure.material.DocumentTextExtractor;
import com.example.demo.infrastructure.material.MaterialCatalogRepository;
import com.example.demo.infrastructure.material.MaterialLineageIdentity;
import com.example.demo.infrastructure.material.MaterialLineageRepository;
import com.example.demo.infrastructure.material.StoredMaterialChunk;
import com.example.demo.infrastructure.material.StoredMaterialRecord;
import com.example.demo.infrastructure.material.StoredMaterialSegment;
import com.example.demo.model.MaterialIndexingStatus;
import com.example.demo.model.MaterialMetadataInput;
import com.example.demo.model.MaterialMetadataSnapshot;
import com.example.demo.model.MaterialSummary;
import com.example.demo.model.MaterialVersionState;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MaterialIngestionService {

    private static final Logger logger = LoggerFactory.getLogger(MaterialIngestionService.class);
    private static final String SUPERSEDE_REASON_NEW_ACTIVE_VERSION = "material.superseded_by_new_active_version";
    private static final String SUPERSEDE_REASON_REACTIVATED_VERSION = "material.superseded_by_reactivated_version";

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
        RolloutProperties rolloutProperties
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
            RolloutProperties.enabledForTests()
        );
    }

    @Transactional
    public MaterialSummary saveText(String title, String content, MaterialMetadataInput metadataInput) {
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
            com.example.demo.infrastructure.material.MaterialMetadataHints.empty(),
            List.of(),
            null,
            "direct-text",
            false,
            DocumentParserProfile.RICH_TEXT
        );
        try {
            return persistMaterial(
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
                    parseResult.metadataHints()
                ),
                parseResult
            );
        } catch (ApiException exception) {
            logKnownMaterialFailure("persist", "text", resolvedTitle, null, "text/plain", exception);
            throw exception;
        } catch (RuntimeException exception) {
            logUnexpectedMaterialFailure("persist", "text", resolvedTitle, null, "text/plain", exception);
            throw exception;
        }
    }

    @Transactional
    public MaterialSummary saveUpload(String title, MultipartFile file, MaterialMetadataInput metadataInput) {
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
                return persistMaterial(
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
                        document.metadataHints()
                    ),
                    document
                );
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

    private MaterialSummary persistMaterial(
        String title,
        String sourceType,
        String originalFileName,
        String mediaType,
        String lineageTitle,
        MaterialMetadataSnapshot metadata,
        DocumentParseResult document
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
        MaterialLineageIdentity lineageIdentity = contentSupport.buildLineageIdentity(
            sourceType,
            lineageTitle,
            originalFileName,
            storedContent
        );
        String sourceKey = lineageRepository.resolveSourceKey(lineageIdentity);
        ChunkProfile chunkProfile = contentSupport.configuredChunkProfile(rolloutProperties.isStructuredV1());
        List<StoredMaterialChunk> initialChunks = contentSupport.buildChunks(document, chunkProfile);
        List<StoredMaterialChunk> rawChunks = initialChunks.isEmpty()
            ? contentSupport.buildChunks(normalizedSegments, chunkProfile)
            : initialChunks;
        lineageRepository.lockLineage(sourceKey);

        java.util.Optional<StoredMaterialRecord> existingRecord =
            catalogRepository.findBySourceKeyAndContentHash(sourceKey, contentHash);
        if (existingRecord.isPresent()) {
            return handleExistingMaterial(existingRecord.get(), sourceKey);
        }

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

    private MaterialSummary saveNewMaterial(
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
        return contentSupport.toSummary(savedRecord);
    }

    private MaterialMetadataSnapshot resolveMetadata(
        MaterialMetadataInput metadataInput,
        String title,
        String sourceType,
        String originalFileName,
        String mediaType,
        String headerText,
        com.example.demo.infrastructure.material.MaterialMetadataHints parserHints
    ) {
        if (!rolloutProperties.isMetadataV1()) {
            return MaterialMetadataSnapshot.fromInput(metadataInput);
        }
        return metadataResolver.resolve(
            metadataInput,
            title,
            sourceType,
            originalFileName,
            mediaType,
            headerText,
            parserHints
        );
    }

    private MaterialSummary handleExistingMaterial(StoredMaterialRecord existingRecord, String sourceKey) {
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
            return contentSupport.toSummary(requeuedRecord);
        }

        return contentSupport.toSummary(resolvedRecord);
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
