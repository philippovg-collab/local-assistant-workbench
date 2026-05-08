package com.example.demo.support;

import com.example.demo.service.material.ChunkProfile;
import com.example.demo.service.material.LexicalProviderType;
import com.example.demo.service.material.MaterialChunkSearchMatch;
import com.example.demo.service.material.MaterialIndexingLease;
import com.example.demo.service.material.MaterialLineageIdentity;
import com.example.demo.service.material.MaterialRetrievalScopeSnapshot;
import com.example.demo.service.material.MaterialSearchScope;
import com.example.demo.service.material.MaterialSearchSyncQueueEntry;
import com.example.demo.service.material.QualityLayerCoverageSnapshot;
import com.example.demo.service.material.SearchSyncDeliveryState;
import com.example.demo.service.material.SearchSyncOperationType;
import com.example.demo.service.material.SearchableMaterialChunkSnapshot;
import com.example.demo.service.material.SearchableMaterialSnapshot;
import com.example.demo.service.material.StoredEmbeddedMaterialChunk;
import com.example.demo.service.material.StoredMaterialChunk;
import com.example.demo.service.material.StoredMaterialRecord;
import com.example.demo.service.material.StoredMaterialSegment;
import com.example.demo.service.material.port.LexicalSearchProvider;
import com.example.demo.service.material.port.MaterialCatalogRepository;
import com.example.demo.service.material.port.MaterialChunkingRepository;
import com.example.demo.service.material.port.MaterialIndexingQueueRepository;
import com.example.demo.service.material.port.MaterialLineageRepository;
import com.example.demo.service.material.port.MaterialSearchSyncQueueRepository;
import com.example.demo.service.material.port.MaterialSearchableSnapshotRepository;
import com.example.demo.service.material.port.QualityLayerMetricsRepository;
import com.example.demo.service.material.port.SemanticSearchRepository;

import com.example.demo.model.KnowledgeScope;
import com.example.demo.model.DocumentStatus;
import com.example.demo.model.MaterialIndexingStatus;
import com.example.demo.model.MaterialMetadataSnapshot;
import com.example.demo.model.MaterialSummary;
import com.example.demo.model.RetrievalFilters;
import com.example.demo.model.MaterialVersionState;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Test double for pure service/domain behavior.
 * It does not model PostgreSQL constraints, cascades, locking, or transaction ordering.
 */
