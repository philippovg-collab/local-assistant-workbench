package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.example.demo.embedding.EmbeddingClient;
import com.example.demo.infrastructure.material.PostgresMaterialRepository;
import com.example.demo.infrastructure.material.MaterialSearchSyncQueueEntry;
import com.example.demo.infrastructure.material.SearchableChunkDocument;
import com.example.demo.infrastructure.material.StoredEmbeddedMaterialChunk;
import com.example.demo.infrastructure.material.StoredMaterialChunk;
import com.example.demo.infrastructure.material.StoredMaterialRecord;
import com.example.demo.model.MaterialIndexingStatus;
import com.example.demo.model.MaterialVersionState;
import com.example.demo.support.IntegrationTestOverrides;
import com.example.demo.support.PostgresIntegrationTestSupport;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(IntegrationTestOverrides.class)
class ElasticsearchIndexSyncIT extends PostgresIntegrationTestSupport {

    @Container
    @SuppressWarnings("resource")
    private static final ElasticsearchContainer ELASTICSEARCH = new ElasticsearchContainer(
        DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.13.4")
    )
        .withEnv("xpack.security.enabled", "false")
        .withEnv("discovery.type", "single-node");

    @DynamicPropertySource
    static void registerElasticsearchProperties(DynamicPropertyRegistry registry) {
        registry.add("app.search-sync.enabled", () -> "true");
        registry.add("app.search-sync.index-prefix", () -> "rag-chunks-it");
        registry.add("app.search-sync.index-version", () -> "v1");
        registry.add("spring.elasticsearch.uris", () -> "http://" + ELASTICSEARCH.getHttpHostAddress());
    }

    @Autowired
    private PostgresMaterialRepository repository;

    @Autowired
    private MaterialSearchSyncLifecycleService lifecycleService;

    @Autowired
    private ElasticsearchIndexAdminService indexAdminService;

    @Autowired
    private ElasticsearchIndexSyncService searchSyncService;

    @Autowired
    private SearchSyncRecoveryService recoveryService;

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    @Autowired
    private EmbeddingClient embeddingClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetState() throws Exception {
        jdbcTemplate.execute("TRUNCATE TABLE material_search_sync_queue, material_chunks, materials CASCADE");
        indexAdminService.prepareConfiguredWriteIndex();
        elasticsearchClient.deleteByQuery(delete -> delete
            .index("rag-chunks-it-write")
            .query(query -> query.matchAll(matchAll -> matchAll))
        );
        elasticsearchClient.indices().refresh(refresh -> refresh.index("rag-chunks-it-write"));
    }

    @Test
    void upsertSearchableEventWritesAllCurrentChunkDocuments() throws Exception {
        StoredMaterialRecord record = materialRecord(
            "Pricing FAQ",
            "Тариф Премиум стоит 12000 тенге.",
            "pricing-lineage",
            MaterialVersionState.ACTIVE,
            MaterialIndexingStatus.PENDING,
            Instant.parse("2026-04-17T10:00:00Z")
        );
        repository.save(record, List.of(
            rawChunk(0, "Тариф Премиум стоит 12000 тенге.", 1),
            rawChunk(1, "Приоритетная поддержка включена.", 2)
        ));

        lifecycleService.markIndexingReady(
            record.id(),
            List.of(
                embeddedChunk(0, "Тариф Премиум стоит 12000 тенге.", 1),
                embeddedChunk(1, "Приоритетная поддержка включена.", 2)
            ),
            MaterialIndexingStatus.READY,
            null,
            null,
            Instant.parse("2026-04-17T10:01:00Z")
        );
        refreshWriteAlias();

        List<SearchableChunkDocument> documents = documentsFor(record.id());

        assertEquals(2, documents.size());
        assertEquals(List.of(record.id() + ":0", record.id() + ":1"), documents.stream().map(SearchableChunkDocument::chunkId).toList());
        assertTrue(elasticsearchClient.indices().existsAlias(request -> request.name("rag-chunks-it-write")).value());
        assertTrue(elasticsearchClient.indices().existsAlias(request -> request.name("rag-chunks-it-read")).value());
        assertEquals(0, repository.getSearchSyncQueueSnapshot().pendingCount());
    }

