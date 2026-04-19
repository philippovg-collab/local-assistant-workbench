package com.example.demo.service;

import com.example.demo.infrastructure.material.MaterialCatalogRepository;
import com.example.demo.infrastructure.material.MaterialChunkingRepository;
import com.example.demo.infrastructure.material.MaterialLineageRepository;
import com.example.demo.infrastructure.material.MaterialSearchSyncQueueRepository;
import com.example.demo.infrastructure.material.ChunkProfile;
import com.example.demo.infrastructure.material.StoredEmbeddedMaterialChunk;
import com.example.demo.infrastructure.material.StoredMaterialChunk;
import com.example.demo.infrastructure.material.StoredMaterialRecord;
import com.example.demo.infrastructure.material.StoredMaterialSegment;
import com.example.demo.infrastructure.material.SearchSyncOperationType;
import com.example.demo.model.MaterialIndexingStatus;
import com.example.demo.model.MaterialVersionState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaterialSearchSyncLifecycleService {

    private final MaterialCatalogRepository catalogRepository;
    private final MaterialLineageRepository lineageRepository;
    private final MaterialChunkingRepository chunkingRepository;
    private final com.example.demo.infrastructure.material.MaterialIndexingQueueRepository indexingQueueRepository;
    private final MaterialSearchSyncQueueRepository queueRepository;
    private final AfterCommitExecutor afterCommitExecutor;
    private final ObjectProvider<ElasticsearchIndexSyncService> searchSyncServiceProvider;

    @Autowired
    public MaterialSearchSyncLifecycleService(
        MaterialCatalogRepository catalogRepository,
        MaterialLineageRepository lineageRepository,
        MaterialChunkingRepository chunkingRepository,
        com.example.demo.infrastructure.material.MaterialIndexingQueueRepository indexingQueueRepository,
        MaterialSearchSyncQueueRepository queueRepository,
        AfterCommitExecutor afterCommitExecutor,
        ObjectProvider<ElasticsearchIndexSyncService> searchSyncServiceProvider
    ) {
        this.catalogRepository = catalogRepository;
        this.lineageRepository = lineageRepository;
        this.chunkingRepository = chunkingRepository;
        this.indexingQueueRepository = indexingQueueRepository;
        this.queueRepository = queueRepository;
        this.afterCommitExecutor = afterCommitExecutor;
        this.searchSyncServiceProvider = searchSyncServiceProvider;
    }

    @Transactional
    public StoredMaterialRecord saveNewActiveMaterial(
        StoredMaterialRecord record,
        List<StoredMaterialChunk> rawChunks,
        String supersedeReason,
        Instant updatedAt
    ) {
        return saveNewActiveMaterial(
            record,
            ChunkProfile.FIXED_V1.propertyValue(),
            rawChunks,
            List.of(),
            supersedeReason,
            updatedAt
        );
    }

    @Transactional
    public StoredMaterialRecord saveNewActiveMaterial(
        StoredMaterialRecord record,
        String chunkProfile,
        List<StoredMaterialChunk> rawChunks,
        List<StoredMaterialSegment> segments,
        String supersedeReason,
        Instant updatedAt
    ) {
        lineageRepository.lockLineage(record.sourceKey());
        List<String> supersededMaterialIds = new ArrayList<>();
        catalogRepository.supersedeActiveVersions(
            record.sourceKey(),
            record.id(),
            record.id(),
            supersedeReason,
            updatedAt
        ).forEach(material -> appendMaterialId(supersededMaterialIds, material));
        StoredMaterialRecord savedRecord = catalogRepository.save(record, chunkProfile, rawChunks, segments);
        if (!savedRecord.id().equals(record.id())) {
            return savedRecord;
        }

        persistSearchSyncMaterials(supersededMaterialIds, SearchSyncOperationType.DELETE, updatedAt);
        return savedRecord;
    }

    @Transactional
    public boolean importLegacyRecord(StoredMaterialRecord normalizedRecord, String supersedeReason) {
        return importLegacyRecord(
            normalizedRecord,
            ChunkProfile.FIXED_V1.propertyValue(),
            List.of(),
            supersedeReason
        );
    }

    @Transactional
    public boolean importLegacyRecord(
        StoredMaterialRecord normalizedRecord,
        String chunkProfile,
        List<StoredMaterialSegment> segments,
        String supersedeReason
    ) {
        lineageRepository.lockLineage(normalizedRecord.sourceKey());
        List<String> supersededMaterialIds = new ArrayList<>();
        catalogRepository.supersedeActiveVersions(
            normalizedRecord.sourceKey(),
            normalizedRecord.id(),
            normalizedRecord.id(),
            supersedeReason,
            normalizedRecord.updatedAt()
        ).forEach(material -> appendMaterialId(supersededMaterialIds, material));
        StoredMaterialRecord savedRecord = catalogRepository.save(
            normalizedRecord,
            chunkProfile,
            normalizedRecord.chunks(),
            segments
        );
        if (!savedRecord.id().equals(normalizedRecord.id())) {
            return false;
        }

        persistSearchSyncMaterials(supersededMaterialIds, SearchSyncOperationType.DELETE, normalizedRecord.updatedAt());
        return true;
    }

    @Transactional
    public StoredMaterialRecord reactivateVersion(
        StoredMaterialRecord existingRecord,
        String supersedeReason,
        Instant updatedAt
    ) {
        lineageRepository.lockLineage(existingRecord.sourceKey());
        List<String> supersededMaterialIds = new ArrayList<>();
        catalogRepository.supersedeActiveVersions(
            existingRecord.sourceKey(),
            existingRecord.id(),
            existingRecord.id(),
            supersedeReason,
            updatedAt
        ).forEach(material -> appendMaterialId(supersededMaterialIds, material));

        StoredMaterialRecord updatedRecord = catalogRepository.updateVersionState(
            existingRecord.id(),
            MaterialVersionState.ACTIVE,
            null,
            null,
            updatedAt
        );
        List<String> upsertMaterialIds = new ArrayList<>();
        appendMaterialId(upsertMaterialIds, updatedRecord);
        persistSearchSyncMaterials(supersededMaterialIds, SearchSyncOperationType.DELETE, updatedAt);
        persistSearchSyncMaterials(upsertMaterialIds, SearchSyncOperationType.UPSERT, updatedAt);
        return updatedRecord;
    }

    @Transactional
    public StoredMaterialRecord replaceChunkingAndMarkIndexingPending(
        String materialId,
        String chunkProfile,
        List<StoredMaterialChunk> rawChunks,
        List<StoredMaterialSegment> segments,
        String reasonCode,
        String reasonMessage,
        Instant updatedAt
    ) {
        catalogRepository.findById(materialId).orElseThrow();
        chunkingRepository.replaceChunking(materialId, chunkProfile, rawChunks, segments, updatedAt);
        StoredMaterialRecord updatedRecord = indexingQueueRepository.markIndexingPending(
            materialId,
            reasonCode,
            reasonMessage,
            updatedAt
        );
        return updatedRecord;
    }

    @Transactional
    public StoredMaterialRecord markIndexingPending(
        String materialId,
        String reasonCode,
        String reasonMessage,
        Instant updatedAt
    ) {
        StoredMaterialRecord updatedRecord = indexingQueueRepository.markIndexingPending(
            materialId,
            reasonCode,
            reasonMessage,
            updatedAt
        );
        return updatedRecord;
    }

    @Transactional
    public void markIndexingReady(
        String materialId,
        List<StoredEmbeddedMaterialChunk> chunks,
        MaterialIndexingStatus finalStatus,
        String reasonCode,
        String reasonMessage,
        Instant updatedAt
    ) {
        indexingQueueRepository.markIndexingReady(
            materialId,
            chunks,
            finalStatus,
            reasonCode,
            reasonMessage,
            updatedAt
        );

        List<String> materialIds = new ArrayList<>();
        appendMaterialId(materialIds, materialId);
        persistSearchSyncMaterials(materialIds, SearchSyncOperationType.UPSERT, updatedAt);
    }

    @Transactional
    public void delete(String materialId, Instant updatedAt) {
        String sourceKey = catalogRepository.findSourceKeyById(materialId).orElse(null);
        if (sourceKey == null) {
            catalogRepository.delete(materialId);
            persistSearchSyncMaterials(List.of(materialId), SearchSyncOperationType.DELETE, updatedAt);
            return;
        }
        lineageRepository.lockLineage(sourceKey);
        StoredMaterialRecord record = catalogRepository.findById(materialId).orElse(null);
        if (record == null) {
            return;
        }

        List<String> deleteMaterialIds = new ArrayList<>();
        if (record.versionState() == MaterialVersionState.ACTIVE) {
            appendMaterialId(deleteMaterialIds, record);
        }

        catalogRepository.delete(materialId);
        if (record.versionState() == MaterialVersionState.ACTIVE) {
            StoredMaterialRecord promotedRecord = catalogRepository
                .findLatestBySourceKeyAndVersionState(sourceKey, MaterialVersionState.SUPERSEDED)
                .map(previousVersion -> catalogRepository.updateVersionState(
                    previousVersion.id(),
                    MaterialVersionState.ACTIVE,
                    null,
                    null,
                    updatedAt
                ))
                .orElse(null);
            List<String> upsertMaterialIds = new ArrayList<>();
            appendMaterialId(upsertMaterialIds, promotedRecord);
            persistSearchSyncMaterials(upsertMaterialIds, SearchSyncOperationType.UPSERT, updatedAt);
        }
        persistSearchSyncMaterials(deleteMaterialIds, SearchSyncOperationType.DELETE, updatedAt);
    }

    private void appendMaterialId(List<String> materialIds, StoredMaterialRecord record) {
        if (record != null) {
            appendMaterialId(materialIds, record.id());
        }
    }

    private void appendMaterialId(List<String> materialIds, String materialId) {
        if (materialIds == null || materialId == null || materialId.isBlank()) {
            return;
        }
        if (!materialIds.contains(materialId)) {
            materialIds.add(materialId);
        }
    }

    private void persistSearchSyncMaterials(List<String> materialIds, Instant requestedAt) {
        persistSearchSyncMaterials(materialIds, SearchSyncOperationType.UPSERT, requestedAt);
    }

    private void persistSearchSyncMaterials(
        List<String> materialIds,
        SearchSyncOperationType operationType,
        Instant requestedAt
    ) {
        queueRepository.enqueueMaterialsForSync(materialIds, operationType, requestedAt);
        if (materialIds == null || materialIds.isEmpty()) {
            return;
        }

        afterCommitExecutor.afterCommit(() -> {
            ElasticsearchIndexSyncService searchSyncService = searchSyncServiceProvider.getIfAvailable();
            if (searchSyncService != null) {
                searchSyncService.requestProcessing();
            }
        });
    }

}
