package com.example.demo.infrastructure.material;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.embedding.EmbeddingClient;
import com.example.demo.infrastructure.material.MaterialSearchSyncQueueEntry;
import com.example.demo.model.MaterialIndexingStatus;
import com.example.demo.model.MaterialVersionState;
import com.example.demo.service.MaterialSearchSyncLifecycleService;
import com.example.demo.support.IntegrationTestOverrides;
import com.example.demo.support.PostgresIntegrationTestSupport;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(IntegrationTestOverrides.class)
class MaterialSearchSyncLifecycleIT extends PostgresIntegrationTestSupport {

    @Autowired
    private PostgresMaterialRepository repository;

    @Autowired
    private MaterialSearchSyncLifecycleService lifecycleService;

    @Autowired
    private EmbeddingClient embeddingClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetTables() {
        jdbcTemplate.execute("TRUNCATE TABLE material_search_sync_queue, material_chunks, materials CASCADE");
    }

    @Test
    void deleteEnqueuesDeletedAndPromotedMaterialIds() {
        StoredMaterialRecord promoted = saveMaterial(
            "Pricing FAQ",
            "Старая цена: 9000 тенге.",
            "pricing-lineage",
            MaterialVersionState.SUPERSEDED,
            MaterialIndexingStatus.READY,
            Instant.parse("2026-04-17T10:00:00Z")
        );
        StoredMaterialRecord active = saveMaterial(
            "Pricing FAQ",
            "Новая цена: 12000 тенге.",
            "pricing-lineage",
            MaterialVersionState.ACTIVE,
            MaterialIndexingStatus.READY,
            Instant.parse("2026-04-17T10:05:00Z")
        );

        lifecycleService.delete(active.id(), Instant.parse("2026-04-17T10:10:00Z"));

        assertTrue(repository.findById(active.id()).isEmpty());
        assertEquals(MaterialVersionState.ACTIVE, repository.findById(promoted.id()).orElseThrow().versionState());
        assertEquals(
            sortedIds(active.id(), promoted.id()),
            queuedMaterialIds(repository.findAllSearchSyncEntries())
        );
    }

    @Test
    void reactivateVersionEnqueuesSupersededAndReactivatedMaterialIds() {
        StoredMaterialRecord original = saveMaterial(
            "Pricing FAQ",
            "Старая версия снова активируется.",
            "pricing-lineage",
            MaterialVersionState.SUPERSEDED,
            MaterialIndexingStatus.READY,
            Instant.parse("2026-04-17T10:00:00Z")
        );
        StoredMaterialRecord active = saveMaterial(
            "Pricing FAQ",
            "Текущая активная версия.",
            "pricing-lineage",
            MaterialVersionState.ACTIVE,
            MaterialIndexingStatus.READY,
            Instant.parse("2026-04-17T10:05:00Z")
        );

        lifecycleService.reactivateVersion(
            repository.findById(original.id()).orElseThrow(),
            "material.manual_rollback",
            Instant.parse("2026-04-17T10:10:00Z")
        );

        assertEquals(MaterialVersionState.ACTIVE, repository.findById(original.id()).orElseThrow().versionState());
        assertEquals(MaterialVersionState.SUPERSEDED, repository.findById(active.id()).orElseThrow().versionState());
        assertEquals(
            sortedIds(active.id(), original.id()),
            queuedMaterialIds(repository.findAllSearchSyncEntries())
        );
    }

    @Test
    void lateCompletionAfterDeleteLeavesDeletedMaterialQueuedForReconcile() {
        StoredMaterialRecord pending = saveMaterial(
            "Pricing FAQ",
            "Материал ещё индексируется.",
            "pricing-lineage",
            MaterialVersionState.ACTIVE,
            MaterialIndexingStatus.PENDING,
            Instant.parse("2026-04-17T10:00:00Z")
        );
        repository.claimNextIndexing(Instant.parse("2026-04-17T10:01:00Z")).orElseThrow();

        lifecycleService.delete(pending.id(), Instant.parse("2026-04-17T10:02:00Z"));
        lifecycleService.markIndexingReady(
            pending.id(),
            List.of(new StoredEmbeddedMaterialChunk(
                0,
                pending.content(),
                null,
                "direct-text",
                false,
                embeddingClient.embed(pending.content())
            )),
            MaterialIndexingStatus.READY,
            null,
            null,
            Instant.parse("2026-04-17T10:03:00Z")
        );

        assertTrue(repository.findById(pending.id()).isEmpty());
        assertEquals(List.of(pending.id()), queuedMaterialIds(repository.findAllSearchSyncEntries()));
    }

