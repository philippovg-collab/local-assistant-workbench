package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.example.demo.config.SearchSyncProperties;
import com.example.demo.infrastructure.material.MaterialSearchSyncQueueEntry;
import com.example.demo.infrastructure.material.MaterialSearchSyncQueueRepository;
import com.example.demo.infrastructure.material.MaterialSearchableSnapshotRepository;
import com.example.demo.infrastructure.material.SearchSyncDeliveryState;
import com.example.demo.infrastructure.material.SearchableMaterialSnapshot;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ElasticsearchIndexSyncServiceTest {

    @Test
    void backoffCapsAtConfiguredMaximum() {
        ElasticsearchIndexSyncService service = createService(
            properties(3, 5, 20),
            org.mockito.Mockito.mock(MaterialSearchSyncQueueRepository.class),
            org.mockito.Mockito.mock(MaterialSearchableSnapshotRepository.class),
            org.mockito.Mockito.mock(ElasticsearchHealthService.class),
            org.mockito.Mockito.mock(ElasticsearchClient.class)
        );

        assertEquals(5, service.backoffSeconds(1));
        assertEquals(10, service.backoffSeconds(2));
        assertEquals(20, service.backoffSeconds(3));
        assertEquals(20, service.backoffSeconds(4));
    }

    @Test
    void retriesOnlyPoisonMaterialWithoutFailingSiblingEntries() {
        MaterialSearchSyncQueueRepository queueRepository = org.mockito.Mockito.mock(MaterialSearchSyncQueueRepository.class);
        MaterialSearchableSnapshotRepository snapshotRepository = org.mockito.Mockito.mock(MaterialSearchableSnapshotRepository.class);
        ElasticsearchHealthService healthService = org.mockito.Mockito.mock(ElasticsearchHealthService.class);
        ElasticsearchClient elasticsearchClient = org.mockito.Mockito.mock(ElasticsearchClient.class);
        MaterialSearchSyncQueueEntry poison = entry("material-a", 1, Instant.parse("2026-04-17T10:00:01Z"));
        MaterialSearchSyncQueueEntry healthy = entry("material-b", 1, Instant.parse("2026-04-17T10:00:02Z"));

        when(queueRepository.claimNextSearchSyncBatch(any(), anyInt()))
            .thenReturn(List.of(poison, healthy))
            .thenReturn(List.of());
        when(queueRepository.hasPendingSearchSyncEvents(any())).thenReturn(false);
        when(snapshotRepository.resolveSearchableSnapshot("material-a"))
            .thenThrow(new IllegalStateException("cluster unavailable"));
        when(snapshotRepository.resolveSearchableSnapshot("material-b"))
            .thenReturn(SearchableMaterialSnapshot.notSearchable("material-b"));

        ElasticsearchIndexSyncService service = createService(
            properties(3, 5, 60),
            queueRepository,
            snapshotRepository,
            healthService,
            elasticsearchClient
        );

        service.requestProcessing();

        verify(queueRepository).markSearchSyncEntryForRetry(
            eq(poison.materialId()),
            eq(poison.claimedAt()),
            eq("search.sync_failed"),
            argThat((String message) -> message.contains("cluster unavailable")),
            any(),
            any()
        );
        verify(queueRepository).completeSearchSyncEntry(eq(healthy.materialId()), eq(healthy.claimedAt()), any());
        verify(queueRepository, never()).markSearchSyncEntryFailed(eq(healthy.materialId()), any(), any(), any(), any());
        verify(healthService, never()).recordSyncFailure(any(), any(), any());
    }

    @Test
    void marksOnlyExhaustedMaterialFailedAndStillDeliversHealthySibling() {
        MaterialSearchSyncQueueRepository queueRepository = org.mockito.Mockito.mock(MaterialSearchSyncQueueRepository.class);
        MaterialSearchableSnapshotRepository snapshotRepository = org.mockito.Mockito.mock(MaterialSearchableSnapshotRepository.class);
        ElasticsearchHealthService healthService = org.mockito.Mockito.mock(ElasticsearchHealthService.class);
        ElasticsearchClient elasticsearchClient = org.mockito.Mockito.mock(ElasticsearchClient.class);
        MaterialSearchSyncQueueEntry exhausted = entry("material-a", 3, Instant.parse("2026-04-17T10:00:03Z"));
        MaterialSearchSyncQueueEntry healthy = entry("material-b", 1, Instant.parse("2026-04-17T10:00:04Z"));

        when(queueRepository.claimNextSearchSyncBatch(any(), anyInt()))
            .thenReturn(List.of(exhausted, healthy))
            .thenReturn(List.of());
        when(queueRepository.hasPendingSearchSyncEvents(any())).thenReturn(false);
        when(snapshotRepository.resolveSearchableSnapshot("material-a"))
            .thenThrow(new IllegalStateException("mapping rejected"));
        when(snapshotRepository.resolveSearchableSnapshot("material-b"))
            .thenReturn(SearchableMaterialSnapshot.notSearchable("material-b"));

        ElasticsearchIndexSyncService service = createService(
            properties(3, 5, 60),
            queueRepository,
            snapshotRepository,
            healthService,
            elasticsearchClient
        );

        service.requestProcessing();

        verify(queueRepository).markSearchSyncEntryFailed(
            eq(exhausted.materialId()),
            eq(exhausted.claimedAt()),
            eq("search.sync_failed"),
            argThat((String message) -> message.contains("mapping rejected")),
            any()
        );
        verify(queueRepository).completeSearchSyncEntry(eq(healthy.materialId()), eq(healthy.claimedAt()), any());
        verify(queueRepository, never()).markSearchSyncEntryFailed(eq(healthy.materialId()), any(), any(), any(), any());
        verify(queueRepository, never()).markSearchSyncEntryForRetry(eq(healthy.materialId()), any(), any(), any(), any(), any());
        verify(healthService, never()).recordSyncFailure(any(), any(), any());
    }

    private ElasticsearchIndexSyncService createService(
        SearchSyncProperties properties,
        MaterialSearchSyncQueueRepository queueRepository,
        MaterialSearchableSnapshotRepository snapshotRepository,
        ElasticsearchHealthService healthService,
        ElasticsearchClient elasticsearchClient
    ) {
        return new ElasticsearchIndexSyncService(
            queueRepository,
            snapshotRepository,
            healthService,
            elasticsearchClient,
            properties,
            Runnable::run
        );
    }

    private SearchSyncProperties properties(int maxAttempts, int retryBaseSeconds, int retryMaxSeconds) {
        SearchSyncProperties properties = new SearchSyncProperties();
        properties.setEnabled(true);
        properties.setIndexPrefix("test-rag-chunks");
        properties.setIndexVersion("v1");
        properties.setClaimBatchSize(8);
        properties.setClaimLeaseSeconds(30);
        properties.setMaxAttempts(maxAttempts);
        properties.setRetryBaseSeconds(retryBaseSeconds);
        properties.setRetryMaxSeconds(retryMaxSeconds);
        return properties;
    }

    private static MaterialSearchSyncQueueEntry entry(String materialId, int attemptCount, Instant claimedAt) {
        Instant createdAt = claimedAt.minusSeconds(10);
        return new MaterialSearchSyncQueueEntry(
            materialId,
            SearchSyncDeliveryState.IN_PROGRESS,
            attemptCount,
            null,
            claimedAt,
            null,
            null,
            createdAt,
            createdAt,
            createdAt
        );
    }
}
