package com.example.demo.service;

import com.example.demo.service.material.MaterialSearchSyncQueueEntry;
import com.example.demo.service.material.SearchSyncOperationType;
import com.example.demo.service.material.SearchableChunkDocument;
import com.example.demo.service.material.SearchableMaterialSnapshot;
import com.example.demo.service.material.port.MaterialSearchSyncQueueRepository;
import com.example.demo.service.material.port.MaterialSearchableSnapshotRepository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import com.example.demo.config.SearchSyncProperties;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.search-sync", name = "enabled", havingValue = "true")
public class ElasticsearchIndexSyncService {

    private static final Logger logger = LoggerFactory.getLogger(ElasticsearchIndexSyncService.class);

    private final MaterialSearchSyncQueueRepository queueRepository;
    private final MaterialSearchableSnapshotRepository searchableSnapshotRepository;
    private final ElasticsearchHealthService healthService;
    private final ElasticsearchClient elasticsearchClient;
    private final SearchSyncProperties searchSyncProperties;
    private final Executor searchSyncExecutor;
    private final AtomicBoolean drainScheduled = new AtomicBoolean(false);

    public ElasticsearchIndexSyncService(
        MaterialSearchSyncQueueRepository queueRepository,
        MaterialSearchableSnapshotRepository searchableSnapshotRepository,
        ElasticsearchHealthService healthService,
        ElasticsearchClient elasticsearchClient,
        SearchSyncProperties searchSyncProperties,
        @Qualifier("searchSyncExecutor") Executor searchSyncExecutor
    ) {
        this.queueRepository = queueRepository;
        this.searchableSnapshotRepository = searchableSnapshotRepository;
        this.healthService = healthService;
        this.elasticsearchClient = elasticsearchClient;
        this.searchSyncProperties = searchSyncProperties;
        this.searchSyncExecutor = searchSyncExecutor;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        requestProcessing();
    }

    public void requestProcessing() {
        if (!drainScheduled.compareAndSet(false, true)) {
            return;
        }

        try {
            searchSyncExecutor.execute(this::drainQueue);
        } catch (RejectedExecutionException exception) {
            drainScheduled.set(false);
            logger.warn("Search sync executor rejected queue processing request; leaving sync events pending", exception);
        }
    }

    long backoffSeconds(int attemptNumber) {
        long base = Math.max(1, searchSyncProperties.getRetryBaseSeconds());
        int exponent = Math.min(30, Math.max(0, attemptNumber - 1));
        long candidate = base * (1L << exponent);
        return Math.min(candidate, Math.max(base, searchSyncProperties.getRetryMaxSeconds()));
    }

    private void drainQueue() {
        try {
            Instant now = Instant.now();
            queueRepository.resetExpiredSearchSyncClaims(
                now.minusSeconds(searchSyncProperties.getClaimLeaseSeconds()),
                now
            );

            int processedBatches = 0;
            int maxBatches = Math.max(1, searchSyncProperties.getMaxDrainBatches());
            while (processedBatches < maxBatches) {
                List<MaterialSearchSyncQueueEntry> claimedEntries = queueRepository.claimNextSearchSyncBatch(
                    Instant.now(),
                    searchSyncProperties.getClaimBatchSize()
                );
                if (claimedEntries.isEmpty()) {
                    break;
                }
                processClaimedBatch(claimedEntries, Instant.now());
                processedBatches += 1;
            }
        } finally {
            drainScheduled.set(false);
            if (queueRepository.hasPendingSearchSyncEvents(Instant.now())) {
                requestProcessing();
            }
        }
    }

    private void processClaimedBatch(List<MaterialSearchSyncQueueEntry> claimedEntries, Instant startedAt) {
        MDC.put("batchSize", String.valueOf(claimedEntries.size()));
        int completedCount = 0;
        int retriedCount = 0;
        int failedCount = 0;
        Instant lastSuccessfulSyncAt = null;

        try {
            for (MaterialSearchSyncQueueEntry entry : claimedEntries) {
                MDC.put("materialId", entry.materialId());
                MDC.put("attempt", String.valueOf(entry.attemptCount()));
                try {
                    reconcileMaterial(entry);
                    Instant completedAt = Instant.now();
                    queueRepository.completeSearchSyncEntry(entry.materialId(), entry.claimedAt(), completedAt);
                    completedCount += 1;
                    lastSuccessfulSyncAt = completedAt;
                } catch (RuntimeException exception) {
                    if (handleFailure(entry, exception) == FailureDisposition.FAILED) {
                        failedCount += 1;
                    } else {
                        retriedCount += 1;
                    }
                } finally {
                    MDC.remove("materialId");
                    MDC.remove("attempt");
                }
            }

            if (lastSuccessfulSyncAt != null) {
                healthService.recordSuccessfulSync(lastSuccessfulSyncAt);
            }
            logger.info(
                "Elasticsearch sync batch completed: claimed={} completed={} retried={} failed={} startedAt={}",
                claimedEntries.size(),
                completedCount,
                retriedCount,
                failedCount,
                startedAt
            );
        } finally {
            MDC.remove("batchSize");
        }
    }