    @Test
    void deleteActiveVersionPromotesPreviousReadyVersionInElasticsearch() throws Exception {
        StoredMaterialRecord promoted = materialRecord(
            "Pricing FAQ",
            "Старая цена 9000 тенге.",
            "pricing-lineage",
            MaterialVersionState.SUPERSEDED,
            MaterialIndexingStatus.READY,
            Instant.parse("2026-04-17T10:00:00Z")
        );
        StoredMaterialRecord active = materialRecord(
            "Pricing FAQ",
            "Новая цена 12000 тенге.",
            "pricing-lineage",
            MaterialVersionState.ACTIVE,
            MaterialIndexingStatus.PENDING,
            Instant.parse("2026-04-17T10:05:00Z")
        );

        repository.save(promoted, List.of(rawChunk(0, promoted.content(), 1)));
        repository.markIndexingReady(
            promoted.id(),
            List.of(embeddedChunk(0, promoted.content(), 1)),
            MaterialIndexingStatus.READY,
            null,
            null,
            promoted.updatedAt()
        );
        repository.save(active, List.of(rawChunk(0, active.content(), 1)));
        lifecycleService.markIndexingReady(
            active.id(),
            List.of(embeddedChunk(0, active.content(), 1)),
            MaterialIndexingStatus.READY,
            null,
            null,
            Instant.parse("2026-04-17T10:06:00Z")
        );
        refreshWriteAlias();
        assertEquals(1, documentsFor(active.id()).size());
        assertTrue(documentsFor(promoted.id()).isEmpty());

        lifecycleService.delete(active.id(), Instant.parse("2026-04-17T10:07:00Z"));
        refreshWriteAlias();

        assertTrue(documentsFor(active.id()).isEmpty());
        List<SearchableChunkDocument> promotedDocuments = documentsFor(promoted.id());
        assertEquals(1, promotedDocuments.size());
        assertEquals(promoted.id() + ":0", promotedDocuments.getFirst().chunkId());
        assertTrue(repository.findAllSearchSyncEntries().isEmpty());
    }

    @Test
    void staleUpsertEventConvergesToCurrentNonSearchableSnapshot() throws Exception {
        StoredMaterialRecord record = materialRecord(
            "Pricing FAQ",
            "Материал временно недоступен для поиска.",
            "pricing-lineage",
            MaterialVersionState.ACTIVE,
            MaterialIndexingStatus.PENDING,
            Instant.parse("2026-04-17T10:00:00Z")
        );
        repository.save(record, List.of(rawChunk(0, record.content(), 1)));
        lifecycleService.markIndexingReady(
            record.id(),
            List.of(embeddedChunk(0, record.content(), 1)),
            MaterialIndexingStatus.READY,
            null,
            null,
            Instant.parse("2026-04-17T10:01:00Z")
        );
        refreshWriteAlias();
        assertFalse(documentsFor(record.id()).isEmpty());

        repository.markIndexingPending(
            record.id(),
            "material.reindex_requested",
            "Manual reindex requested",
            Instant.parse("2026-04-17T10:02:00Z")
        );
        repository.enqueueMaterialsForSync(
            List.of(record.id()),
            Instant.parse("2026-04-17T10:03:00Z")
        );

        searchSyncService.requestProcessing();
        refreshWriteAlias();

        assertTrue(documentsFor(record.id()).isEmpty());
        assertEquals(0, repository.getSearchSyncQueueSnapshot().pendingCount());
    }

    @Test
    void prepareWriteIndexForNewVersionDoesNotCutOverExistingReadAlias() throws Exception {
        indexAdminService.prepareWriteIndex("v1");
        indexAdminService.prepareWriteIndex("v2");

        assertTrue(elasticsearchClient.indices().exists(request -> request.index("rag-chunks-it-v2")).value());
        assertEquals(Set.of("rag-chunks-it-v1"), aliasTargets("rag-chunks-it-read"));
        assertEquals(Set.of("rag-chunks-it-v2"), aliasTargets("rag-chunks-it-write"));
    }

