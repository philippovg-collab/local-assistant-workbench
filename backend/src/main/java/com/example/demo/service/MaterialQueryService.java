package com.example.demo.service;

import com.example.demo.api.ApiException;
import com.example.demo.config.MaterialProperties;
import com.example.demo.infrastructure.material.MaterialCatalogRepository;
import com.example.demo.infrastructure.material.MaterialFormatRegistry;
import com.example.demo.infrastructure.material.MaterialIndexingQueueRepository;
import com.example.demo.infrastructure.material.OcrCapability;
import com.example.demo.infrastructure.material.OcrCapabilityProvider;
import com.example.demo.infrastructure.material.StoredMaterialRecord;
import com.example.demo.model.MaterialIndexingStatus;
import com.example.demo.model.MaterialLineageResponse;
import com.example.demo.model.MaterialPdfUploadPolicyResponse;
import com.example.demo.model.MaterialLineageVersion;
import com.example.demo.model.MaterialSummary;
import com.example.demo.model.MaterialUploadPolicyResponse;
import com.example.demo.model.MaterialVersionState;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class MaterialQueryService {

    private static final String SUPERSEDE_REASON_NEW_ACTIVE_VERSION = "material.superseded_by_new_active_version";
    private static final String SUPERSEDE_REASON_REACTIVATED_VERSION = "material.superseded_by_reactivated_version";
    private static final String SUPERSEDE_REASON_LEGACY_FALLBACK = "material.supersede_reason_legacy_unknown";

    private final MaterialCatalogRepository repository;
    private final MaterialIndexingQueueRepository indexingQueueRepository;
    private final MaterialProperties properties;
    private final MaterialFormatRegistry formatRegistry;
    private final OcrCapabilityProvider ocrCapabilityProvider;
    private final MaterialContentSupport contentSupport;
    private final MaterialIndexingService indexingService;

    public MaterialQueryService(
        MaterialCatalogRepository repository,
        MaterialIndexingQueueRepository indexingQueueRepository,
        MaterialProperties properties,
        MaterialFormatRegistry formatRegistry,
        OcrCapabilityProvider ocrCapabilityProvider,
        MaterialContentSupport contentSupport,
        MaterialIndexingService indexingService
    ) {
        this.repository = repository;
        this.indexingQueueRepository = indexingQueueRepository;
        this.properties = properties;
        this.formatRegistry = formatRegistry;
        this.ocrCapabilityProvider = ocrCapabilityProvider;
        this.contentSupport = contentSupport;
        this.indexingService = indexingService;
    }

    public List<MaterialSummary> listSummaries() {
        return repository.findAll().stream()
            .sorted(Comparator.comparing(record -> record.createdAt(), Comparator.reverseOrder()))
            .map(contentSupport::toSummary)
            .toList();
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
        StoredMaterialRecord record = repository.findById(id).orElseThrow(() -> new ApiException(
            HttpStatus.NOT_FOUND,
            "material.not_found",
            "Material '" + id + "' does not exist"
        ));

        if (record.versionState() != MaterialVersionState.ACTIVE) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                "material.reindex_requires_active_version",
                "Only ACTIVE material versions can be reindexed"
            );
        }

        if (record.status() != MaterialIndexingStatus.FAILED && record.status() != MaterialIndexingStatus.PARTIAL_READY) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                "material.reindex_not_allowed_for_status",
                "Manual reindex is only available for FAILED or PARTIAL_READY materials"
            );
        }

        String preservedReasonCode = isPartialWarning(record) ? record.statusReasonCode() : null;
        String preservedReasonMessage = isPartialWarning(record) ? record.statusReasonMessage() : null;
        StoredMaterialRecord updatedRecord = indexingQueueRepository.markIndexingPending(
            record.id(),
            preservedReasonCode,
            preservedReasonMessage,
            Instant.now()
        );
        indexingService.requestProcessing();
        return contentSupport.toSummary(updatedRecord);
    }

    public MaterialLineageResponse getLineage(String id) {
        StoredMaterialRecord requestedRecord = repository.findById(id).orElseThrow(() -> new ApiException(
            HttpStatus.NOT_FOUND,
            "material.not_found",
            "Material '" + id + "' does not exist"
        ));

        List<StoredMaterialRecord> lineageRecords = repository.findAllBySourceKey(requestedRecord.sourceKey()).stream()
            .sorted(Comparator
                .comparing((StoredMaterialRecord record) -> record.versionState() == MaterialVersionState.ACTIVE ? 0 : 1)
                .thenComparing(StoredMaterialRecord::updatedAt, Comparator.reverseOrder())
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

    @Transactional
    public void delete(String id) {
        StoredMaterialRecord record = repository.findById(id).orElse(null);
        repository.delete(id);
        if (record == null || record.versionState() != MaterialVersionState.ACTIVE) {
            return;
        }

        repository.findLatestBySourceKeyAndVersionState(record.sourceKey(), MaterialVersionState.SUPERSEDED)
            .ifPresent(previousVersion -> repository.updateVersionState(
                previousVersion.id(),
                MaterialVersionState.ACTIVE,
                null,
                null,
                Instant.now()
            ));
    }

    public String supersedeReasonForNewActiveVersion() {
        return SUPERSEDE_REASON_NEW_ACTIVE_VERSION;
    }

    public String supersedeReasonForReactivatedVersion() {
        return SUPERSEDE_REASON_REACTIVATED_VERSION;
    }

    private boolean isPartialWarning(StoredMaterialRecord record) {
        return record.status() == MaterialIndexingStatus.PARTIAL_READY
            && StringUtils.hasText(record.statusReasonCode())
            && record.statusReasonCode().startsWith("material.partial_");
    }
}
