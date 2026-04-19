package com.example.demo.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.example.demo.config.SearchSyncProperties;
import com.example.demo.infrastructure.material.MaterialSearchSyncQueueRepository;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class ElasticsearchHealthService {

    private final SearchSyncProperties searchSyncProperties;
    private final MaterialSearchSyncQueueRepository queueRepository;
    private final ObjectProvider<ElasticsearchClient> elasticsearchClientProvider;
    private final Clock clock;
    private volatile CachedSearchSyncHealth cachedHealth;

    @Autowired
    public ElasticsearchHealthService(
        SearchSyncProperties searchSyncProperties,
        MaterialSearchSyncQueueRepository queueRepository,
        ObjectProvider<ElasticsearchClient> elasticsearchClientProvider
    ) {
        this(
            searchSyncProperties,
            queueRepository,
            elasticsearchClientProvider,
            Clock.systemUTC()
        );
    }

    ElasticsearchHealthService(
        SearchSyncProperties searchSyncProperties,
        MaterialSearchSyncQueueRepository queueRepository,
        ObjectProvider<ElasticsearchClient> elasticsearchClientProvider,
        Clock clock
    ) {
        this.searchSyncProperties = searchSyncProperties;
        this.queueRepository = queueRepository;
        this.elasticsearchClientProvider = elasticsearchClientProvider;
        this.clock = clock;
    }

    public SearchSyncHealth currentHealth() {
        return snapshot().health();
    }

    public synchronized SearchSyncHealth refreshHealthSnapshotIfStale() {
        CachedSearchSyncHealth snapshot = snapshot();
        if (!snapshot.stale(clock.instant(), searchSyncProperties.getHealthSnapshotTtlSeconds())) {
            return snapshot.health();
        }

        return refreshHealthSnapshot();
    }

    public synchronized SearchSyncHealth refreshHealthSnapshot() {
        SearchSyncHealth refreshed = loadHealthWithProbe(lastSuccessfulSyncAt());
        cachedHealth = new CachedSearchSyncHealth(refreshed, clock.instant());
        return refreshed;
    }

    public synchronized SearchSyncHealth recordSuccessfulSync(Instant syncedAt) {
        Instant observedAt = syncedAt == null ? clock.instant() : syncedAt;
        Instant lastSuccessfulSyncAt = maxInstant(lastSuccessfulSyncAt(), syncedAt);
        SearchSyncHealth updated = loadCachedHealth(lastSuccessfulSyncAt, "UP", null, null);
        cachedHealth = new CachedSearchSyncHealth(updated, observedAt);
        return updated;
    }

    public synchronized SearchSyncHealth recordSyncFailure(String reasonCode, String reasonMessage, Instant failedAt) {
        Instant observedAt = failedAt == null ? clock.instant() : failedAt;
        SearchSyncHealth updated = loadCachedHealth(
            lastSuccessfulSyncAt(),
            "DEGRADED",
            reasonCode == null ? "search.sync_failed" : reasonCode,
            "Elasticsearch sync failed: " + normalizeMessage(reasonMessage)
        );
        cachedHealth = new CachedSearchSyncHealth(updated, observedAt);
        return updated;
    }

    public synchronized SearchSyncHealth recordRuntimeFailure(Throwable throwable) {
        Instant observedAt = clock.instant();
        SearchSyncHealth updated = loadCachedHealth(
            lastSuccessfulSyncAt(),
            "DEGRADED",
            "search.runtime_failure",
            "Elasticsearch query failed at runtime, falling back to PostgreSQL: " + rootMessage(throwable)
        );
        cachedHealth = new CachedSearchSyncHealth(updated, observedAt);
        return updated;
    }

    private CachedSearchSyncHealth snapshot() {
        CachedSearchSyncHealth snapshot = cachedHealth;
        if (snapshot != null) {
            return snapshot;
        }

        synchronized (this) {
            if (cachedHealth == null) {
                cachedHealth = new CachedSearchSyncHealth(loadCachedHealth(null, "UP", null, null), null);
            }
            return cachedHealth;
        }
    }

    private SearchSyncHealth loadCachedHealth(
        Instant lastSuccessfulSyncAt,
        String clusterStatusOverride,
        String reasonCodeOverride,
        String reasonMessageOverride
    ) {
        MaterialSearchSyncQueueRepository.SearchSyncQueueSnapshot queueSnapshot = queueRepository.getSearchSyncQueueSnapshot();
        return buildHealth(
            queueSnapshot,
            lastSuccessfulSyncAt,
            clusterStatusOverride,
            reasonCodeOverride,
            reasonMessageOverride
        );
    }

    private SearchSyncHealth loadHealthWithProbe(Instant lastSuccessfulSyncAt) {
        MaterialSearchSyncQueueRepository.SearchSyncQueueSnapshot queueSnapshot = queueRepository.getSearchSyncQueueSnapshot();
        if (!searchSyncProperties.isEnabled()) {
            return disabledHealth(queueSnapshot, lastSuccessfulSyncAt);
        }

        ElasticsearchClient elasticsearchClient = elasticsearchClientProvider.getIfAvailable();
        if (elasticsearchClient == null) {
            return clientNotConfiguredHealth(queueSnapshot, lastSuccessfulSyncAt);
        }

        try {
            elasticsearchClient.info();
            return buildHealth(queueSnapshot, lastSuccessfulSyncAt, "UP", null, null);
        } catch (IOException exception) {
            return buildHealth(
                queueSnapshot,
                lastSuccessfulSyncAt,
                "DOWN",
                "search.cluster_unavailable",
                "Elasticsearch cluster is unavailable: " + rootMessage(exception)
            );
        } catch (RuntimeException exception) {
            return buildHealth(
                queueSnapshot,
                lastSuccessfulSyncAt,
                "DOWN",
                "search.cluster_unavailable",
                "Elasticsearch cluster is unavailable: " + rootMessage(exception)
            );
        }
    }

    private SearchSyncHealth buildHealth(
        MaterialSearchSyncQueueRepository.SearchSyncQueueSnapshot queueSnapshot,
        Instant lastSuccessfulSyncAt,
        String clusterStatusOverride,
        String reasonCodeOverride,
        String reasonMessageOverride
    ) {
        if (!searchSyncProperties.isEnabled()) {
            return disabledHealth(queueSnapshot, lastSuccessfulSyncAt);
        }

        if (elasticsearchClientProvider.getIfAvailable() == null) {
            return clientNotConfiguredHealth(queueSnapshot, lastSuccessfulSyncAt);
        }

        String clusterStatus = clusterStatusOverride == null ? "UP" : clusterStatusOverride;
        String reasonCode = reasonCodeOverride;
        String reasonMessage = reasonMessageOverride;

        if ("UP".equals(clusterStatus)) {
            SearchSyncHealth queueEvaluatedHealth = applyQueueState(queueSnapshot, lastSuccessfulSyncAt);
            clusterStatus = queueEvaluatedHealth.clusterStatus();
            reasonCode = queueEvaluatedHealth.reasonCode();
            reasonMessage = queueEvaluatedHealth.reasonMessage();
        }

        return new SearchSyncHealth(
            clusterStatus,
            reasonCode,
            reasonMessage,
            queueSnapshot.pendingCount(),
            queueSnapshot.inProgressCount(),
            queueSnapshot.failedCount(),
            queueSnapshot.nextRetryAt(),
            queueSnapshot.oldestOutstandingAt(),
            lastSuccessfulSyncAt
        );
    }

    private SearchSyncHealth applyQueueState(
        MaterialSearchSyncQueueRepository.SearchSyncQueueSnapshot queueSnapshot,
        Instant lastSuccessfulSyncAt
    ) {
        String clusterStatus = "UP";
        String reasonCode = null;
        String reasonMessage = null;
        Instant now = clock.instant();
        boolean hasOutstandingBacklog = queueSnapshot.pendingCount() > 0 || queueSnapshot.inProgressCount() > 0;
        boolean failedThresholdExceeded = queueSnapshot.failedCount() > searchSyncProperties.getMaxFailedEventsBeforeFallback();
        boolean backlogIsStale = queueSnapshot.oldestOutstandingAt() != null
            && queueSnapshot.oldestOutstandingAt().plusSeconds(searchSyncProperties.getMaxOutstandingSeconds()).isBefore(now);
        boolean backlogWithoutSuccessfulSync = hasOutstandingBacklog && lastSuccessfulSyncAt == null;

        if (failedThresholdExceeded) {
            clusterStatus = "DEGRADED";
            reasonCode = "search.sync_failures";
            reasonMessage = "Elasticsearch sync has failed events pending operator attention.";
        } else if (backlogIsStale) {
            clusterStatus = "DEGRADED";
            reasonCode = "search.sync_backlog_stale";
            reasonMessage =
                "Elasticsearch sync backlog is older than the safe threshold of "
                    + searchSyncProperties.getMaxOutstandingSeconds()
                    + " seconds.";
        } else if (backlogWithoutSuccessfulSync) {
            clusterStatus = "DEGRADED";
            reasonCode = "search.sync_pending_without_success";
            reasonMessage = "Elasticsearch sync has outstanding backlog and no successful sync has completed yet.";
        }

        return new SearchSyncHealth(
            clusterStatus,
            reasonCode,
            reasonMessage,
            queueSnapshot.pendingCount(),
            queueSnapshot.inProgressCount(),
            queueSnapshot.failedCount(),
            queueSnapshot.nextRetryAt(),
            queueSnapshot.oldestOutstandingAt(),
            lastSuccessfulSyncAt
        );
    }

    private SearchSyncHealth disabledHealth(
        MaterialSearchSyncQueueRepository.SearchSyncQueueSnapshot queueSnapshot,
        Instant lastSuccessfulSyncAt
    ) {
        return new SearchSyncHealth(
            "DISABLED",
            "search.sync_disabled",
            "Elasticsearch search sync is disabled by configuration.",
            queueSnapshot.pendingCount(),
            queueSnapshot.inProgressCount(),
            queueSnapshot.failedCount(),
            queueSnapshot.nextRetryAt(),
            queueSnapshot.oldestOutstandingAt(),
            lastSuccessfulSyncAt
        );
    }

    private SearchSyncHealth clientNotConfiguredHealth(
        MaterialSearchSyncQueueRepository.SearchSyncQueueSnapshot queueSnapshot,
        Instant lastSuccessfulSyncAt
    ) {
        return new SearchSyncHealth(
            "DOWN",
            "search.client_not_configured",
            "Elasticsearch client is not configured in the current application context.",
            queueSnapshot.pendingCount(),
            queueSnapshot.inProgressCount(),
            queueSnapshot.failedCount(),
            queueSnapshot.nextRetryAt(),
            queueSnapshot.oldestOutstandingAt(),
            lastSuccessfulSyncAt
        );
    }

    private Instant lastSuccessfulSyncAt() {
        CachedSearchSyncHealth snapshot = cachedHealth;
        return snapshot == null ? null : snapshot.health().lastSuccessfulSyncAt();
    }

    private Instant maxInstant(Instant current, Instant candidate) {
        if (current == null) {
            return candidate;
        }
        if (candidate == null || current.isAfter(candidate)) {
            return current;
        }
        return candidate;
    }

    private String normalizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "Unknown Elasticsearch sync failure";
        }
        return message;
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    public record SearchSyncHealth(
        String clusterStatus,
        String reasonCode,
        String reasonMessage,
        int pendingCount,
        int inProgressCount,
        int failedCount,
        Instant nextRetryAt,
        Instant oldestOutstandingAt,
        Instant lastSuccessfulSyncAt
    ) {
        public boolean productionReady() {
            return "UP".equals(clusterStatus);
        }
    }

    private record CachedSearchSyncHealth(
        SearchSyncHealth health,
        Instant refreshedAt
    ) {
        private boolean stale(Instant now, int ttlSeconds) {
            if (refreshedAt == null) {
                return true;
            }
            return refreshedAt.plusSeconds(Math.max(1, ttlSeconds)).isBefore(now);
        }
    }
}