    @Test
    void requeueFailedRecoveryReprocessesTerminalEventsIdempotently() throws Exception {
        StoredMaterialRecord record = materialRecord(
            "Pricing FAQ",
            "Тариф Премиум стоит 12000 тенге.",
            "pricing-lineage",
            MaterialVersionState.ACTIVE,
            MaterialIndexingStatus.READY,
            Instant.parse("2026-04-17T11:00:00Z")
        );
        repository.save(record, List.of(rawChunk(0, record.content(), 1)));
        repository.markIndexingReady(
            record.id(),
            List.of(embeddedChunk(0, record.content(), 1)),
            MaterialIndexingStatus.READY,
            null,
            null,
            record.updatedAt()
        );
        repository.enqueueMaterialsForSync(List.of(record.id()), Instant.parse("2026-04-17T11:00:00Z"));
        MaterialSearchSyncQueueEntry failedEntry = repository.claimNextSearchSyncBatch(
            Instant.parse("2026-04-17T11:00:05Z"),
            1
        ).getFirst();
        repository.markSearchSyncEntryFailed(
            failedEntry.materialId(),
            failedEntry.claimedAt(),
            "search.sync_failed",
            "Terminal failure",
            Instant.parse("2026-04-17T11:00:10Z")
        );

        SearchSyncRecoveryService.RequeueRunSummary firstRun = recoveryService.recoverFailedEventsAndWait(
            java.time.Duration.ofSeconds(5),
            java.time.Duration.ofMillis(10)
        );
        refreshWriteAlias();

        assertEquals(1, firstRun.requeuedFailedCount());
        assertEquals(1, documentsFor(record.id()).size());
        assertEquals(0, repository.getSearchSyncQueueSnapshot().failedCount());
        assertTrue(repository.findAllSearchSyncEntries().isEmpty());

        SearchSyncRecoveryService.RequeueRunSummary secondRun = recoveryService.recoverFailedEventsAndWait(
            java.time.Duration.ofSeconds(5),
            java.time.Duration.ofMillis(10)
        );
        refreshWriteAlias();

        assertEquals(0, secondRun.requeuedFailedCount());
        assertEquals(1, documentsFor(record.id()).size());
    }

