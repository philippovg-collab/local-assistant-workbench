package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.infrastructure.material.MaterialSearchSyncQueueRepository;
import com.example.demo.infrastructure.material.MaterialSearchableSnapshotRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SearchSyncRecoveryServiceTest {

    @Test
    void enqueueReplayPersistsOneUpsertEventPerSearchableMaterial() {
        MaterialSearchSyncQueueRepository queueRepository = org.mockito.Mockito.mock(MaterialSearchSyncQueueRepository.class);
        MaterialSearchableSnapshotRepository searchableSnapshotRepository =
            org.mockito.Mockito.mock(MaterialSearchableSnapshotRepository.class);
        ElasticsearchIndexAdminService indexAdminService = org.mockito.Mockito.mock(ElasticsearchIndexAdminService.class);
        ElasticsearchIndexSyncService searchSyncService = org.mockito.Mockito.mock(ElasticsearchIndexSyncService.class);
        ElasticsearchHealthService healthService = org.mockito.Mockito.mock(ElasticsearchHealthService.class);
        MutableClock clock = new MutableClock(Instant.parse("2026-04-18T06:00:00Z"));

        when(searchableSnapshotRepository.findAllSearchableMaterialIds()).thenReturn(List.of("material-ready", "material-partial"));

        SearchSyncRecoveryService service = new SearchSyncRecoveryService(
            queueRepository,
            searchableSnapshotRepository,
            indexAdminService,
            searchSyncService,
            healthService,
            clock,
            duration -> {
            }
        );

        SearchSyncRecoveryService.ReplaySummary summary = service.enqueueReplayForAllSearchableActiveMaterials();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> materialIdsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(queueRepository).enqueueMaterialsForSync(materialIdsCaptor.capture(), org.mockito.ArgumentMatchers.eq(clock.instant()));
        assertEquals(2, summary.enqueuedCount());
        assertEquals(
            List.of("material-ready", "material-partial"),
            materialIdsCaptor.getValue().stream().toList()
        );
    }

    @Test
    void rebuildCurrentWriteIndexClearsCurrentTargetAndReplaysSearchableMaterials() {
        MaterialSearchSyncQueueRepository queueRepository = org.mockito.Mockito.mock(MaterialSearchSyncQueueRepository.class);
        MaterialSearchableSnapshotRepository searchableSnapshotRepository =
            org.mockito.Mockito.mock(MaterialSearchableSnapshotRepository.class);
        ElasticsearchIndexAdminService indexAdminService = org.mockito.Mockito.mock(ElasticsearchIndexAdminService.class);
        ElasticsearchIndexSyncService searchSyncService = org.mockito.Mockito.mock(ElasticsearchIndexSyncService.class);
        ElasticsearchHealthService healthService = org.mockito.Mockito.mock(ElasticsearchHealthService.class);
        MutableClock clock = new MutableClock(Instant.parse("2026-04-18T06:00:00Z"));

        when(indexAdminService.currentWriteIndex()).thenReturn("rag-chunks-v2");
        when(indexAdminService.clearCurrentWriteIndex()).thenReturn(9L);
        when(queueRepository.requeueFailedSearchSyncEntries(any())).thenReturn(3);
        when(searchableSnapshotRepository.findAllSearchableMaterialIds()).thenReturn(List.of("material-a", "material-b"));

        SearchSyncRecoveryService service = new SearchSyncRecoveryService(
            queueRepository,
            searchableSnapshotRepository,
            indexAdminService,
            searchSyncService,
            healthService,
            clock,
            duration -> {
            }
        );

        SearchSyncRecoveryService.RebuildSummary summary = service.rebuildCurrentWriteIndex();

        verify(indexAdminService, never()).prepareConfiguredWriteIndex();
        verify(indexAdminService).clearCurrentWriteIndex();
        verify(queueRepository).requeueFailedSearchSyncEntries(clock.instant());
        verify(queueRepository).enqueueMaterialsForSync(any(), org.mockito.ArgumentMatchers.eq(clock.instant()));
        verify(searchSyncService, never()).requestProcessing();
        assertEquals("rag-chunks-v2", summary.writeIndexName());
        assertEquals(9L, summary.clearedDocumentCount());
        assertEquals(3, summary.requeuedFailedCount());
        assertEquals(2, summary.replayEnqueuedCount());
    }

    @Test
    void recoverFailedEventsAndWaitTriggersSyncAndReturnsSummary() {
        MaterialSearchSyncQueueRepository queueRepository = org.mockito.Mockito.mock(MaterialSearchSyncQueueRepository.class);
        MaterialSearchableSnapshotRepository searchableSnapshotRepository =
            org.mockito.Mockito.mock(MaterialSearchableSnapshotRepository.class);
        ElasticsearchIndexAdminService indexAdminService = org.mockito.Mockito.mock(ElasticsearchIndexAdminService.class);
        ElasticsearchIndexSyncService searchSyncService = org.mockito.Mockito.mock(ElasticsearchIndexSyncService.class);
        ElasticsearchHealthService healthService = org.mockito.Mockito.mock(ElasticsearchHealthService.class);
        MutableClock clock = new MutableClock(Instant.parse("2026-04-18T06:00:00Z"));

        when(indexAdminService.currentWriteIndex()).thenReturn("rag-chunks-v1");
        when(queueRepository.requeueFailedSearchSyncEntries(any())).thenReturn(2);
        when(queueRepository.getSearchSyncQueueSnapshot())
            .thenReturn(new MaterialSearchSyncQueueRepository.SearchSyncQueueSnapshot(1, 0, 0, null, clock.instant()))
            .thenReturn(new MaterialSearchSyncQueueRepository.SearchSyncQueueSnapshot(0, 0, 0, null, null));
        when(healthService.currentHealth()).thenReturn(new ElasticsearchHealthService.SearchSyncHealth(
            "UP",
            null,
            null,
            0,
            0,
            0,
            null,
            null,
            Instant.parse("2026-04-18T06:00:03Z")
        ));

        SearchSyncRecoveryService service = new SearchSyncRecoveryService(
            queueRepository,
            searchableSnapshotRepository,
            indexAdminService,
            searchSyncService,
            healthService,
            clock,
            duration -> clock.advance(duration)
        );

        SearchSyncRecoveryService.RequeueRunSummary summary = service.recoverFailedEventsAndWait(
            Duration.ofSeconds(30),
            Duration.ofSeconds(1)
        );

        verify(indexAdminService, never()).prepareConfiguredWriteIndex();
        verify(searchSyncService).requestProcessing();
        assertEquals(2, summary.requeuedFailedCount());
        assertEquals(0, summary.waitSummary().failedCount());
        assertEquals(Instant.parse("2026-04-18T06:00:03Z"), summary.waitSummary().lastSuccessfulSyncAt());
    }

    @Test
    void waitForRecoveryCompletionFailsWhenFailedEventsRemain() {
        MaterialSearchSyncQueueRepository queueRepository = org.mockito.Mockito.mock(MaterialSearchSyncQueueRepository.class);
        MaterialSearchableSnapshotRepository searchableSnapshotRepository =
            org.mockito.Mockito.mock(MaterialSearchableSnapshotRepository.class);
        ElasticsearchIndexAdminService indexAdminService = org.mockito.Mockito.mock(ElasticsearchIndexAdminService.class);
        ElasticsearchIndexSyncService searchSyncService = org.mockito.Mockito.mock(ElasticsearchIndexSyncService.class);
        ElasticsearchHealthService healthService = org.mockito.Mockito.mock(ElasticsearchHealthService.class);
        MutableClock clock = new MutableClock(Instant.parse("2026-04-18T06:00:00Z"));

        when(queueRepository.getSearchSyncQueueSnapshot())
            .thenReturn(new MaterialSearchSyncQueueRepository.SearchSyncQueueSnapshot(0, 0, 1, null, null));

        SearchSyncRecoveryService service = new SearchSyncRecoveryService(
            queueRepository,
            searchableSnapshotRepository,
            indexAdminService,
            searchSyncService,
            healthService,
            clock,
            duration -> {
            }
        );

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> service.waitForRecoveryCompletion(Duration.ofSeconds(5), Duration.ofMillis(100))
        );

        assertEquals(
            "Elasticsearch recovery did not converge because FAILED search-sync events remain: 1",
            exception.getMessage()
        );
    }

    @Test
    void waitForRecoveryCompletionFailsOnTimeout() {
        MaterialSearchSyncQueueRepository queueRepository = org.mockito.Mockito.mock(MaterialSearchSyncQueueRepository.class);
        MaterialSearchableSnapshotRepository searchableSnapshotRepository =
            org.mockito.Mockito.mock(MaterialSearchableSnapshotRepository.class);
        ElasticsearchIndexAdminService indexAdminService = org.mockito.Mockito.mock(ElasticsearchIndexAdminService.class);
        ElasticsearchIndexSyncService searchSyncService = org.mockito.Mockito.mock(ElasticsearchIndexSyncService.class);
        ElasticsearchHealthService healthService = org.mockito.Mockito.mock(ElasticsearchHealthService.class);
        MutableClock clock = new MutableClock(Instant.parse("2026-04-18T06:00:00Z"));

        when(queueRepository.getSearchSyncQueueSnapshot())
            .thenReturn(new MaterialSearchSyncQueueRepository.SearchSyncQueueSnapshot(1, 0, 0, null, clock.instant()))
            .thenReturn(new MaterialSearchSyncQueueRepository.SearchSyncQueueSnapshot(1, 0, 0, null, clock.instant().plusSeconds(1)))
            .thenReturn(new MaterialSearchSyncQueueRepository.SearchSyncQueueSnapshot(1, 0, 0, null, clock.instant().plusSeconds(2)))
            .thenReturn(new MaterialSearchSyncQueueRepository.SearchSyncQueueSnapshot(1, 0, 0, null, clock.instant().plusSeconds(3)));

        SearchSyncRecoveryService service = new SearchSyncRecoveryService(
            queueRepository,
            searchableSnapshotRepository,
            indexAdminService,
            searchSyncService,
            healthService,
            clock,
            duration -> clock.advance(duration)
        );

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> service.waitForRecoveryCompletion(Duration.ofSeconds(2), Duration.ofSeconds(1))
        );

        assertEquals(
            "Timed out waiting for Elasticsearch recovery completion: pending=1, inProgress=0, failed=0",
            exception.getMessage()
        );
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
