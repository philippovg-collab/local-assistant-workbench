package com.example.demo.service;

import com.example.demo.api.ApiException;
import com.example.demo.config.MaterialProperties;
import com.example.demo.infrastructure.material.DocumentTextExtractor;
import com.example.demo.infrastructure.material.ExtractedDocument;
import com.example.demo.infrastructure.material.ExtractedDocumentSegment;
import com.example.demo.infrastructure.material.MaterialCatalogRepository;
import com.example.demo.infrastructure.material.MaterialIndexingQueueRepository;
import com.example.demo.infrastructure.material.StoredMaterialChunk;
import com.example.demo.infrastructure.material.StoredMaterialRecord;
import com.example.demo.model.MaterialIndexingStatus;
import com.example.demo.model.MaterialSummary;
import com.example.demo.model.MaterialVersionState;
import java.io.IOException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MaterialIngestionService {

    private static final Logger logger = LoggerFactory.getLogger(MaterialIngestionService.class);
    private static final String SUPERSEDE_REASON_NEW_ACTIVE_VERSION = "material.superseded_by_new_active_version";
    private static final String SUPERSEDE_REASON_REACTIVATED_VERSION = "material.superseded_by_reactivated_version";

    private final MaterialCatalogRepository catalogRepository;
    private final MaterialIndexingQueueRepository indexingQueueRepository;
    private final DocumentTextExtractor extractor;
    private final MaterialProperties properties;
    private final MaterialContentSupport contentSupport;
    private final MaterialIndexingService indexingService;

    public MaterialIngestionService(
        MaterialCatalogRepository catalogRepository,
        MaterialIndexingQueueRepository indexingQueueRepository,
        DocumentTextExtractor extractor,
        MaterialProperties properties,
        MaterialContentSupport contentSupport,
        MaterialIndexingService indexingService
    ) {
        this.catalogRepository = catalogRepository;
        this.indexingQueueRepository = indexingQueueRepository;
        this.extractor = extractor;
        this.properties = properties;
        this.contentSupport = contentSupport;
        this.indexingService = indexingService;
    }

    public MaterialSummary saveText(String title, String content) {
        if (!StringUtils.hasText(content)) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "material.empty_text",
                "Material text is empty"
            );
        }

        String resolvedTitle = StringUtils.hasText(title) ? title.trim() : "Text material";
        String lineageTitle = StringUtils.hasText(title) ? title.trim() : null;
        try {
            return persistMaterial(
                resolvedTitle,
                "text",
                null,
                "text/plain",
                lineageTitle,
                new ExtractedDocument(
                    List.of(new ExtractedDocumentSegment(content, null, "direct-text", false)),
                    "direct-text",
                    false,
                    null,
                    null,
                    null
                )
            );
        } catch (ApiException exception) {
            logKnownMaterialFailure("persist", "text", resolvedTitle, null, "text/plain", exception);
            throw exception;
        } catch (RuntimeException exception) {
            logUnexpectedMaterialFailure("persist", "text", resolvedTitle, null, "text/plain", exception);
            throw exception;
        }
    }

    public MaterialSummary saveUpload(String title, MultipartFile file) {
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
            ExtractedDocument document;
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

    public boolean importLegacyRecord(StoredMaterialRecord legacyRecord) {
        if (legacyRecord == null) {
            return false;
        }

        StoredMaterialRecord normalizedRecord = contentSupport.normalizeLegacyRecord(legacyRecord);
        if (!StringUtils.hasText(normalizedRecord.content())) {
            return false;
        }

        if (catalogRepository.findByContentHash(normalizedRecord.contentHash()).isPresent()) {
            return false;
        }

        catalogRepository.save(normalizedRecord, normalizedRecord.chunks());
        catalogRepository.supersedeActiveVersions(
            normalizedRecord.sourceKey(),
            normalizedRecord.id(),
            normalizedRecord.contentHash(),
            SUPERSEDE_REASON_NEW_ACTIVE_VERSION,
            normalizedRecord.updatedAt()
        );
        indexingService.requestProcessing();
        return true;
    }

    private MaterialSummary persistMaterial(
        String title,
        String sourceType,
        String originalFileName,
        String mediaType,
        String lineageTitle,
        ExtractedDocument document
    ) {
        List<ExtractedDocumentSegment> normalizedSegments = contentSupport.normalizeSegments(document.segments());
        String storedContent = contentSupport.joinSegments(normalizedSegments);
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
        MaterialContentSupport.MaterialLineageIdentity lineageIdentity = contentSupport.buildLineageIdentity(
            sourceType,
            lineageTitle,
            originalFileName,
            storedContent
        );
        String sourceKey = resolveSourceKey(lineageIdentity);
        List<StoredMaterialChunk> rawChunks = contentSupport.buildChunks(
            normalizedSegments,
            contentSupport.normalizeExtractor(document.extractor()),
            document.ocrUsed()
        );

        return catalogRepository.findByContentHash(contentHash)
            .map(existingRecord -> handleExistingMaterial(existingRecord, sourceKey))
            .orElseGet(() -> saveNewMaterial(
                title,
                sourceType,
                originalFileName,
                mediaType,
                storedContent,
                normalizedContent,
                contentHash,
                sourceKey,
                document,
                rawChunks
            ));
    }

    private String resolveSourceKey(MaterialContentSupport.MaterialLineageIdentity lineageIdentity) {
        return catalogRepository.findAll().stream()
            .filter(record -> contentSupport.matchesLineage(record, lineageIdentity))
            .sorted(Comparator
                .comparing((StoredMaterialRecord record) -> record.versionState() == MaterialVersionState.ACTIVE ? 0 : 1)
                .thenComparing(StoredMaterialRecord::updatedAt, Comparator.reverseOrder())
                .thenComparing(StoredMaterialRecord::createdAt, Comparator.reverseOrder()))
            .map(StoredMaterialRecord::sourceKey)
            .findFirst()
            .orElseGet(() -> contentSupport.buildSourceKey(lineageIdentity));
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
        ExtractedDocument document,
        List<StoredMaterialChunk> rawChunks
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
            document.warningCode(),
            document.warningMessage(),
            now,
            now
        );

        StoredMaterialRecord savedRecord = catalogRepository.save(record, rawChunks);
        if (savedRecord.id().equals(record.id())) {
            catalogRepository.supersedeActiveVersions(
                sourceKey,
                savedRecord.id(),
                contentHash,
                SUPERSEDE_REASON_NEW_ACTIVE_VERSION,
                now
            );
            indexingService.requestProcessing();
        }
        return contentSupport.toSummary(savedRecord);
    }

    private MaterialSummary handleExistingMaterial(StoredMaterialRecord existingRecord, String sourceKey) {
        Instant now = Instant.now();
        StoredMaterialRecord resolvedRecord = existingRecord;

        if (existingRecord.sourceKey().equals(sourceKey)
            && existingRecord.versionState() == MaterialVersionState.SUPERSEDED) {
            catalogRepository.supersedeActiveVersions(
                sourceKey,
                existingRecord.id(),
                existingRecord.contentHash(),
                SUPERSEDE_REASON_REACTIVATED_VERSION,
                now
            );
            resolvedRecord = catalogRepository.updateVersionState(
                existingRecord.id(),
                MaterialVersionState.ACTIVE,
                null,
                null,
                now
            );
        }

        if (resolvedRecord.status() == MaterialIndexingStatus.FAILED) {
            StoredMaterialRecord requeuedRecord = indexingQueueRepository.markIndexingPending(
                resolvedRecord.id(),
                null,
                null,
                now
            );
            indexingService.requestProcessing();
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