    @Test
    void rebuildRecoveryClearsCurrentWriteIndexAndReplaysOnlySearchableActiveMaterials() throws Exception {
        indexAdminService.prepareWriteIndex("v2");
        Set<String> readTargetsBefore = aliasTargets("rag-chunks-it-read");

        StoredMaterialRecord ready = materialRecord(
            "Ready FAQ",
            "Готовая версия для rebuild.",
            "rebuild-lineage-ready",
            MaterialVersionState.ACTIVE,
            MaterialIndexingStatus.READY,
            Instant.parse("2026-04-17T12:00:00Z")
        );
        StoredMaterialRecord partial = materialRecord(
            "Partial FAQ",
            "Частично готовая версия для rebuild.",
            "rebuild-lineage-partial",
            MaterialVersionState.ACTIVE,
            MaterialIndexingStatus.PARTIAL_READY,
            Instant.parse("2026-04-17T12:01:00Z")
        );
        StoredMaterialRecord superseded = materialRecord(
            "Superseded FAQ",
            "Историческая версия.",
            "rebuild-lineage-history",
            MaterialVersionState.SUPERSEDED,
            MaterialIndexingStatus.READY,
            Instant.parse("2026-04-17T12:02:00Z")
        );
        StoredMaterialRecord pending = materialRecord(
            "Pending FAQ",
            "Pending version should not replay.",
            "rebuild-lineage-pending",
            MaterialVersionState.ACTIVE,
            MaterialIndexingStatus.PENDING,
            Instant.parse("2026-04-17T12:03:00Z")
        );
        StoredMaterialRecord failed = materialRecord(
            "Failed FAQ",
            "Failed version should not replay.",
            "rebuild-lineage-failed",
            MaterialVersionState.ACTIVE,
            MaterialIndexingStatus.FAILED,
            Instant.parse("2026-04-17T12:04:00Z")
        );

        repository.save(ready, List.of(rawChunk(0, ready.content(), 1)));
        repository.markIndexingReady(
            ready.id(),
            List.of(embeddedChunk(0, ready.content(), 1)),
            MaterialIndexingStatus.READY,
            null,
            null,
            ready.updatedAt()
        );
        repository.save(partial, List.of(rawChunk(0, partial.content(), 1)));
        repository.markIndexingReady(
            partial.id(),
            List.of(embeddedChunk(0, partial.content(), 1)),
            MaterialIndexingStatus.PARTIAL_READY,
            "material.partial_ocr",
            "Partial OCR warning",
            partial.updatedAt()
        );
        repository.save(superseded, List.of(rawChunk(0, superseded.content(), 1)));
        repository.markIndexingReady(
            superseded.id(),
            List.of(embeddedChunk(0, superseded.content(), 1)),
            MaterialIndexingStatus.READY,
            null,
            null,
            superseded.updatedAt()
        );
        repository.save(pending, List.of(rawChunk(0, pending.content(), 1)));
        repository.save(failed, List.of(rawChunk(0, failed.content(), 1)));

        repository.enqueueMaterialsForSync(List.of(pending.id()), Instant.parse("2026-04-17T12:05:00Z"));
        MaterialSearchSyncQueueEntry failedPendingEntry = repository.claimNextSearchSyncBatch(
            Instant.parse("2026-04-17T12:05:05Z"),
            1
        ).getFirst();
        repository.markSearchSyncEntryFailed(
            failedPendingEntry.materialId(),
            failedPendingEntry.claimedAt(),
            "search.sync_failed",
            "Terminal failure",
            Instant.parse("2026-04-17T12:05:10Z")
        );

        indexDocument(
            "rag-chunks-it-v2",
            new SearchableChunkDocument(
                "stale-doc",
                "stale-doc",
                "ghost-material",
                "ghost-lineage",
                "Ghost title",
                "stale rebuild payload",
                1,
                "direct-text",
                false,
                "text",
                Instant.parse("2026-04-17T11:59:00Z")
            )
        );
        elasticsearchClient.indices().refresh(refresh -> refresh.index("rag-chunks-it-v2"));

        SearchSyncRecoveryService.RebuildRunSummary summary = recoveryService.rebuildCurrentWriteIndexAndWait(
            java.time.Duration.ofSeconds(5),
            java.time.Duration.ofMillis(10)
        );
        elasticsearchClient.indices().refresh(refresh -> refresh.index("rag-chunks-it-v2"));

        assertEquals("rag-chunks-it-v2", summary.writeIndexName());
        assertTrue(summary.clearedDocumentCount() >= 1);
        assertEquals(1, summary.requeuedFailedCount());
        assertEquals(2, summary.replayEnqueuedCount());
        assertEquals(readTargetsBefore, aliasTargets("rag-chunks-it-read"));
        assertEquals(Set.of("rag-chunks-it-v2"), aliasTargets("rag-chunks-it-write"));
        assertTrue(documentsFor("ghost-material").isEmpty());
        assertEquals(1, documentsFor(ready.id()).size());
        assertEquals(1, documentsFor(partial.id()).size());
        assertTrue(documentsFor(superseded.id()).isEmpty());
        assertTrue(documentsFor(pending.id()).isEmpty());
        assertTrue(documentsFor(failed.id()).isEmpty());
        assertEquals(0, repository.getSearchSyncQueueSnapshot().failedCount());
    }

    private void indexDocument(String indexName, SearchableChunkDocument document) throws IOException {
        elasticsearchClient.index(index -> index
            .index(indexName)
            .id(document.documentId())
            .document(document)
        );
    }

    private List<SearchableChunkDocument> documentsFor(String materialId) throws IOException {
        SearchResponse<SearchableChunkDocument> response = elasticsearchClient.search(search -> search
                .index("rag-chunks-it-write")
                .size(10)
                .query(query -> query.term(term -> term.field("materialId").value(materialId))),
            SearchableChunkDocument.class
        );
        return response.hits().hits().stream()
            .map(hit -> hit.source())
            .filter(document -> document != null)
            .toList();
    }

    private void refreshWriteAlias() throws IOException {
        elasticsearchClient.indices().refresh(refresh -> refresh.index("rag-chunks-it-write"));
    }

    private Set<String> aliasTargets(String aliasName) throws IOException {
        return elasticsearchClient.indices().getAlias(request -> request.name(aliasName)).result().keySet();
    }

    private StoredEmbeddedMaterialChunk embeddedChunk(int index, String text, Integer page) {
        return new StoredEmbeddedMaterialChunk(
            index,
            text,
            page,
            "direct-text",
            false,
            embeddingClient.embed(text)
        );
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
            List.of(rawChunk(0, content, 1)),
            status,
            versionState,
            null,
            null,
            timestamp,
            timestamp
        );
    }

    private StoredMaterialChunk rawChunk(int index, String text, Integer page) {
        return new StoredMaterialChunk(index, text, List.of(), page, "direct-text", false);
    }
}