    @Test
    void lateCompletionAfterSupersedeKeepsOldAndReplacementMaterialQueuedForReconcile() {
        StoredMaterialRecord original = saveMaterial(
            "Pricing FAQ",
            "Старая версия ещё индексируется.",
            "pricing-lineage",
            MaterialVersionState.ACTIVE,
            MaterialIndexingStatus.PENDING,
            Instant.parse("2026-04-17T10:00:00Z")
        );
        repository.claimNextIndexing(Instant.parse("2026-04-17T10:01:00Z")).orElseThrow();

        StoredMaterialRecord replacement = materialRecord(
            "Pricing FAQ",
            "Новая версия",
            "pricing-lineage",
            MaterialVersionState.ACTIVE,
            MaterialIndexingStatus.PENDING,
            Instant.parse("2026-04-17T10:02:00Z")
        );
        lifecycleService.saveNewActiveMaterial(
            replacement,
            replacement.chunks(),
            "material.superseded_by_new_active_version",
            Instant.parse("2026-04-17T10:02:00Z")
        );
        lifecycleService.markIndexingReady(
            original.id(),
            List.of(new StoredEmbeddedMaterialChunk(
                0,
                original.content(),
                null,
                "direct-text",
                false,
                embeddingClient.embed(original.content())
            )),
            MaterialIndexingStatus.READY,
            null,
            null,
            Instant.parse("2026-04-17T10:03:00Z")
        );

        StoredMaterialRecord storedOriginal = repository.findById(original.id()).orElseThrow();
        assertEquals(MaterialVersionState.SUPERSEDED, storedOriginal.versionState());
        assertEquals(
            sortedIds(original.id(), replacement.id()),
            queuedMaterialIds(repository.findAllSearchSyncEntries())
        );
    }

    private StoredMaterialRecord saveMaterial(
        String title,
        String content,
        String sourceKey,
        MaterialVersionState versionState,
        MaterialIndexingStatus status,
        Instant timestamp
    ) {
        StoredMaterialRecord record = materialRecord(title, content, sourceKey, versionState, status, timestamp);
        repository.save(record, record.chunks());
        if (status == MaterialIndexingStatus.READY || status == MaterialIndexingStatus.PARTIAL_READY) {
            repository.markIndexingReady(
                record.id(),
                List.of(new StoredEmbeddedMaterialChunk(
                    0,
                    content,
                    null,
                    "direct-text",
                    false,
                    embeddingClient.embed(content)
                )),
                status,
                status == MaterialIndexingStatus.PARTIAL_READY ? "material.partial_ocr" : null,
                status == MaterialIndexingStatus.PARTIAL_READY ? "Partial OCR warning" : null,
                timestamp
            );
        }
        return record;
    }

    private StoredMaterialRecord materialRecord(
        String title,
        String content,
        String sourceKey,
        MaterialVersionState versionState,
        MaterialIndexingStatus status,
        Instant timestamp
    ) {
        return new StoredMaterialRecord(
            UUID.randomUUID().toString(),
            title,
            "text",
            null,
            "text/plain",
            content,
            content,
            UUID.randomUUID().toString(),
            sourceKey,
            "direct-text",
            false,
            null,
            List.of(new StoredMaterialChunk(0, content, List.of(), null, "direct-text", false)),
            status,
            versionState,
            null,
            null,
            timestamp,
            timestamp
        );
    }

    private List<String> queuedMaterialIds(List<MaterialSearchSyncQueueEntry> entries) {
        return entries.stream()
            .map(MaterialSearchSyncQueueEntry::materialId)
            .sorted()
            .toList();
    }

    private static List<String> sortedIds(String... materialIds) {
        return java.util.Arrays.stream(materialIds)
            .sorted()
            .toList();
    }
}
