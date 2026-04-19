package com.example.demo.infrastructure.material;

import com.example.demo.model.KnowledgeScope;
import com.example.demo.model.MaterialSummary;
import com.example.demo.model.RetrievalFilters;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class MaterialInfrastructureConfig {

    @Bean
    PostgresMaterialJdbcSupport postgresMaterialJdbcSupport(
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        return new PostgresMaterialJdbcSupport(jdbcTemplate, transactionManager);
    }

    @Bean
    PostgresMaterialCatalogRepository postgresMaterialCatalogRepository(PostgresMaterialJdbcSupport support) {
        return new PostgresMaterialCatalogRepository(support);
    }

    @Bean
    PostgresMaterialLineageRepository postgresMaterialLineageRepository(PostgresMaterialJdbcSupport support) {
        return new PostgresMaterialLineageRepository(support);
    }

    @Bean
    PostgresMaterialChunkingRepository postgresMaterialChunkingRepository(PostgresMaterialJdbcSupport support) {
        return new PostgresMaterialChunkingRepository(support);
    }

    @Bean
    PostgresMaterialIndexingQueueRepository postgresMaterialIndexingQueueRepository(PostgresMaterialJdbcSupport support) {
        return new PostgresMaterialIndexingQueueRepository(support);
    }

    @Bean
    PostgresMaterialSemanticSearchRepository postgresMaterialSemanticSearchRepository(PostgresMaterialJdbcSupport support) {
        return new PostgresMaterialSemanticSearchRepository(support);
    }

    @Bean
    PostgresLexicalSearchProvider postgresLexicalSearchProvider(PostgresMaterialJdbcSupport support) {
        return new PostgresLexicalSearchProvider(support);
    }

    @Bean
    PostgresMaterialSearchSyncQueueRepository postgresMaterialSearchSyncQueueRepository(PostgresMaterialJdbcSupport support) {
        return new PostgresMaterialSearchSyncQueueRepository(support);
    }

    @Bean
    PostgresMaterialSearchableSnapshotRepository postgresMaterialSearchableSnapshotRepository(PostgresMaterialJdbcSupport support) {
        return new PostgresMaterialSearchableSnapshotRepository(support);
    }

    @Bean
    PostgresQualityLayerMetricsRepository postgresQualityLayerMetricsRepository(PostgresMaterialJdbcSupport support) {
        return new PostgresQualityLayerMetricsRepository(support);
    }
}

final class PostgresMaterialCatalogRepository implements MaterialCatalogRepository {

    private final PostgresMaterialJdbcSupport support;

    PostgresMaterialCatalogRepository(PostgresMaterialJdbcSupport support) {
        this.support = support;
    }

    @Override
    public List<StoredMaterialRecord> findAll() {
        return support.findAll();
    }

    @Override
    public List<MaterialSummary> findSummaries(int offset, int limit) {
        return support.findSummaries(offset, limit);
    }

    @Override
    public List<StoredMaterialRecord> findByIds(Collection<String> ids) {
        return support.findByIds(ids);
    }

    @Override
    public List<StoredMaterialRecord> findActivePageAfter(Instant createdAt, String id, int limit) {
        return support.findActivePageAfter(createdAt, id, limit);
    }

    @Override
    public Optional<StoredMaterialRecord> findById(String id) {
        return support.findById(id);
    }

    @Override
    public Optional<String> findSourceKeyById(String id) {
        return support.findSourceKeyById(id);
    }

    @Override
    public Optional<StoredMaterialRecord> findBySourceKeyAndContentHash(String sourceKey, String contentHash) {
        return support.findBySourceKeyAndContentHash(sourceKey, contentHash);
    }

    @Override
    public MaterialRetrievalScopeSnapshot describeRetrievalScope(
        KnowledgeScope knowledgeScope,
        RetrievalFilters retrievalFilters,
        Instant uploadedAfterInclusive,
        Instant uploadedBeforeExclusive
    ) {
        return support.describeRetrievalScope(
            knowledgeScope,
            retrievalFilters,
            uploadedAfterInclusive,
            uploadedBeforeExclusive
        );
    }

    @Override
    public StoredMaterialRecord save(
        StoredMaterialRecord record,
        String chunkProfile,
        List<StoredMaterialChunk> chunks,
        List<StoredMaterialSegment> segments
    ) {
        return support.save(record, chunkProfile, chunks, segments);
    }

    @Override
    public List<StoredMaterialRecord> findAllBySourceKey(String sourceKey) {
        return support.findAllBySourceKey(sourceKey);
    }

    @Override
    public void delete(String id) {
        support.delete(id);
    }

    @Override
    public int countMaterials() {
        return support.countMaterials();
    }

    @Override
    public int countActiveMaterials() {
        return support.countActiveMaterials();
    }

    @Override
    public int countReadyMaterials() {
        return support.countReadyMaterials();
    }