public class InMemoryMaterialRepository implements
    MaterialCatalogRepository,
    MaterialLineageRepository,
    MaterialChunkingRepository,
    SemanticSearchRepository,
    LexicalSearchProvider,
    MaterialIndexingQueueRepository,
    MaterialSearchSyncQueueRepository,
    MaterialSearchableSnapshotRepository,
    QualityLayerMetricsRepository {

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{N}]+");

    private final Map<String, StoredMaterialRecord> recordsById = new LinkedHashMap<>();
    private final Map<String, String> idsBySourceKeyAndContentHash = new LinkedHashMap<>();
    private final Map<String, String> sourceKeysByIdentity = new LinkedHashMap<>();
    private final Map<String, List<StoredMaterialChunk>> rawChunksByMaterialId = new LinkedHashMap<>();
    private final Map<String, List<StoredMaterialSegment>> segmentsByMaterialId = new LinkedHashMap<>();
    private final Map<String, String> chunkProfilesByMaterialId = new LinkedHashMap<>();
    private final Map<String, List<StoredEmbeddedMaterialChunk>> embeddedChunksByMaterialId = new LinkedHashMap<>();
    private final Map<String, Integer> attemptsByMaterialId = new LinkedHashMap<>();
    private final Map<String, Instant> nextRetryAtByMaterialId = new LinkedHashMap<>();
    private final Map<String, Instant> claimedAtByMaterialId = new LinkedHashMap<>();
    private final Map<String, MaterialSearchSyncQueueEntry> searchSyncQueueByMaterialId = new LinkedHashMap<>();
    private final Map<String, Long> searchSyncIntentVersionByMaterialId = new LinkedHashMap<>();
    private final Map<String, Long> claimedSearchSyncIntentVersionByMaterialId = new LinkedHashMap<>();

    @Override
    public synchronized List<StoredMaterialRecord> findAll() {
        return recordsById.values().stream().toList();
    }

    @Override
    public synchronized List<MaterialSummary> findSummaries(int offset, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return recordsById.values().stream()
            .sorted(Comparator.comparing(StoredMaterialRecord::createdAt).reversed())
            .skip(Math.max(0, offset))
            .limit(limit)
            .map(this::toSummary)
            .toList();
    }

    @Override
    public synchronized List<MaterialSummary> findSummariesByWorkspace(String workspaceKey, int offset, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        String normalizedWorkspaceKey = workspaceKey == null ? null : workspaceKey.toLowerCase(Locale.ROOT);
        return recordsById.values().stream()
            .filter(record -> {
                String recordWorkspaceKey = record.metadata().workspaceKey();
                return normalizedWorkspaceKey != null
                    && recordWorkspaceKey != null
                    && normalizedWorkspaceKey.equals(recordWorkspaceKey.toLowerCase(Locale.ROOT));
            })
            .sorted(Comparator.comparing(StoredMaterialRecord::createdAt).reversed())
            .skip(Math.max(0, offset))
            .limit(limit)
            .map(this::toSummary)
            .toList();
    }

    @Override
    public synchronized List<StoredMaterialRecord> findByIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
            .filter(id -> id != null && !id.isBlank())
            .distinct()
            .map(recordsById::get)
            .filter(Objects::nonNull)
            .toList();
    }

    @Override
    public synchronized List<StoredMaterialRecord> findActivePageAfter(Instant createdAt, String id, int limit) {
        if (limit <= 0) {
            return List.of();
        }

        return recordsById.values().stream()
            .filter(record -> record.versionState() == MaterialVersionState.ACTIVE)
            .sorted(Comparator.comparing(StoredMaterialRecord::createdAt).thenComparing(StoredMaterialRecord::id))
            .filter(record -> createdAt == null || id == null || isAfterCursor(record, createdAt, id))
            .limit(limit)
            .toList();
    }

    @Override
    public synchronized Optional<StoredMaterialRecord> findById(String id) {
        return Optional.ofNullable(recordsById.get(id));
    }

    @Override
    public synchronized Optional<String> findSourceKeyById(String id) {
        return findById(id).map(StoredMaterialRecord::sourceKey);
    }

    @Override
    public synchronized Optional<StoredMaterialRecord> findBySourceKeyAndContentHash(String sourceKey, String contentHash) {
        return recordsById.values().stream()
            .filter(record -> record.sourceKey().equals(sourceKey) && record.contentHash().equals(contentHash))
            .sorted(Comparator
                .comparing((StoredMaterialRecord record) -> record.versionState() == MaterialVersionState.ACTIVE ? 0 : 1)
                .thenComparing(StoredMaterialRecord::lineageVersion, Comparator.reverseOrder())
                .thenComparing(StoredMaterialRecord::createdAt, Comparator.reverseOrder()))
            .findFirst();
    }

    @Override
    public synchronized String resolveSourceKey(MaterialLineageIdentity identity) {
        return sourceKeysByIdentity.computeIfAbsent(identityKey(identity), ignored -> identity.sourceKey());
    }

    @Override
    public synchronized StoredMaterialRecord save(StoredMaterialRecord record, List<StoredMaterialChunk> chunks) {
        return save(record, ChunkProfile.FIXED_V1.propertyValue(), chunks, List.of());
    }

    @Override
    public synchronized StoredMaterialRecord save(
        StoredMaterialRecord record,
        String chunkProfile,
        List<StoredMaterialChunk> chunks,
        List<StoredMaterialSegment> segments
    ) {
        String contentKey = sourceKeyAndContentHashKey(record.sourceKey(), record.contentHash());
        int lineageVersion = record.lineageVersion() > 0 ? record.lineageVersion() : nextLineageVersion(record.sourceKey());
        StoredMaterialRecord persistedRecord = record.withLineageVersion(lineageVersion);
        recordsById.put(persistedRecord.id(), persistedRecord);
        idsBySourceKeyAndContentHash.put(contentKey, persistedRecord.id());
        rawChunksByMaterialId.put(persistedRecord.id(), chunks == null ? List.of() : List.copyOf(chunks));
        segmentsByMaterialId.put(persistedRecord.id(), segments == null ? List.of() : List.copyOf(segments));
        chunkProfilesByMaterialId.put(persistedRecord.id(), chunkProfile == null ? ChunkProfile.FIXED_V1.propertyValue() : chunkProfile);
        embeddedChunksByMaterialId.put(persistedRecord.id(), List.of());
        attemptsByMaterialId.put(persistedRecord.id(), 0);
        nextRetryAtByMaterialId.put(persistedRecord.id(), persistedRecord.updatedAt());
        claimedAtByMaterialId.put(persistedRecord.id(), null);
        return persistedRecord;
    }

    @Override
    public synchronized List<StoredMaterialChunk> findChunks(String materialId) {
        return rawChunksByMaterialId.getOrDefault(materialId, List.of());
    }

    @Override
    public synchronized List<StoredMaterialSegment> findSegments(String materialId) {
        return segmentsByMaterialId.getOrDefault(materialId, List.of());
    }

    @Override
    public synchronized String findChunkProfile(String materialId) {
        return chunkProfilesByMaterialId.getOrDefault(materialId, ChunkProfile.FIXED_V1.propertyValue());
    }

    @Override
    public synchronized void replaceChunking(
        String materialId,
        String chunkProfile,
        List<StoredMaterialChunk> chunks,
        List<StoredMaterialSegment> segments,
        Instant updatedAt
    ) {
        if (!recordsById.containsKey(materialId)) {
            return;
        }

        rawChunksByMaterialId.put(materialId, chunks == null ? List.of() : List.copyOf(chunks));
        segmentsByMaterialId.put(materialId, segments == null ? List.of() : List.copyOf(segments));
        chunkProfilesByMaterialId.put(materialId, chunkProfile == null ? ChunkProfile.FIXED_V1.propertyValue() : chunkProfile);
        embeddedChunksByMaterialId.put(materialId, List.of());
    }

    @Override
    public synchronized List<StoredMaterialRecord> findAllBySourceKey(String sourceKey) {
        return recordsById.values().stream()
            .filter(record -> record.sourceKey().equals(sourceKey))
            .sorted(Comparator
                .comparingInt(StoredMaterialRecord::lineageVersion)
                .reversed()
                .thenComparing(StoredMaterialRecord::createdAt, Comparator.reverseOrder())
                .thenComparing(StoredMaterialRecord::id, Comparator.reverseOrder()))
            .toList();
    }

    @Override
    public synchronized StoredMaterialRecord updateMetadata(
        String materialId,
        MaterialMetadataSnapshot metadata,
        Instant updatedAt
    ) {
        StoredMaterialRecord record = recordsById.get(materialId);
        if (record == null) {
            return null;
        }
        StoredMaterialRecord updated = copyWithMetadata(record, metadata, updatedAt);
        recordsById.put(materialId, updated);
        return updated;
    }

    @Override
    public synchronized void lockLineage(String sourceKey) {
        // synchronized repository methods already serialize the in-memory test double.
    }

    @Override
    public synchronized void delete(String id) {
        StoredMaterialRecord removed = recordsById.remove(id);
        rawChunksByMaterialId.remove(id);
        segmentsByMaterialId.remove(id);
        chunkProfilesByMaterialId.remove(id);
        embeddedChunksByMaterialId.remove(id);
        attemptsByMaterialId.remove(id);
        nextRetryAtByMaterialId.remove(id);
        claimedAtByMaterialId.remove(id);
        if (removed != null) {
            String contentKey = sourceKeyAndContentHashKey(removed.sourceKey(), removed.contentHash());
            findBySourceKeyAndContentHash(removed.sourceKey(), removed.contentHash())
                .ifPresentOrElse(
                    replacement -> idsBySourceKeyAndContentHash.put(contentKey, replacement.id()),
                    () -> idsBySourceKeyAndContentHash.remove(contentKey)
                );
        }
    }

    @Override
    public synchronized int countMaterials() {
        return recordsById.size();
    }

    @Override
    public synchronized int countMaterialsByWorkspace(String workspaceKey) {
        String normalizedWorkspaceKey = workspaceKey == null ? null : workspaceKey.toLowerCase(Locale.ROOT);
        return (int) recordsById.values().stream()
            .filter(record -> {
                String recordWorkspaceKey = record.metadata().workspaceKey();
                return normalizedWorkspaceKey != null
                    && recordWorkspaceKey != null
                    && normalizedWorkspaceKey.equals(recordWorkspaceKey.toLowerCase(Locale.ROOT));
            })
            .count();
    }

    @Override
    public synchronized int countActiveMaterials() {
        return (int) recordsById.values().stream()
            .filter(record -> record.versionState() == MaterialVersionState.ACTIVE)
            .count();
    }

    @Override
    public synchronized int countReadyMaterials() {
        return (int) recordsById.values().stream()
            .filter(this::isSearchable)
            .count();
    }

    @Override
    public synchronized QualityLayerCoverageSnapshot qualityLayerCoverageSnapshot() {
        int activeTotal = 0;
        int activeWithEffectiveMetadata = 0;
        int workspaceCovered = 0;
        int documentTypeCovered = 0;
        int documentStatusCovered = 0;
        int structuredProfileActive = 0;
        int partialReadyActive = 0;

        for (StoredMaterialRecord record : recordsById.values()) {
            if (record.versionState() != MaterialVersionState.ACTIVE) {
                continue;
            }
            activeTotal += 1;

            if (hasMeaningfulMetadata(record)) {
                activeWithEffectiveMetadata += 1;
            }
            if (record.metadata().documentType() != null && record.metadata().documentType() != com.example.demo.model.DocumentType.OTHER) {
                documentTypeCovered += 1;
            }
            if (hasText(record.metadata().workspaceKey())) {
                workspaceCovered += 1;
            }
            if (record.metadata().documentStatus() != null) {
                documentStatusCovered += 1;
            }
            if (ChunkProfile.STRUCTURED_V1.propertyValue().equals(chunkProfilesByMaterialId.get(record.id()))) {
                structuredProfileActive += 1;
            }
            if (record.status() == MaterialIndexingStatus.PARTIAL_READY) {
                partialReadyActive += 1;
            }
        }

        return new QualityLayerCoverageSnapshot(
            activeTotal,
            activeWithEffectiveMetadata,
            workspaceCovered,
            documentTypeCovered,
            documentStatusCovered,
            structuredProfileActive,
            partialReadyActive
        );
    }

    @Override
    public synchronized MaterialRetrievalScopeSnapshot describeRetrievalScope(
        KnowledgeScope knowledgeScope,
        RetrievalFilters retrievalFilters,
        Instant uploadedAfterInclusive,
        Instant uploadedBeforeExclusive
    ) {
        List<StoredMaterialRecord> allRecords = new ArrayList<>(recordsById.values());
        KnowledgeScope effectiveScope = knowledgeScope == null ? KnowledgeScope.empty() : knowledgeScope;
        RetrievalFilters effectiveFilters = retrievalFilters == null ? RetrievalFilters.empty() : retrievalFilters;
        List<StoredMaterialRecord> scopedRecords = allRecords.stream()
            .filter(record -> matchesKnowledgeScope(record, effectiveScope, uploadedAfterInclusive, uploadedBeforeExclusive))
            .filter(record -> matchesRetrievalFilters(record, effectiveFilters))
            .toList();
        return new MaterialRetrievalScopeSnapshot(
            allRecords.size(),
            (int) allRecords.stream().filter(record -> record.versionState() == MaterialVersionState.ACTIVE).count(),
            (int) allRecords.stream().filter(this::isSearchable).count(),
            scopedRecords.size(),
            (int) scopedRecords.stream().filter(record -> record.versionState() == MaterialVersionState.ACTIVE).count(),
            (int) scopedRecords.stream().filter(this::isSearchable).count()
        );
    }

    @Override
    public synchronized List<StoredMaterialRecord> supersedeActiveVersions(
        String sourceKey,
        String supersededByMaterialId,
        String excludeMaterialId,
        String supersedeReason,
        Instant updatedAt
    ) {
        List<StoredMaterialRecord> affectedRecords = new ArrayList<>();
        for (Map.Entry<String, StoredMaterialRecord> entry : recordsById.entrySet()) {
            StoredMaterialRecord record = entry.getValue();
            if (!record.sourceKey().equals(sourceKey)) {
                continue;
            }
            if (record.versionState() != MaterialVersionState.ACTIVE) {
                continue;
            }
            if (record.id().equals(excludeMaterialId)) {
                continue;
            }
            affectedRecords.add(record);
            entry.setValue(copyWithVersionState(
                record,
                MaterialVersionState.SUPERSEDED,
                supersededByMaterialId,
                supersedeReason,
                updatedAt
            ));
        }
        return List.copyOf(affectedRecords);
    }

    @Override
    public synchronized Optional<StoredMaterialRecord> findLatestBySourceKeyAndVersionState(
        String sourceKey,
        MaterialVersionState versionState
    ) {
        return recordsById.values().stream()
            .filter(record -> record.sourceKey().equals(sourceKey))
            .filter(record -> record.versionState() == versionState)
            .max(Comparator
                .comparingInt(StoredMaterialRecord::lineageVersion)
                .thenComparing(StoredMaterialRecord::createdAt)
                .thenComparing(StoredMaterialRecord::id));
    }

    @Override
    public synchronized StoredMaterialRecord updateVersionState(
        String materialId,
        MaterialVersionState versionState,
        String supersededByMaterialId,
        String supersedeReason,
        Instant updatedAt
    ) {
        StoredMaterialRecord record = recordsById.get(materialId);
        if (record == null) {
            return null;
        }

        StoredMaterialRecord updated = copyWithVersionState(
            record,
            versionState,
            supersededByMaterialId,
            supersedeReason,
            updatedAt
        );
        recordsById.put(materialId, updated);
        return updated;
    }

    @Override
    public synchronized List<MaterialChunkSearchMatch> searchSemantic(float[] queryEmbedding, int limit) {
        return searchSemantic(queryEmbedding, limit, MaterialSearchScope.unscoped());
    }

    @Override
    public synchronized List<MaterialChunkSearchMatch> searchSemantic(
        float[] queryEmbedding,
        int limit,
        Set<String> allowedMaterialIds
    ) {
        return searchSemantic(queryEmbedding, limit, MaterialSearchScope.fromLegacyMaterialIds(allowedMaterialIds));
    }

    @Override
    public synchronized List<MaterialChunkSearchMatch> searchSemantic(
        float[] queryEmbedding,
        int limit,
        Set<String> allowedMaterialIds,
        RetrievalFilters filters
    ) {
        return searchSemantic(
            queryEmbedding,
            limit,
            MaterialSearchScope.fromLegacyMaterialIds(allowedMaterialIds, filters)
        );
    }

    @Override
    public synchronized List<MaterialChunkSearchMatch> searchSemantic(
        float[] queryEmbedding,
        int limit,
        MaterialSearchScope scope
    ) {
        if (queryEmbedding == null || queryEmbedding.length == 0 || limit <= 0) {
            return List.of();
        }
        MaterialSearchScope safeScope = scope == null ? MaterialSearchScope.unscoped() : scope;
        if (safeScope.isNoResults()) {
            return List.of();
        }

        return readyRecords().stream()
            .filter(record -> matchesMaterialSearchScope(record, safeScope))
            .flatMap(record -> embeddedChunksByMaterialId.getOrDefault(record.id(), List.of()).stream()
                .map(chunk -> new SemanticCandidate(record, chunk, cosineDistance(queryEmbedding, chunk.embedding()))))
            .sorted(Comparator
                .comparingDouble(SemanticCandidate::distance)
                .thenComparing(candidate -> candidate.chunk().page() == null ? Integer.MAX_VALUE : candidate.chunk().page())
                .thenComparing(candidate -> candidate.chunk().index()))
            .limit(limit)
            .map(candidate -> new MaterialChunkSearchMatch(
                candidate.record().id(),
                candidate.chunk().index(),
                candidate.record().title(),
                candidate.chunk().text(),
                candidate.chunk().page(),
                candidate.chunk().extractor(),
                candidate.chunk().ocrUsed(),
                candidate.chunk().chunkType(),
                candidate.distance(),
                null
            ))
            .toList();
    }

    @Override
    public LexicalProviderType type() {
        return LexicalProviderType.POSTGRES;
    }

    @Override
    public synchronized List<MaterialChunkSearchMatch> search(String query, int limit) {
        return search(query, limit, MaterialSearchScope.unscoped());
    }

    @Override
    public synchronized List<MaterialChunkSearchMatch> search(String query, int limit, Set<String> allowedMaterialIds) {
        return search(query, limit, MaterialSearchScope.fromLegacyMaterialIds(allowedMaterialIds));
    }

    @Override
    public synchronized List<MaterialChunkSearchMatch> search(
        String query,
        int limit,
        Set<String> allowedMaterialIds,
        RetrievalFilters filters
    ) {
        return search(query, limit, MaterialSearchScope.fromLegacyMaterialIds(allowedMaterialIds, filters));
    }

    @Override
    public synchronized List<MaterialChunkSearchMatch> search(
        String query,
        int limit,
        MaterialSearchScope scope
    ) {
        if (query == null || query.isBlank() || limit <= 0) {
            return List.of();
        }
        MaterialSearchScope safeScope = scope == null ? MaterialSearchScope.unscoped() : scope;
        if (safeScope.isNoResults()) {
            return List.of();
        }

        Set<String> queryTokens = tokenize(query);
        String normalizedPrompt = normalize(query);

        return readyRecords().stream()
            .filter(record -> matchesMaterialSearchScope(record, safeScope))
            .flatMap(record -> embeddedChunksByMaterialId.getOrDefault(record.id(), List.of()).stream()
                .map(chunk -> new LexicalCandidate(record, chunk, lexicalScore(record.title(), chunk.text(), normalizedPrompt, queryTokens))))
            .filter(candidate -> candidate.score() > 0)
            .sorted(Comparator
                .comparingInt(LexicalCandidate::score)
                .reversed()
                .thenComparing(candidate -> candidate.chunk().page() == null ? Integer.MAX_VALUE : candidate.chunk().page())
                .thenComparing(candidate -> candidate.chunk().index()))
            .limit(limit)
            .map(candidate -> new MaterialChunkSearchMatch(
                candidate.record().id(),
                candidate.chunk().index(),
                candidate.record().title(),
                candidate.chunk().text(),
                candidate.chunk().page(),
                candidate.chunk().extractor(),
                candidate.chunk().ocrUsed(),
                candidate.chunk().chunkType(),
                null,
                (double) candidate.score()
            ))
            .toList();
    }

    @Override
    public synchronized StoredMaterialRecord markIndexingPending(
        String materialId,
        String reasonCode,
        String reasonMessage,
        Instant updatedAt
    ) {
        StoredMaterialRecord record = recordsById.get(materialId);
        if (record == null) {
            return null;
        }

        claimedAtByMaterialId.put(materialId, null);
        nextRetryAtByMaterialId.put(materialId, updatedAt);
        StoredMaterialRecord updated = copyWithStatus(
            record,
            MaterialIndexingStatus.PENDING,
            reasonCode,
            reasonMessage,
            updatedAt
        );
        recordsById.put(materialId, updated);
        return updated;
    }

    @Override
    public synchronized void markIndexingReady(
        String materialId,
        List<StoredEmbeddedMaterialChunk> chunks,
        MaterialIndexingStatus status,
        String reasonCode,
        String reasonMessage,
        Instant updatedAt
    ) {
        StoredMaterialRecord record = recordsById.get(materialId);
        if (record == null) {
            return;
        }

        embeddedChunksByMaterialId.put(materialId, chunks == null ? List.of() : List.copyOf(chunks));
        claimedAtByMaterialId.put(materialId, null);
        nextRetryAtByMaterialId.put(materialId, null);
        recordsById.put(materialId, copyWithStatus(record, status, reasonCode, reasonMessage, updatedAt));
    }

    @Override
    public synchronized void markIndexingFailed(String materialId, String code, String message, Instant updatedAt) {
        StoredMaterialRecord record = recordsById.get(materialId);
        if (record == null) {
            return;
        }

        claimedAtByMaterialId.put(materialId, null);
        nextRetryAtByMaterialId.put(materialId, null);
        recordsById.put(materialId, copyWithStatus(record, MaterialIndexingStatus.FAILED, code, message, updatedAt));
    }

    @Override
    public synchronized void rescheduleIndexing(String materialId, String code, String message, Instant updatedAt, Instant nextRetryAt) {
        StoredMaterialRecord record = recordsById.get(materialId);
        if (record == null) {
            return;
        }

        claimedAtByMaterialId.put(materialId, null);
        nextRetryAtByMaterialId.put(materialId, nextRetryAt);
        recordsById.put(materialId, copyWithStatus(record, MaterialIndexingStatus.PENDING, code, message, updatedAt));
    }

    @Override
    public synchronized void resetExpiredIndexingClaims(Instant staleBefore, Instant now) {
        for (Map.Entry<String, StoredMaterialRecord> entry : recordsById.entrySet()) {
            String materialId = entry.getKey();
            StoredMaterialRecord record = entry.getValue();
            Instant claimedAt = claimedAtByMaterialId.get(materialId);
            if (record.versionState() != MaterialVersionState.ACTIVE) {
                continue;
            }
            if (record.status() == MaterialIndexingStatus.IN_PROGRESS && claimedAt != null && claimedAt.isBefore(staleBefore)) {
                claimedAtByMaterialId.put(materialId, null);
                recordsById.put(materialId, copyWithStatus(record, MaterialIndexingStatus.PENDING, record.statusReasonCode(), record.statusReasonMessage(), now));
            }
        }
    }

    @Override
    public synchronized Optional<MaterialIndexingLease> claimNextIndexing(Instant now) {
        return recordsById.values().stream()
            .filter(record -> record.versionState() == MaterialVersionState.ACTIVE)
            .filter(record -> record.status() == MaterialIndexingStatus.PENDING)
            .filter(record -> {
                Instant nextRetryAt = nextRetryAtByMaterialId.get(record.id());
                return nextRetryAt == null || !nextRetryAt.isAfter(now);
            })
            .sorted(Comparator.comparing(record -> {
                Instant nextRetryAt = nextRetryAtByMaterialId.get(record.id());
                return nextRetryAt == null ? record.createdAt() : nextRetryAt;
            }))
            .findFirst()
            .map(record -> {
                int attempt = attemptsByMaterialId.getOrDefault(record.id(), 0) + 1;
                attemptsByMaterialId.put(record.id(), attempt);
                claimedAtByMaterialId.put(record.id(), now);
                recordsById.put(record.id(), copyWithStatus(record, MaterialIndexingStatus.IN_PROGRESS, record.statusReasonCode(), record.statusReasonMessage(), now));
                return new MaterialIndexingLease(record, attempt);
            });
    }

    @Override
    public synchronized boolean hasPendingIndexing(Instant now) {
        return recordsById.values().stream()
            .filter(record -> record.versionState() == MaterialVersionState.ACTIVE)
            .filter(record -> record.status() == MaterialIndexingStatus.PENDING)
            .anyMatch(record -> {
                Instant nextRetryAt = nextRetryAtByMaterialId.get(record.id());
                return nextRetryAt == null || !nextRetryAt.isAfter(now);
            });
    }

    @Override
    public synchronized IndexingQueueSnapshot getIndexingQueueSnapshot() {
        int pendingCount = 0;
        int inProgressCount = 0;
        int failedCount = 0;
        Instant nextRetryAt = null;
        Instant oldestPendingAt = null;
        Instant oldestInProgressAt = null;

        for (StoredMaterialRecord record : recordsById.values()) {
            if (record.versionState() != MaterialVersionState.ACTIVE) {
                continue;
            }

            if (record.status() == MaterialIndexingStatus.PENDING) {
                pendingCount += 1;
                Instant candidateNextRetryAt = nextRetryAtByMaterialId.get(record.id());
                if (candidateNextRetryAt != null && (nextRetryAt == null || candidateNextRetryAt.isBefore(nextRetryAt))) {
                    nextRetryAt = candidateNextRetryAt;
                }
                if (oldestPendingAt == null || record.updatedAt().isBefore(oldestPendingAt)) {
                    oldestPendingAt = record.updatedAt();
                }
            } else if (record.status() == MaterialIndexingStatus.IN_PROGRESS) {
                inProgressCount += 1;
                Instant claimedAt = claimedAtByMaterialId.getOrDefault(record.id(), record.updatedAt());
                if (oldestInProgressAt == null || claimedAt.isBefore(oldestInProgressAt)) {
                    oldestInProgressAt = claimedAt;
                }
            } else if (record.status() == MaterialIndexingStatus.FAILED) {
                failedCount += 1;
            }
        }

        return new IndexingQueueSnapshot(
            pendingCount,
            inProgressCount,
            failedCount,
            nextRetryAt,
            oldestPendingAt,
            oldestInProgressAt
        );
    }

    @Override
    public synchronized void enqueueMaterialsForSync(java.util.Collection<String> materialIds, Instant requestedAt) {
        enqueueMaterialsForSync(materialIds, SearchSyncOperationType.UPSERT, requestedAt);
    }

    @Override
    public synchronized void enqueueMaterialsForSync(
        java.util.Collection<String> materialIds,
        SearchSyncOperationType operationType,
        Instant requestedAt
    ) {
        if (materialIds == null || materialIds.isEmpty()) {
            return;
        }

        Instant effectiveRequestedAt = requestedAt == null ? Instant.now() : requestedAt;
        SearchSyncOperationType effectiveOperationType = operationType == null
            ? SearchSyncOperationType.UPSERT
            : operationType;
        for (String materialId : materialIds) {
            if (materialId == null || materialId.isBlank()) {
                continue;
            }
            MaterialSearchSyncQueueEntry current = searchSyncQueueByMaterialId.get(materialId);
            if (current == null) {
                searchSyncIntentVersionByMaterialId.put(materialId, 1L);
                claimedSearchSyncIntentVersionByMaterialId.remove(materialId);
                searchSyncQueueByMaterialId.put(materialId, new MaterialSearchSyncQueueEntry(
                    materialId,
                    effectiveOperationType,
                    SearchSyncDeliveryState.PENDING,
                    0,
                    null,
                    null,
                    null,
                    null,
                    effectiveRequestedAt,
                    effectiveRequestedAt,
                    effectiveRequestedAt
                ));
                continue;
            }

            Instant nextRequestedAt = current.requestedAt().isAfter(effectiveRequestedAt)
                ? current.requestedAt()
                : effectiveRequestedAt;
            searchSyncIntentVersionByMaterialId.put(
                materialId,
                searchSyncIntentVersionByMaterialId.getOrDefault(materialId, 1L) + 1
            );
            if (current.deliveryState() == SearchSyncDeliveryState.IN_PROGRESS) {
                searchSyncQueueByMaterialId.put(materialId, new MaterialSearchSyncQueueEntry(
                    current.materialId(),
                    effectiveOperationType,
                    current.deliveryState(),
                    current.attemptCount(),
                    current.nextAttemptAt(),
                    current.claimedAt(),
                    current.lastErrorCode(),
                    current.lastErrorMessage(),
                    nextRequestedAt,
                    current.createdAt(),
                    effectiveRequestedAt
                ));
                continue;
            }

            claimedSearchSyncIntentVersionByMaterialId.remove(materialId);
            searchSyncQueueByMaterialId.put(materialId, new MaterialSearchSyncQueueEntry(
                current.materialId(),
                effectiveOperationType,
                SearchSyncDeliveryState.PENDING,
                0,
                null,
                null,
                null,
                null,
                nextRequestedAt,
                current.createdAt(),
                effectiveRequestedAt
            ));
        }
    }

    @Override
    public synchronized List<MaterialSearchSyncQueueEntry> findAllSearchSyncEntries() {
        return searchSyncQueueByMaterialId.values().stream()
            .sorted(Comparator
                .comparing(MaterialSearchSyncQueueEntry::requestedAt)
                .thenComparing(MaterialSearchSyncQueueEntry::materialId))
            .toList();
    }

    public synchronized void clearSearchSyncQueue() {
        searchSyncQueueByMaterialId.clear();
        searchSyncIntentVersionByMaterialId.clear();
        claimedSearchSyncIntentVersionByMaterialId.clear();
    }

    @Override
    public synchronized int requeueFailedSearchSyncEntries(Instant now) {
        int requeued = 0;
        for (MaterialSearchSyncQueueEntry entry : new ArrayList<>(searchSyncQueueByMaterialId.values())) {
            if (entry.deliveryState() != SearchSyncDeliveryState.FAILED) {
                continue;
            }
            claimedSearchSyncIntentVersionByMaterialId.remove(entry.materialId());
            searchSyncQueueByMaterialId.put(entry.materialId(), new MaterialSearchSyncQueueEntry(
                entry.materialId(),
                entry.operationType(),
                SearchSyncDeliveryState.PENDING,
                0,
                null,
                null,
                null,
                null,
                entry.requestedAt(),
                entry.createdAt(),
                now
            ));
            requeued += 1;
        }
        return requeued;
    }

    @Override
    public synchronized void resetExpiredSearchSyncClaims(Instant staleBefore, Instant now) {
        for (MaterialSearchSyncQueueEntry entry : new ArrayList<>(searchSyncQueueByMaterialId.values())) {
            if (entry.deliveryState() != SearchSyncDeliveryState.IN_PROGRESS) {
                continue;
            }
            if (entry.claimedAt() == null || !entry.claimedAt().isBefore(staleBefore)) {
                continue;
            }
            boolean newerIntent = hasNewerSearchSyncIntent(entry.materialId());
            claimedSearchSyncIntentVersionByMaterialId.remove(entry.materialId());
            searchSyncQueueByMaterialId.put(entry.materialId(), new MaterialSearchSyncQueueEntry(
                entry.materialId(),
                entry.operationType(),
                SearchSyncDeliveryState.PENDING,
                newerIntent ? 0 : entry.attemptCount(),
                null,
                null,
                newerIntent ? null : entry.lastErrorCode(),
                newerIntent ? null : entry.lastErrorMessage(),
                entry.requestedAt(),
                entry.createdAt(),
                now
            ));
        }
    }

    @Override
    public synchronized List<MaterialSearchSyncQueueEntry> claimNextSearchSyncBatch(Instant now, int limit) {
        if (limit <= 0) {
            return List.of();
        }

        List<MaterialSearchSyncQueueEntry> claimed = searchSyncQueueByMaterialId.values().stream()
            .filter(entry -> entry.deliveryState() == SearchSyncDeliveryState.PENDING)
            .filter(entry -> entry.nextAttemptAt() == null || !entry.nextAttemptAt().isAfter(now))
            .sorted(Comparator
                .comparing((MaterialSearchSyncQueueEntry entry) ->
                    entry.nextAttemptAt() == null ? entry.requestedAt() : entry.nextAttemptAt())
                .thenComparing(MaterialSearchSyncQueueEntry::requestedAt)
                .thenComparing(MaterialSearchSyncQueueEntry::materialId))
            .limit(limit)
            .toList();

        for (MaterialSearchSyncQueueEntry entry : claimed) {
            claimedSearchSyncIntentVersionByMaterialId.put(
                entry.materialId(),
                searchSyncIntentVersionByMaterialId.getOrDefault(entry.materialId(), 1L)
            );
            searchSyncQueueByMaterialId.put(entry.materialId(), new MaterialSearchSyncQueueEntry(
                entry.materialId(),
                entry.operationType(),
                SearchSyncDeliveryState.IN_PROGRESS,
                entry.attemptCount() + 1,
                entry.nextAttemptAt(),
                now,
                entry.lastErrorCode(),
                entry.lastErrorMessage(),
                entry.requestedAt(),
                entry.createdAt(),
                now
            ));
        }

        return claimed.stream()
            .map(entry -> searchSyncQueueByMaterialId.get(entry.materialId()))
            .toList();
    }

    @Override
    public synchronized boolean hasPendingSearchSyncEvents(Instant now) {
        return searchSyncQueueByMaterialId.values().stream()
            .filter(entry -> entry.deliveryState() == SearchSyncDeliveryState.PENDING)
            .anyMatch(entry -> entry.nextAttemptAt() == null || !entry.nextAttemptAt().isAfter(now));
    }

    @Override
    public synchronized void completeSearchSyncEntry(String materialId, Instant claimedAt, Instant now) {
        MaterialSearchSyncQueueEntry current = searchSyncQueueByMaterialId.get(materialId);
        if (current == null
            || current.deliveryState() != SearchSyncDeliveryState.IN_PROGRESS
            || current.claimedAt() == null
            || !current.claimedAt().equals(claimedAt)) {
            return;
        }

        if (hasNewerSearchSyncIntent(materialId)) {
            claimedSearchSyncIntentVersionByMaterialId.remove(materialId);
            searchSyncQueueByMaterialId.put(materialId, new MaterialSearchSyncQueueEntry(
                current.materialId(),
                current.operationType(),
                SearchSyncDeliveryState.PENDING,
                0,
                null,
                null,
                null,
                null,
                current.requestedAt(),
                current.createdAt(),
                now
            ));
            return;
        }

        searchSyncQueueByMaterialId.remove(materialId);
        searchSyncIntentVersionByMaterialId.remove(materialId);
        claimedSearchSyncIntentVersionByMaterialId.remove(materialId);
    }

    @Override
    public synchronized void markSearchSyncEntryForRetry(
        String materialId,
        Instant claimedAt,
        String errorCode,
        String errorMessage,
        Instant now,
        Instant nextAttemptAt
    ) {
        updateClaimedSearchSyncQueueEntry(
            materialId,
            claimedAt,
            SearchSyncDeliveryState.PENDING,
            nextAttemptAt,
            now,
            errorCode,
            errorMessage
        );
    }

    @Override
    public synchronized void markSearchSyncEntryFailed(
        String materialId,
        Instant claimedAt,
        String errorCode,
        String errorMessage,
        Instant now
    ) {
        updateClaimedSearchSyncQueueEntry(
            materialId,
            claimedAt,
            SearchSyncDeliveryState.FAILED,
            null,
            now,
            errorCode,
            errorMessage
        );
    }

    @Override
    public synchronized MaterialSearchSyncQueueRepository.SearchSyncQueueSnapshot getSearchSyncQueueSnapshot() {
        int pendingCount = 0;
        int inProgressCount = 0;
        int failedCount = 0;
        Instant nextRetryAt = null;
        Instant oldestOutstandingAt = null;

        for (MaterialSearchSyncQueueEntry entry : searchSyncQueueByMaterialId.values()) {
            if (entry.deliveryState() == SearchSyncDeliveryState.PENDING) {
                pendingCount += 1;
                if (entry.nextAttemptAt() != null && (nextRetryAt == null || entry.nextAttemptAt().isBefore(nextRetryAt))) {
                    nextRetryAt = entry.nextAttemptAt();
                }
                if (oldestOutstandingAt == null || entry.requestedAt().isBefore(oldestOutstandingAt)) {
                    oldestOutstandingAt = entry.requestedAt();
                }
            } else if (entry.deliveryState() == SearchSyncDeliveryState.IN_PROGRESS) {
                inProgressCount += 1;
                if (oldestOutstandingAt == null || entry.requestedAt().isBefore(oldestOutstandingAt)) {
                    oldestOutstandingAt = entry.requestedAt();
                }
            } else if (entry.deliveryState() == SearchSyncDeliveryState.FAILED) {
                failedCount += 1;
            }
        }

        return new MaterialSearchSyncQueueRepository.SearchSyncQueueSnapshot(
            pendingCount,
            inProgressCount,
            failedCount,
            nextRetryAt,
            oldestOutstandingAt
        );
    }

    @Override
    public synchronized SearchableMaterialSnapshot resolveSearchableSnapshot(String materialId) {
        StoredMaterialRecord record = recordsById.get(materialId);
        if (record == null || !isSearchable(record)) {
            return SearchableMaterialSnapshot.notSearchable(materialId);
        }

        List<SearchableMaterialChunkSnapshot> chunks = embeddedChunksByMaterialId.getOrDefault(materialId, List.of()).stream()
            .sorted(Comparator.comparingInt(StoredEmbeddedMaterialChunk::index))
            .map(chunk -> new SearchableMaterialChunkSnapshot(
                chunk.index(),
                chunk.text(),
                chunk.page(),
                chunk.extractor(),
                chunk.ocrUsed(),
                chunk.chunkType(),
                chunk.sectionPath(),
                chunk.headingTrail(),
                chunk.tableId(),
                chunk.slideId(),
                chunk.parserConfidence()
            ))
            .toList();
        return new SearchableMaterialSnapshot(
            record.id(),
            true,
            record.sourceKey(),
            record.title(),
            record.sourceType(),
            record.originalFileName(),
            record.mediaType(),
            record.createdAt(),
            record.updatedAt(),
            record.metadata(),
            chunks
        );
    }

    @Override
    public synchronized List<String> findAllSearchableMaterialIds() {
        return recordsById.values().stream()
            .filter(this::isSearchable)
            .sorted(Comparator.comparing(StoredMaterialRecord::updatedAt).thenComparing(StoredMaterialRecord::id))
            .map(StoredMaterialRecord::id)
            .toList();
    }

    private List<StoredMaterialRecord> readyRecords() {
        return new ArrayList<>(recordsById.values().stream()
            .filter(this::isSearchable)
            .toList());
    }

    private boolean isAfterCursor(StoredMaterialRecord record, Instant createdAt, String id) {
        return record.createdAt().isAfter(createdAt)
            || (record.createdAt().equals(createdAt) && record.id().compareTo(id) > 0);
    }

    private MaterialSummary toSummary(StoredMaterialRecord record) {
        String content = record.content() == null ? "" : record.content();
        String preview = content.length() <= 180 ? content : content.substring(0, 180) + "...";
        return new MaterialSummary(
            record.id(),
            record.title(),
            record.sourceType(),
            record.originalFileName(),
            record.status(),
            record.versionState(),
            record.statusReasonCode(),
            record.statusReasonMessage(),
            record.createdAt(),
            record.updatedAt(),
            record.indexingAttempts(),
            record.nextRetryAt(),
            content.length(),
            preview,
            record.metadata()
        );
    }

    private boolean isSearchable(StoredMaterialRecord record) {
        LocalDate today = LocalDate.now();
        MaterialMetadataSnapshot metadata = record.metadata() == null ? MaterialMetadataSnapshot.empty() : record.metadata();
        return record.versionState() == MaterialVersionState.ACTIVE
            && (record.status() == MaterialIndexingStatus.READY || record.status() == MaterialIndexingStatus.PARTIAL_READY)
            && metadata.documentStatus() == DocumentStatus.ACTIVE
            && (metadata.periodStart() == null || !metadata.periodStart().isAfter(today))
            && (metadata.periodEnd() == null || !metadata.periodEnd().isBefore(today));
    }

    private boolean matchesKnowledgeScope(
        StoredMaterialRecord record,
        KnowledgeScope scope,
        Instant uploadedAfterInclusive,
        Instant uploadedBeforeExclusive
    ) {
        if (scope == null) {
            return true;
        }
        if (!scope.documentClasses().isEmpty() && !scope.documentClasses().contains(record.metadata().knowledgeDocumentClass())) {
            return false;
        }
        if (!scope.documentTypes().isEmpty() && !scope.documentTypes().contains(record.metadata().documentType())) {
            return false;
        }
        if (!scope.documentStatuses().isEmpty() && !scope.documentStatuses().contains(record.metadata().documentStatus())) {
            return false;
        }
        if (!scope.projectKeys().isEmpty()) {
            String recordProjectKey = record.metadata().projectKey();
            Set<String> scopeProjectKeys = scope.projectKeys().stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            if (recordProjectKey == null || !scopeProjectKeys.contains(recordProjectKey.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        if (scope.documentNumber() != null) {
            String recordDocumentNumber = record.metadata().documentNumber();
            if (recordDocumentNumber == null || !scope.documentNumber().equalsIgnoreCase(recordDocumentNumber)) {
                return false;
            }
        }
        if (!scope.languageCodes().isEmpty() && !scope.languageCodes().contains(record.metadata().languageCode())) {
            return false;
        }
        if (!scope.tags().isEmpty()) {
            Set<String> recordTags = record.metadata().tags().stream()
                .map(tag -> tag.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            boolean matchesAnyTag = scope.tags().stream()
                .map(tag -> tag.toLowerCase(Locale.ROOT))
                .anyMatch(recordTags::contains);
            if (!matchesAnyTag) {
                return false;
            }
        }
        if (scope.workspaceKey() != null) {
            String workspaceKey = record.metadata().workspaceKey();
            if (workspaceKey == null || !scope.workspaceKey().equalsIgnoreCase(workspaceKey)) {
                return false;
            }
        }
        if (scope.uploadedTodayOnly()) {
            if (uploadedAfterInclusive != null && record.createdAt().isBefore(uploadedAfterInclusive)) {
                return false;
            }
            if (uploadedBeforeExclusive != null && !record.createdAt().isBefore(uploadedBeforeExclusive)) {
                return false;
            }
        }
        if (scope.periodStartFrom() != null
            && (record.metadata().periodStart() == null || record.metadata().periodStart().isBefore(scope.periodStartFrom()))) {
            return false;
        }
        if (scope.periodStartTo() != null
            && (record.metadata().periodStart() == null || record.metadata().periodStart().isAfter(scope.periodStartTo()))) {
            return false;
        }
        if (scope.periodEndFrom() != null
            && (record.metadata().periodEnd() == null || record.metadata().periodEnd().isBefore(scope.periodEndFrom()))) {
            return false;
        }
        if (scope.periodEndTo() != null
            && (record.metadata().periodEnd() == null || record.metadata().periodEnd().isAfter(scope.periodEndTo()))) {
            return false;
        }
        return true;
    }

    private boolean matchesRetrievalFilters(StoredMaterialRecord record, RetrievalFilters filters) {
        RetrievalFilters safeFilters = filters == null ? RetrievalFilters.empty() : filters;
        return safeFilters.isEmpty() || safeFilters.matches(record.metadata());
    }

    private boolean matchesMaterialSearchScope(StoredMaterialRecord record, MaterialSearchScope scope) {
        if (scope.isUnscoped()) {
            return true;
        }
        if (scope.isNoResults()) {
            return false;
        }
        if (scope.isMaterialIds() && !scope.materialIds().contains(record.id())) {
            return false;
        }
        if (scope.isFiltered()
            && !matchesKnowledgeScope(
                record,
                scope.knowledgeScope(),
                scope.uploadedAfterInclusive(),
                scope.uploadedBeforeExclusive()
            )) {
            return false;
        }
        return matchesRetrievalFilters(record, scope.retrievalFilters());
    }

    private boolean hasMeaningfulMetadata(StoredMaterialRecord record) {
        return hasText(record.metadata().workspaceKey())
            && record.metadata().documentType() != null
            && record.metadata().documentType() != com.example.demo.model.DocumentType.OTHER
            && record.metadata().documentStatus() != null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private int lexicalScore(String title, String chunkText, String normalizedPrompt, Set<String> queryTokens) {
        int score = 0;
        String normalizedTitle = normalize(title);
        String normalizedChunk = normalize(chunkText);
        for (String token : queryTokens) {
            score += countOccurrences(normalizedChunk, token) * 2;
            score += countOccurrences(normalizedTitle, token) * 4;
        }
        if (normalizedChunk.contains(normalizedPrompt)) {
            score += 10;
        }
        if (normalizedTitle.contains(normalizedPrompt)) {
            score += 6;
        }
        return score;
    }

    private Set<String> tokenize(String input) {
        String normalized = NON_ALPHANUMERIC.matcher(input.toLowerCase(Locale.ROOT)).replaceAll(" ");
        Set<String> tokens = new LinkedHashSet<>();
        for (String token : normalized.split("\\s+")) {
            if (token.length() >= 2) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private String normalize(String input) {
        return input == null ? "" : input.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private int countOccurrences(String text, String token) {
        if (text.isEmpty() || token.isEmpty()) {
            return 0;
        }
        int count = 0;
        int from = 0;
        while ((from = text.indexOf(token, from)) >= 0) {
            count++;
            from += token.length();
        }
        return count;
    }

    private void updateClaimedSearchSyncQueueEntry(
        String materialId,
        Instant claimedAt,
        SearchSyncDeliveryState deliveryState,
        Instant nextAttemptAt,
        Instant now,
        String errorCode,
        String errorMessage
    ) {
        if (materialId == null || materialId.isBlank() || claimedAt == null) {
            return;
        }

        MaterialSearchSyncQueueEntry current = searchSyncQueueByMaterialId.get(materialId);
        if (current == null
            || current.deliveryState() != SearchSyncDeliveryState.IN_PROGRESS
            || current.claimedAt() == null
            || !current.claimedAt().equals(claimedAt)) {
            return;
        }

        boolean newerIntent = hasNewerSearchSyncIntent(materialId);
        claimedSearchSyncIntentVersionByMaterialId.remove(materialId);
        searchSyncQueueByMaterialId.put(materialId, new MaterialSearchSyncQueueEntry(
            current.materialId(),
            current.operationType(),
            newerIntent ? SearchSyncDeliveryState.PENDING : deliveryState,
            newerIntent ? 0 : current.attemptCount(),
            newerIntent ? null : nextAttemptAt,
            null,
            newerIntent ? null : errorCode,
            newerIntent ? null : errorMessage,
            current.requestedAt(),
            current.createdAt(),
            now
        ));
    }

    private boolean hasNewerSearchSyncIntent(String materialId) {
        long intentVersion = searchSyncIntentVersionByMaterialId.getOrDefault(materialId, 1L);
        long claimedIntentVersion = claimedSearchSyncIntentVersionByMaterialId.getOrDefault(materialId, intentVersion);
        return intentVersion > claimedIntentVersion;
    }

    private double cosineDistance(float[] left, float[] right) {
        if (left.length != right.length) {
            return 1.0d;
        }
        double dot = 0.0d;
        double leftMagnitude = 0.0d;
        double rightMagnitude = 0.0d;
        for (int index = 0; index < left.length; index++) {
            dot += left[index] * right[index];
            leftMagnitude += left[index] * left[index];
            rightMagnitude += right[index] * right[index];
        }
        if (leftMagnitude == 0.0d || rightMagnitude == 0.0d) {
            return 1.0d;
        }
        double similarity = dot / (Math.sqrt(leftMagnitude) * Math.sqrt(rightMagnitude));
        return 1.0d - similarity;
    }

    private StoredMaterialRecord copyWithStatus(
        StoredMaterialRecord record,
        MaterialIndexingStatus status,
        String statusReasonCode,
        String statusReasonMessage,
        Instant updatedAt
    ) {
        return new StoredMaterialRecord(
            record.id(),
            record.title(),
            record.sourceType(),
            record.originalFileName(),
            record.mediaType(),
            record.content(),
            record.normalizedContent(),
            record.contentHash(),
            record.sourceKey(),
            record.extractor(),
            record.ocrUsed(),
            record.pageCount(),
            record.chunks(),
            status,
            record.versionState(),
            statusReasonCode,
            statusReasonMessage,
            record.createdAt(),
            updatedAt,
            attemptsByMaterialId.getOrDefault(record.id(), 0),
            nextRetryAtByMaterialId.get(record.id()),
            record.supersededByMaterialId(),
            record.supersedeReason(),
            record.lineageVersion(),
            record.metadata()
        );
    }

    private StoredMaterialRecord copyWithVersionState(
        StoredMaterialRecord record,
        MaterialVersionState versionState,
        String supersededByMaterialId,
        String supersedeReason,
        Instant updatedAt
    ) {
        return new StoredMaterialRecord(
            record.id(),
            record.title(),
            record.sourceType(),
            record.originalFileName(),
            record.mediaType(),
            record.content(),
            record.normalizedContent(),
            record.contentHash(),
            record.sourceKey(),
            record.extractor(),
            record.ocrUsed(),
            record.pageCount(),
            record.chunks(),
            record.status(),
            versionState,
            record.statusReasonCode(),
            record.statusReasonMessage(),
            record.createdAt(),
            updatedAt,
            attemptsByMaterialId.getOrDefault(record.id(), 0),
            nextRetryAtByMaterialId.get(record.id()),
            supersededByMaterialId,
            supersedeReason,
            record.lineageVersion(),
            record.metadata()
        );
    }

    private StoredMaterialRecord copyWithMetadata(
        StoredMaterialRecord record,
        MaterialMetadataSnapshot metadata,
        Instant updatedAt
    ) {
        return new StoredMaterialRecord(
            record.id(),
            record.title(),
            record.sourceType(),
            record.originalFileName(),
            record.mediaType(),
            record.content(),
            record.normalizedContent(),
            record.contentHash(),
            record.sourceKey(),
            record.extractor(),
            record.ocrUsed(),
            record.pageCount(),
            record.chunks(),
            record.status(),
            record.versionState(),
            record.statusReasonCode(),
            record.statusReasonMessage(),
            record.createdAt(),
            updatedAt,
            attemptsByMaterialId.getOrDefault(record.id(), 0),
            nextRetryAtByMaterialId.get(record.id()),
            record.supersededByMaterialId(),
            record.supersedeReason(),
            record.lineageVersion(),
            metadata == null ? MaterialMetadataSnapshot.empty() : metadata
        );
    }

    private String sourceKeyAndContentHashKey(String sourceKey, String contentHash) {
        return sourceKey + "|" + contentHash;
    }

    private String identityKey(MaterialLineageIdentity identity) {
        return identity.sourceType() + "|" + identity.identityKind().name() + "|" + identity.identityKey();
    }

    private int nextLineageVersion(String sourceKey) {
        return recordsById.values().stream()
            .filter(record -> record.sourceKey().equals(sourceKey))
            .mapToInt(StoredMaterialRecord::lineageVersion)
            .max()
            .orElse(0) + 1;
    }

    private record SemanticCandidate(
        StoredMaterialRecord record,
        StoredEmbeddedMaterialChunk chunk,
        double distance
    ) {
    }

    private record LexicalCandidate(
        StoredMaterialRecord record,
        StoredEmbeddedMaterialChunk chunk,
        int score
    ) {
    }
}