    private FailureDisposition handleFailure(
        MaterialSearchSyncQueueEntry entry,
        RuntimeException exception
    ) {
        int currentAttempt = Math.max(1, entry.attemptCount());
        Instant now = Instant.now();
        String errorCode = "search.sync_failed";
        String errorMessage = rootMessage(exception);

        if (currentAttempt >= searchSyncProperties.getMaxAttempts()) {
            queueRepository.markSearchSyncEntryFailed(
                entry.materialId(),
                entry.claimedAt(),
                errorCode,
                errorMessage,
                now
            );
            logger.warn(
                "Elasticsearch sync failed terminally: materialId={} attempts={} message={}",
                entry.materialId(),
                currentAttempt,
                errorMessage,
                exception
            );
            return FailureDisposition.FAILED;
        }

        Instant nextAttemptAt = now.plusSeconds(backoffSeconds(currentAttempt));
        queueRepository.markSearchSyncEntryForRetry(
            entry.materialId(),
            entry.claimedAt(),
            errorCode,
            errorMessage,
            now,
            nextAttemptAt
        );
        logger.warn(
            "Elasticsearch sync failed and will retry: materialId={} attempts={} nextAttemptAt={} message={}",
            entry.materialId(),
            currentAttempt,
            nextAttemptAt,
            errorMessage,
            exception
        );
        return FailureDisposition.RETRY;
    }

    private void reconcileMaterial(MaterialSearchSyncQueueEntry entry) {
        String materialId = entry.materialId();
        if (entry.operationType() == SearchSyncOperationType.DELETE) {
            deleteExistingDocuments(materialId);
            return;
        }

        SearchableMaterialSnapshot snapshot = searchableSnapshotRepository.resolveSearchableSnapshot(materialId);
        if (!snapshot.searchable()) {
            deleteExistingDocuments(materialId);
            return;
        }

        List<SearchableChunkDocument> documents = SearchableChunkDocument.fromSnapshot(snapshot);
        if (documents.isEmpty()) {
            throw new IllegalStateException("Material has no searchable chunk documents; preserving existing Elasticsearch documents");
        }

        indexDocuments(documents);
        deleteStaleDocuments(materialId, documents.stream().map(SearchableChunkDocument::documentId).toList());
    }

    private void indexDocuments(List<SearchableChunkDocument> documents) {
        int maxBulkActions = Math.max(1, searchSyncProperties.getMaxBulkActions());
        for (int start = 0; start < documents.size(); start += maxBulkActions) {
            int end = Math.min(start + maxBulkActions, documents.size());
            indexDocumentBatch(documents.subList(start, end));
        }
    }

    private void indexDocumentBatch(List<SearchableChunkDocument> documents) {
        BulkResponse response;
        try {
            BulkRequest.Builder bulkRequest = new BulkRequest.Builder();
            for (SearchableChunkDocument document : documents) {
                bulkRequest.operations(operation -> operation.index(index -> index
                    .index(searchSyncProperties.writeAlias())
                    .id(document.documentId())
                    .document(document)
                ));
            }
            response = elasticsearchClient.bulk(bulkRequest.build());
        } catch (IOException exception) {
            throw new IllegalStateException("Elasticsearch bulk index failed", exception);
        }
        assertBulkSuccess(response, "index searchable chunks");
    }

    private void deleteExistingDocuments(String materialId) {
        try {
            elasticsearchClient.deleteByQuery(DeleteByQueryRequest.of(delete -> delete
                .index(searchSyncProperties.writeAlias())
                .query(query -> query.term(term -> term.field("materialId").value(materialId)))
            ));
        } catch (IOException exception) {
            throw new IllegalStateException("Elasticsearch delete-by-query failed", exception);
        }
    }

    private void deleteStaleDocuments(String materialId, List<String> retainedDocumentIds) {
        if (retainedDocumentIds == null || retainedDocumentIds.isEmpty()) {
            deleteExistingDocuments(materialId);
            return;
        }

        try {
            elasticsearchClient.deleteByQuery(DeleteByQueryRequest.of(delete -> delete
                .index(searchSyncProperties.writeAlias())
                .query(query -> query.bool(bool -> bool
                    .filter(filter -> filter.term(term -> term.field("materialId").value(materialId)))
                    .mustNot(mustNot -> mustNot.terms(terms -> terms
                        .field("_id")
                        .terms(values -> values.value(retainedDocumentIds.stream().map(FieldValue::of).toList()))
                    ))
                ))
            ));
        } catch (IOException exception) {
            throw new IllegalStateException("Elasticsearch stale document cleanup failed", exception);
        }
    }

    private void assertBulkSuccess(BulkResponse response, String operation) {
        if (!response.errors()) {
            return;
        }

        List<String> reasons = new ArrayList<>();
        response.items().forEach(item -> {
            if (item.error() != null && item.error().reason() != null) {
                reasons.add(item.error().reason());
            }
        });
        throw new IllegalStateException(
            "Elasticsearch bulk " + operation + " failed: " + String.join("; ", reasons)
        );
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private enum FailureDisposition {
        RETRY,
        FAILED
    }
}
