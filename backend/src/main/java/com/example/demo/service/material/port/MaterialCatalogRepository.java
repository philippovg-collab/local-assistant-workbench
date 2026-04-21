package com.example.demo.service.material.port;

import com.example.demo.service.material.ChunkProfile;
import com.example.demo.service.material.MaterialRetrievalScopeSnapshot;
import com.example.demo.service.material.StoredMaterialChunk;
import com.example.demo.service.material.StoredMaterialRecord;
import com.example.demo.service.material.StoredMaterialSegment;

import com.example.demo.model.KnowledgeScope;
import com.example.demo.model.MaterialMetadataSnapshot;
import com.example.demo.model.MaterialVersionState;
import com.example.demo.model.MaterialSummary;
import com.example.demo.model.RetrievalFilters;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MaterialCatalogRepository {

    List<StoredMaterialRecord> findAll();

    List<MaterialSummary> findSummaries(int offset, int limit);

    List<MaterialSummary> findSummariesByWorkspace(String workspaceKey, int offset, int limit);

    List<StoredMaterialRecord> findByIds(Collection<String> ids);

    List<StoredMaterialRecord> findActivePageAfter(Instant createdAt, String id, int limit);

    Optional<StoredMaterialRecord> findById(String id);

    default Optional<String> findSourceKeyById(String id) {
        return findById(id).map(StoredMaterialRecord::sourceKey);
    }

    Optional<StoredMaterialRecord> findBySourceKeyAndContentHash(String sourceKey, String contentHash);

    MaterialRetrievalScopeSnapshot describeRetrievalScope(
        KnowledgeScope knowledgeScope,
        RetrievalFilters retrievalFilters,
        Instant uploadedAfterInclusive,
        Instant uploadedBeforeExclusive
    );

    default StoredMaterialRecord save(StoredMaterialRecord record, List<StoredMaterialChunk> chunks) {
        return save(record, ChunkProfile.FIXED_V1.propertyValue(), chunks, List.of());
    }

    StoredMaterialRecord save(
        StoredMaterialRecord record,
        String chunkProfile,
        List<StoredMaterialChunk> chunks,
        List<StoredMaterialSegment> segments
    );

    StoredMaterialRecord updateMetadata(String materialId, MaterialMetadataSnapshot metadata, Instant updatedAt);

    List<StoredMaterialRecord> findAllBySourceKey(String sourceKey);

    void delete(String id);

    int countMaterials();

    int countMaterialsByWorkspace(String workspaceKey);

    int countActiveMaterials();

    int countReadyMaterials();

    List<StoredMaterialRecord> supersedeActiveVersions(
        String sourceKey,
        String supersededByMaterialId,
        String excludeMaterialId,
        String supersedeReason,
        Instant updatedAt
    );

    Optional<StoredMaterialRecord> findLatestBySourceKeyAndVersionState(String sourceKey, MaterialVersionState versionState);

    StoredMaterialRecord updateVersionState(
        String materialId,
        MaterialVersionState versionState,
        String supersededByMaterialId,
        String supersedeReason,
        Instant updatedAt
    );
}
