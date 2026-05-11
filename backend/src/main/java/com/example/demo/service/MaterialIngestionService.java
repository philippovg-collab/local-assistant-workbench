package com.example.demo.service;

import com.example.demo.config.MaterialProperties;
import com.example.demo.config.RolloutProperties;
import com.example.demo.model.MaterialLineageOverrideInput;
import com.example.demo.model.MaterialMetadataInput;
import com.example.demo.model.MaterialSummary;
import com.example.demo.service.material.StoredMaterialRecord;
import com.example.demo.service.material.port.DocumentTextExtractor;
import com.example.demo.service.material.port.MaterialCatalogRepository;
import com.example.demo.service.material.port.MaterialLineageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MaterialIngestionService {

    private final MaterialIngestionWorkflow workflow;

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
        StructuredV1ProofService structuredV1ProofService,
        MaterialAutoTaggingLifecycleService autoTaggingLifecycleService,
        MaterialAutoTaggingWorkerService autoTaggingWorkerService,
        PlatformTransactionManager transactionManager
    ) {
        RolloutProperties safeRolloutProperties = rolloutProperties == null ? new RolloutProperties() : rolloutProperties;
        MaterialPersistenceCoordinator persistenceCoordinator = new MaterialPersistenceCoordinator(
            catalogRepository,
            lineageRepository,
            properties,
            contentSupport,
            lifecycleService,
            indexingService,
            afterCommitExecutor,
            safeRolloutProperties,
            structuredV1ProofService,
            autoTaggingLifecycleService,
            autoTaggingWorkerService,
            transactionManager
        );
        this.workflow = new MaterialIngestionWorkflow(
            catalogRepository,
            lineageRepository,
            extractor,
            properties,
            contentSupport,
            metadataResolver,
            lifecycleService,
            indexingService,
            afterCommitExecutor,
            safeRolloutProperties,
            persistenceCoordinator
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
        RolloutProperties rolloutProperties,
        MaterialAutoTaggingLifecycleService autoTaggingLifecycleService,
        MaterialAutoTaggingWorkerService autoTaggingWorkerService
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
            StructuredV1ProofService.allowAllForTests(rolloutProperties),
            autoTaggingLifecycleService,
            autoTaggingWorkerService,
            null
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
            StructuredV1ProofService.allowAllForTests(rolloutProperties),
            null,
            null,
            null
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
            StructuredV1ProofService.allowAllForTests(rolloutProperties),
            null,
            null,
            null
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
            StructuredV1ProofService.allowAllForTests(RolloutProperties.enabledForTests()),
            null,
            null,
            null
        );
    }

    public MaterialSummary saveText(String title, String content, MaterialMetadataInput metadataInput) {
        return saveText(title, content, metadataInput, null);
    }

    public MaterialSummary saveText(
        String title,
        String content,
        MaterialMetadataInput metadataInput,
        MaterialLineageOverrideInput lineageOverride
    ) {
        return workflow.saveText(title, content, metadataInput, lineageOverride);
    }

    public MaterialSummary saveUpload(String title, MultipartFile file, MaterialMetadataInput metadataInput) {
        return saveUpload(title, file, metadataInput, null);
    }

    public MaterialSummary saveUpload(
        String title,
        MultipartFile file,
        MaterialMetadataInput metadataInput,
        MaterialLineageOverrideInput lineageOverride
    ) {
        return workflow.saveUpload(title, file, metadataInput, lineageOverride);
    }

    public MaterialSummary saveUploadVersion(
        String materialId,
        String title,
        MultipartFile file,
        MaterialMetadataInput metadataInput
    ) {
        return workflow.saveUploadVersion(materialId, title, file, metadataInput);
    }

    public MaterialSummary editMaterial(
        String materialId,
        String title,
        String content,
        MaterialMetadataInput metadataInput
    ) {
        return workflow.editMaterial(materialId, title, content, metadataInput);
    }

    @Transactional
    public boolean importLegacyRecord(StoredMaterialRecord legacyRecord) {
        return workflow.importLegacyRecord(legacyRecord);
    }
}
