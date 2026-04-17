package com.example.demo.support;

import com.example.demo.infrastructure.material.MaterialCatalogRepository;
import com.example.demo.infrastructure.material.MaterialChunkSearchMatch;
import com.example.demo.infrastructure.material.MaterialIndexingLease;
import com.example.demo.infrastructure.material.MaterialIndexingQueueRepository;
import com.example.demo.infrastructure.material.MaterialSearchRepository;
import com.example.demo.infrastructure.material.StoredEmbeddedMaterialChunk;
import com.example.demo.infrastructure.material.StoredMaterialChunk;
import com.example.demo.infrastructure.material.StoredMaterialRecord;
import com.example.demo.model.MaterialIndexingStatus;
import com.example.demo.model.MaterialVersionState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public class InMemoryMaterialRepository implements MaterialCatalogRepository, MaterialSearchRepository, MaterialIndexingQueueRepository {

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{N}]+");

    private final Map<String, StoredMaterialRecord> recordsById = new LinkedHashMap<>();
    private final Map<String, String> idsByContentHash = new LinkedHashMap<>();
    private final Map<String, List<StoredMaterialChunk>> rawChunksByMaterialId = new LinkedHashMap<>();
    private final Map<String, List<StoredEmbeddedMaterialChunk>> embeddedChunksByMaterialId = new LinkedHashMap<>();
    private final Map<String, Integer> attemptsByMaterialId = new LinkedHashMap<>();
    private final Map<String, Instant> nextRetryAtByMaterialId = new LinkedHashMap<>();
    private final Map<String, Instant> claimedAtByMaterialId = new LinkedHashMap<>();

    @Override
    public synchronized List<StoredMaterialRecord> findAll() {
        return recordsById.values().stream().toList();
    }

    @Override
    public synchronized Optional<StoredMaterialRecord> findById(String id) {
        return Optional.ofNullable(recordsById.get(id));
    }

    @Override
    public synchronized Optional<StoredMaterialRecord> findByContentHash(String contentHash) {
        String id = idsByContentHash.get(contentHash);
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(recordsById.get(id));
    }

    @Override
    public synchronized StoredMaterialRecord save(StoredMaterialRecord record, List<StoredMaterialChunk> chunks) {
        String existingId = idsByContentHash.get(record.contentHash());
        if (existingId != null) {
            return recordsById.get(existingId);
        }

        recordsById.put(record.id(), record);
        idsByContentHash.put(record.contentHash(), record.id());
        rawChunksByMaterialId.put(record.id(), chunks == null ? List.of() : List.copyOf(chunks));
        embeddedChunksByMaterialId.put(record.id(), List.of());
        attemptsByMaterialId.put(record.id(), 0);
        nextRetryAtByMaterialId.put(record.id(), record.updatedAt());
        claimedAtByMaterialId.put(record.id(), null);
        return record;
    }

    @Override
    public synchronized List<StoredMaterialChunk> findChunks(String materialId) {
        return rawChunksByMaterialId.getOrDefault(materialId, List.of());
    }

    @Override
    public synchronized List<StoredMaterialRecord> findAllBySourceKey(String sourceKey) {
        return recordsById.values().stream()
            .filter(record -> record.sourceKey().equals(sourceKey))
            .sorted(Comparator.comparing(StoredMaterialRecord::updatedAt).reversed())
            .toList();
    }

    @Override
    public synchronized void delete(String id) {
        StoredMaterialRecord removed = recordsById.remove(id);
        rawChunksByMaterialId.remove(id);
        embeddedChunksByMaterialId.remove(id);
        attemptsByMaterialId.remove(id);
        nextRetryAtByMaterialId.remove(id);
        claimedAtByMaterialId.remove(id);
        if (removed != null) {
            idsByContentHash.remove(removed.contentHash());
        }
    }

    @Override
    public synchronized int countMaterials() {
        return recordsById.size();
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
            .filter(record -> record.versionState() == MaterialVersionState.ACTIVE)
            .filter(record -> record.status() == MaterialIndexingStatus.READY || record.status() == MaterialIndexingStatus.PARTIAL_READY)
            .count();
    }

    @Override
    public synchronized void supersedeActiveVersions(
        String sourceKey,
        String activeMaterialId,
        String excludeContentHash,
        String supersedeReason,
        Instant updatedAt
    ) {
        for (Map.Entry<String, StoredMaterialRecord> entry : recordsById.entrySet()) {
            StoredMaterialRecord record = entry.getValue();
            if (!record.sourceKey().equals(sourceKey)) {
                continue;
            }
            if (record.versionState() != MaterialVersionState.ACTIVE) {
                continue;
            }
            if (record.contentHash().equals(excludeContentHash)) {
                continue;
            }
            entry.setValue(copyWithVersionState(
                record,
                MaterialVersionState.SUPERSEDED,
                activeMaterialId,
                supersedeReason,
                updatedAt
            ));
        }
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
                .comparing(StoredMaterialRecord::updatedAt)
                .thenComparing(StoredMaterialRecord::createdAt));
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
        if (queryEmbedding == null || queryEmbedding.length == 0 || limit <= 0) {
            return List.of();
        }

        return readyRecords().stream()
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
                candidate.distance(),
                null
            ))
            .toList();
    }

    @Override
    public synchronized List<MaterialChunkSearchMatch> searchLexical(String query, int limit) {
        if (query == null || query.isBlank() || limit <= 0) {
            return List.of();
        }

        Set<String> queryTokens = tokenize(query);
        String normalizedPrompt = normalize(query);

        return readyRecords().stream()
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
            } else if (record.status() == MaterialIndexingStatus.IN_PROGRESS) {
                inProgressCount += 1;
            } else if (record.status() == MaterialIndexingStatus.FAILED) {
                failedCount += 1;
            }
        }

        return new IndexingQueueSnapshot(pendingCount, inProgressCount, failedCount, nextRetryAt);
    }

    private List<StoredMaterialRecord> readyRecords() {
        return new ArrayList<>(recordsById.values().stream()
            .filter(record -> record.versionState() == MaterialVersionState.ACTIVE)
            .filter(record -> record.status() == MaterialIndexingStatus.READY || record.status() == MaterialIndexingStatus.PARTIAL_READY)
            .toList());
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
            record.supersedeReason()
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
            supersedeReason
        );
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
