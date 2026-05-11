package com.example.demo.service.material.port;

import com.example.demo.service.material.MaterialLineageIdentity;
import com.example.demo.service.material.MaterialLineageOperatorOverride;
import java.time.Instant;
import java.util.Optional;

public interface MaterialLineageRepository {

    String resolveSourceKey(MaterialLineageIdentity identity);

    Optional<MaterialLineageOperatorOverride> findActiveOverride(String lineageKey);

    Optional<MaterialLineageOperatorOverride> findActiveOverrideBySourceKey(String sourceKey);

    MaterialLineageOperatorOverride saveOverride(
        String lineageKey,
        String sourceKey,
        String reason,
        String author,
        Instant now
    );

    void lockLineage(String sourceKey);
}
