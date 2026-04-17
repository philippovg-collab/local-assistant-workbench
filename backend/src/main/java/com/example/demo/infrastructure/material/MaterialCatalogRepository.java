package com.example.demo.infrastructure.material;

import com.example.demo.model.MaterialVersionState;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MaterialCatalogRepository {

    List<StoredMaterialRecord> findAll();

    Optional<StoredMaterialRecord> findById(String id);

    Optional<StoredMaterialRecord> findByContentHash(String contentHash);

    StoredMaterialRecord save(StoredMaterialRecord record, List<StoredMaterialChunk> chunks);

    List<StoredMaterialChunk> findChunks(String materialId);

    List<StoredMaterialRecord> findAllBySourceKey(String sourceKey);

    void delete(String id);

    int countMaterials();

    int countActiveMaterials();

    int countReadyMaterials();

    void supersedeActiveVersions(
        String sourceKey,
        String activeMaterialId,
        String excludeContentHash,
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
