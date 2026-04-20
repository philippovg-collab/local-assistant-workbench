package com.example.demo.service;

import com.example.demo.service.material.ChunkProfile;
import com.example.demo.service.material.QualityLayerCoverageSnapshot;
import com.example.demo.service.material.StoredMaterialRecord;
import com.example.demo.service.material.port.QualityLayerMetricsRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.example.demo.config.RolloutProperties;
import com.example.demo.model.DocumentType;
import com.example.demo.model.MaterialIndexingStatus;
import com.example.demo.model.MaterialMetadataInput;
import com.example.demo.model.MaterialMetadataSnapshot;
import com.example.demo.model.MaterialVersionState;
import com.example.demo.model.SourceTrustLevel;
import com.example.demo.support.InMemoryMaterialRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QualityLayerHealthServiceTest {

    @Test
    void computesCoverageFromActiveMaterialsOnly() {
        InMemoryMaterialRepository repository = new InMemoryMaterialRepository();

        repository.save(
            record(
                "North contract",
                MaterialVersionState.ACTIVE,
                MaterialIndexingStatus.READY,
                MaterialMetadataSnapshot.fromInput(new MaterialMetadataInput(
                    DocumentType.CONTRACT,
                    LocalDate.parse("2026-04-15"),
                    "KZ-2026-0415-ENERGY",
                    "Dana Sarsen",
                    "Grid operations",
                    "v2",
                    "ru",
                    List.of("dispatch"),
                    SourceTrustLevel.HIGH,
                    "North Upgrade",
                    "GridBuild LLP",
                    "APPROVED",
                    LocalDate.parse("2026-04-01"),
                    LocalDate.parse("2026-06-30")
                ))
            ),
            ChunkProfile.STRUCTURED_V1.propertyValue(),
            List.of(),
            List.of()
        );
        repository.save(
            record(
                "South memo",
                MaterialVersionState.ACTIVE,
                MaterialIndexingStatus.PARTIAL_READY,
                MaterialMetadataSnapshot.empty()
            ),
            ChunkProfile.FIXED_V1.propertyValue(),
            List.of(),
            List.of()
        );
        repository.save(
            record(
                "Historical contract",
                MaterialVersionState.SUPERSEDED,
                MaterialIndexingStatus.READY,
                MaterialMetadataSnapshot.fromInput(new MaterialMetadataInput(
                    DocumentType.CONTRACT,
                    null,
                    "OLD-2026-0001",
                    "Archive owner",
                    "Archive",
                    null,
                    null,
                    List.of(),
                    SourceTrustLevel.HIGH,
                    null,
                    null,
                    null,
                    null,
                    null
                ))
            ),
            ChunkProfile.STRUCTURED_V1.propertyValue(),
            List.of(),
            List.of()
        );

        QualityLayerHealthService service = new QualityLayerHealthService(repository, new RolloutProperties());

        var health = service.currentHealth();

        assertFalse(health.flags().metadataV1());
        assertEquals(2, health.metadataCoverage().activeTotal());
        assertEquals(1, health.metadataCoverage().activeWithEffectiveMetadata());
        assertEquals(1, health.metadataCoverage().documentType().covered());
        assertEquals(1, health.metadataCoverage().sourceTrust().covered());
        assertEquals(1, health.metadataCoverage().authorOrDepartment().covered());
        assertEquals(2, health.activeBackfillCoverage().activeTotal());
        assertEquals(1, health.activeBackfillCoverage().structuredProfileActive());
        assertEquals(1, health.activeBackfillCoverage().pendingBackfill());
        assertEquals(1, health.activeBackfillCoverage().partialReadyActive());
    }

    @Test
    void cachesCoverageSnapshotForHealthPollingWindow() {
        CountingMetricsRepository repository = new CountingMetricsRepository(new QualityLayerCoverageSnapshot(
            2,
            1,
            1,
            1,
            1,
            1,
            0
        ));
        QualityLayerHealthService service = new QualityLayerHealthService(repository, new RolloutProperties());

        service.currentHealth();
        service.currentHealth();

        assertEquals(1, repository.calls);
    }

    private StoredMaterialRecord record(
        String title,
        MaterialVersionState versionState,
        MaterialIndexingStatus status,
        MaterialMetadataSnapshot metadata
    ) {
        Instant timestamp = Instant.parse("2026-04-19T08:00:00Z");
        String content = title + " content";
        return new StoredMaterialRecord(
            UUID.randomUUID().toString(),
            title,
            "text",
            null,
            "text/plain",
            content,
            content,
            UUID.randomUUID().toString(),
            title.toLowerCase().replace(" ", "-"),
            "direct-text",
            false,
            null,
            List.of(),
            status,
            versionState,
            null,
            null,
            timestamp,
            timestamp,
            metadata
        );
    }

    private static final class CountingMetricsRepository implements QualityLayerMetricsRepository {

        private final QualityLayerCoverageSnapshot snapshot;
        private int calls;

        private CountingMetricsRepository(QualityLayerCoverageSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public QualityLayerCoverageSnapshot qualityLayerCoverageSnapshot() {
            calls += 1;
            return snapshot;
        }
    }
}