    @Override
    public List<StoredMaterialRecord> supersedeActiveVersions(
        String sourceKey,
        String supersededByMaterialId,
        String excludeMaterialId,
        String supersedeReason,
        Instant updatedAt
    ) {
        return support.supersedeActiveVersions(
            sourceKey,
            supersededByMaterialId,
            excludeMaterialId,
            supersedeReason,
            updatedAt
        );
    }

    @Override
    public Optional<StoredMaterialRecord> findLatestBySourceKeyAndVersionState(String sourceKey, com.example.demo.model.MaterialVersionState versionState) {
        return support.findLatestBySourceKeyAndVersionState(sourceKey, versionState);
    }

    @Override
    public StoredMaterialRecord updateVersionState(
        String materialId,
        com.example.demo.model.MaterialVersionState versionState,
        String supersededByMaterialId,
        String supersedeReason,
        Instant updatedAt
    ) {
        return support.updateVersionState(materialId, versionState, supersededByMaterialId, supersedeReason, updatedAt);
    }
}

final class PostgresMaterialLineageRepository implements MaterialLineageRepository {

    private final PostgresMaterialJdbcSupport support;

    PostgresMaterialLineageRepository(PostgresMaterialJdbcSupport support) {
        this.support = support;
    }

    @Override
    public String resolveSourceKey(MaterialLineageIdentity identity) {
        return support.resolveSourceKey(identity);
    }

    @Override
    public void lockLineage(String sourceKey) {
        support.lockLineage(sourceKey);
    }
}

final class PostgresMaterialChunkingRepository implements MaterialChunkingRepository {

    private final PostgresMaterialJdbcSupport support;

    PostgresMaterialChunkingRepository(PostgresMaterialJdbcSupport support) {
        this.support = support;
    }

    @Override
    public List<StoredMaterialSegment> findSegments(String materialId) {
        return support.findSegments(materialId);
    }

    @Override
    public String findChunkProfile(String materialId) {
        return support.findChunkProfile(materialId);
    }

    @Override
    public void replaceChunking(
        String materialId,
        String chunkProfile,
        List<StoredMaterialChunk> chunks,
        List<StoredMaterialSegment> segments,
        Instant updatedAt
    ) {
        support.replaceChunking(materialId, chunkProfile, chunks, segments, updatedAt);
    }

    @Override
    public List<StoredMaterialChunk> findChunks(String materialId) {
        return support.findChunks(materialId);
    }
}

final class PostgresQualityLayerMetricsRepository implements QualityLayerMetricsRepository {

    private final PostgresMaterialJdbcSupport support;

    PostgresQualityLayerMetricsRepository(PostgresMaterialJdbcSupport support) {
        this.support = support;
    }

    @Override
    public QualityLayerCoverageSnapshot qualityLayerCoverageSnapshot() {
        return support.qualityLayerCoverageSnapshot();
    }
}

final class PostgresMaterialIndexingQueueRepository implements MaterialIndexingQueueRepository {

    private final PostgresMaterialJdbcSupport support;

    PostgresMaterialIndexingQueueRepository(PostgresMaterialJdbcSupport support) {
        this.support = support;
    }

    @Override
    public StoredMaterialRecord markIndexingPending(String materialId, String reasonCode, String reasonMessage, Instant updatedAt) {
        return support.markIndexingPending(materialId, reasonCode, reasonMessage, updatedAt);
    }

    @Override
    public void markIndexingReady(
        String materialId,
        List<StoredEmbeddedMaterialChunk> chunks,
        com.example.demo.model.MaterialIndexingStatus status,
        String reasonCode,
        String reasonMessage,
        Instant updatedAt
    ) {
        support.markIndexingReady(materialId, chunks, status, reasonCode, reasonMessage, updatedAt);
    }

    @Override
    public void markIndexingFailed(String materialId, String code, String message, Instant updatedAt) {
        support.markIndexingFailed(materialId, code, message, updatedAt);
    }

    @Override
    public void rescheduleIndexing(String materialId, String code, String message, Instant updatedAt, Instant nextRetryAt) {
        support.rescheduleIndexing(materialId, code, message, updatedAt, nextRetryAt);
    }

    @Override
    public void resetExpiredIndexingClaims(Instant staleBefore, Instant now) {
        support.resetExpiredIndexingClaims(staleBefore, now);
    }

    @Override
    public Optional<MaterialIndexingLease> claimNextIndexing(Instant now) {
        return support.claimNextIndexing(now);
    }

    @Override
    public boolean hasPendingIndexing(Instant now) {
        return support.hasPendingIndexing(now);
    }

    @Override
    public IndexingQueueSnapshot getIndexingQueueSnapshot() {
        return support.getIndexingQueueSnapshot();
    }
}

final class PostgresMaterialSemanticSearchRepository implements SemanticSearchRepository {

    private final PostgresMaterialJdbcSupport support;

    PostgresMaterialSemanticSearchRepository(PostgresMaterialJdbcSupport support) {
        this.support = support;
    }

    @Override
    public List<MaterialChunkSearchMatch> searchSemantic(float[] queryEmbedding, int limit) {
        return support.searchSemantic(queryEmbedding, limit);
    }

    @Override
    public List<MaterialChunkSearchMatch> searchSemantic(float[] queryEmbedding, int limit, Set<String> allowedMaterialIds) {
        return support.searchSemantic(queryEmbedding, limit, allowedMaterialIds);
    }

    @Override
    public List<MaterialChunkSearchMatch> searchSemantic(
        float[] queryEmbedding,
        int limit,
        Set<String> allowedMaterialIds,
        RetrievalFilters filters
    ) {
        return support.searchSemantic(queryEmbedding, limit, allowedMaterialIds, filters);
    }
}

final class PostgresLexicalSearchProvider implements LexicalSearchProvider {

    private final PostgresMaterialJdbcSupport support;

    PostgresLexicalSearchProvider(PostgresMaterialJdbcSupport support) {
        this.support = support;
    }

    @Override
    public LexicalProviderType type() {
        return support.type();
    }

    @Override
    public List<MaterialChunkSearchMatch> search(String query, int limit) {
        return support.search(query, limit);
    }

    @Override
    public List<MaterialChunkSearchMatch> search(String query, int limit, Set<String> allowedMaterialIds) {
        return support.search(query, limit, allowedMaterialIds);
    }

    @Override
    public List<MaterialChunkSearchMatch> search(
        String query,
        int limit,
        Set<String> allowedMaterialIds,
        RetrievalFilters filters
    ) {
        return support.search(query, limit, allowedMaterialIds, filters);
    }
}

final class PostgresMaterialSearchSyncQueueRepository implements MaterialSearchSyncQueueRepository {

    private final PostgresMaterialJdbcSupport support;

    PostgresMaterialSearchSyncQueueRepository(PostgresMaterialJdbcSupport support) {
        this.support = support;
    }

    @Override
    public void enqueueMaterialsForSync(Collection<String> materialIds, Instant requestedAt) {
        support.enqueueMaterialsForSync(materialIds, requestedAt);
    }

    @Override
    public void enqueueMaterialsForSync(
        Collection<String> materialIds,
        SearchSyncOperationType operationType,
        Instant requestedAt
    ) {
        support.enqueueMaterialsForSync(materialIds, operationType, requestedAt);
    }

    @Override
    public List<MaterialSearchSyncQueueEntry> findAllSearchSyncEntries() {
        return support.findAllSearchSyncEntries();
    }

    @Override
    public int requeueFailedSearchSyncEntries(Instant now) {
        return support.requeueFailedSearchSyncEntries(now);
    }

    @Override
    public void resetExpiredSearchSyncClaims(Instant staleBefore, Instant now) {
        support.resetExpiredSearchSyncClaims(staleBefore, now);
    }

    @Override
    public List<MaterialSearchSyncQueueEntry> claimNextSearchSyncBatch(Instant now, int limit) {
        return support.claimNextSearchSyncBatch(now, limit);
    }

    @Override
    public boolean hasPendingSearchSyncEvents(Instant now) {
        return support.hasPendingSearchSyncEvents(now);
    }

    @Override
    public void completeSearchSyncEntry(String materialId, Instant claimedAt, Instant now) {
        support.completeSearchSyncEntry(materialId, claimedAt, now);
    }

    @Override
    public void markSearchSyncEntryForRetry(
        String materialId,
        Instant claimedAt,
        String errorCode,
        String errorMessage,
        Instant now,
        Instant nextAttemptAt
    ) {
        support.markSearchSyncEntryForRetry(materialId, claimedAt, errorCode, errorMessage, now, nextAttemptAt);
    }

    @Override
    public void markSearchSyncEntryFailed(
        String materialId,
        Instant claimedAt,
        String errorCode,
        String errorMessage,
        Instant now
    ) {
        support.markSearchSyncEntryFailed(materialId, claimedAt, errorCode, errorMessage, now);
    }

    @Override
    public SearchSyncQueueSnapshot getSearchSyncQueueSnapshot() {
        return support.getSearchSyncQueueSnapshot();
    }
}

final class PostgresMaterialSearchableSnapshotRepository implements MaterialSearchableSnapshotRepository {

    private final PostgresMaterialJdbcSupport support;

    PostgresMaterialSearchableSnapshotRepository(PostgresMaterialJdbcSupport support) {
        this.support = support;
    }

    @Override
    public SearchableMaterialSnapshot resolveSearchableSnapshot(String materialId) {
        return support.resolveSearchableSnapshot(materialId);
    }

    @Override
    public List<String> findAllSearchableMaterialIds() {
        return support.findAllSearchableMaterialIds();
    }
}
