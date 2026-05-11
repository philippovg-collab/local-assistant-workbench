package com.example.demo.service;

import com.example.demo.model.DocumentBlockType;
import com.example.demo.service.material.QualityLayerCoverageSnapshot;
import com.example.demo.service.material.port.QualityLayerMetricsRepository;

import com.example.demo.config.RolloutProperties;
import com.example.demo.model.ActiveBackfillCoverage;
import com.example.demo.model.CoverageStat;
import com.example.demo.model.MetadataCoverage;
import com.example.demo.model.QualityLayerFlags;
import com.example.demo.model.QualityLayerHealth;
import com.example.demo.model.RerankerDelta;
import com.example.demo.model.RetrievalWindowHealth;
import com.example.demo.service.material.ChunkProfile;
import jakarta.annotation.PostConstruct;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class QualityLayerHealthService {

    private static final Logger logger = LoggerFactory.getLogger(QualityLayerHealthService.class);
    private static final int WINDOW_SIZE = 200;
    private static final long COVERAGE_CACHE_TTL_MILLIS = 30_000L;

    private final QualityLayerMetricsRepository metricsRepository;
    private final RolloutProperties rolloutProperties;
    private final MaterialContentSupport contentSupport;
    private final StructuredV1ProofService structuredV1ProofService;
    private final boolean noOp;
    private final Deque<RetrievalWindowSample> retrievalWindow = new ArrayDeque<>();
    private QualityLayerCoverageSnapshot cachedCoverageSnapshot;
    private long cachedCoverageSnapshotAtMillis;

    @Autowired
    public QualityLayerHealthService(
        QualityLayerMetricsRepository metricsRepository,
        RolloutProperties rolloutProperties,
        MaterialContentSupport contentSupport,
        StructuredV1ProofService structuredV1ProofService
    ) {
        this(metricsRepository, rolloutProperties, contentSupport, structuredV1ProofService, false);
    }

    public QualityLayerHealthService(
        QualityLayerMetricsRepository metricsRepository,
        RolloutProperties rolloutProperties
    ) {
        this(
            metricsRepository,
            rolloutProperties,
            null,
            StructuredV1ProofService.allowAllForTests(rolloutProperties),
            false
        );
    }

    private QualityLayerHealthService(
        QualityLayerMetricsRepository metricsRepository,
        RolloutProperties rolloutProperties,
        MaterialContentSupport contentSupport,
        StructuredV1ProofService structuredV1ProofService,
        boolean noOp
    ) {
        this.metricsRepository = metricsRepository;
        this.rolloutProperties = rolloutProperties == null ? new RolloutProperties() : rolloutProperties;
        this.contentSupport = contentSupport;
        this.structuredV1ProofService = structuredV1ProofService == null
            ? StructuredV1ProofService.allowAllForTests(this.rolloutProperties)
            : structuredV1ProofService;
        this.noOp = noOp;
    }

    public static QualityLayerHealthService noop(RolloutProperties rolloutProperties) {
        return new QualityLayerHealthService(
            null,
            rolloutProperties,
            null,
            StructuredV1ProofService.allowAllForTests(rolloutProperties),
            true
        );
    }

    @PostConstruct
    void logEffectiveFlags() {
        logger.info(
            "Quality-layer rollout flags: metadataV1={} structuredV1={} metadataFiltersV1={} searchApiV1={} rerankerV1={} queryHintsV1={}",
            rolloutProperties.isMetadataV1(),
            rolloutProperties.isStructuredV1(),
            rolloutProperties.isMetadataFiltersV1(),
            rolloutProperties.isSearchApiV1(),
            rolloutProperties.isRerankerV1(),
            rolloutProperties.isQueryHintsV1()
        );
    }

    public QualityLayerFlags flags() {
        return new QualityLayerFlags(
            rolloutProperties.isMetadataV1(),
            rolloutProperties.isStructuredV1(),
            rolloutProperties.isMetadataFiltersV1(),
            rolloutProperties.isSearchApiV1(),
            rolloutProperties.isRerankerV1(),
            rolloutProperties.isQueryHintsV1()
        );
    }

    public QualityLayerHealth currentHealth() {
        if (noOp || metricsRepository == null) {
            return new QualityLayerHealth(
                flags(),
                MetadataCoverage.empty(),
                ActiveBackfillCoverage.empty(),
                snapshotWindow(),
                configuredChunkProfile(),
                effectiveChunkProfile(),
                structuredV1ProofService.status()
            );
        }
        QualityLayerCoverageSnapshot coverageSnapshot = cachedCoverageSnapshot();
        return new QualityLayerHealth(
            flags(),
            metadataCoverage(coverageSnapshot),
            activeBackfillCoverage(coverageSnapshot),
            snapshotWindow(),
            configuredChunkProfile(),
            effectiveChunkProfile(),
            structuredV1ProofService.status()
        );
    }

    public synchronized void recordRetrieval(
        List<DocumentBlockType> finalChunkTypes,
        boolean noContext,
        boolean top1Changed,
        boolean top1Improved,
        boolean appendixDemotion,
        boolean highTrustPromotion
    ) {
        if (noOp) {
            return;
        }
        retrievalWindow.addLast(new RetrievalWindowSample(
            noContext,
            finalChunkTypes == null ? List.of() : finalChunkTypes,
            top1Changed,
            top1Improved,
            appendixDemotion,
            highTrustPromotion
        ));
        while (retrievalWindow.size() > WINDOW_SIZE) {
            retrievalWindow.removeFirst();
        }
    }

    private synchronized QualityLayerCoverageSnapshot cachedCoverageSnapshot() {
        long now = System.currentTimeMillis();
        if (
            cachedCoverageSnapshot != null
                && now - cachedCoverageSnapshotAtMillis < COVERAGE_CACHE_TTL_MILLIS
        ) {
            return cachedCoverageSnapshot;
        }
        cachedCoverageSnapshot = metricsRepository.qualityLayerCoverageSnapshot();
        cachedCoverageSnapshotAtMillis = now;
        return cachedCoverageSnapshot == null ? QualityLayerCoverageSnapshot.empty() : cachedCoverageSnapshot;
    }

    private MetadataCoverage metadataCoverage(QualityLayerCoverageSnapshot snapshot) {
        int activeTotal = snapshot.activeTotal();
        if (activeTotal == 0) {
            return MetadataCoverage.empty();
        }

        return new MetadataCoverage(
            activeTotal,
            snapshot.activeWithEffectiveMetadata(),
            ratio(snapshot.activeWithEffectiveMetadata(), activeTotal),
            new CoverageStat(snapshot.workspaceCovered(), ratio(snapshot.workspaceCovered(), activeTotal)),
            new CoverageStat(snapshot.documentTypeCovered(), ratio(snapshot.documentTypeCovered(), activeTotal)),
            new CoverageStat(snapshot.documentStatusCovered(), ratio(snapshot.documentStatusCovered(), activeTotal))
        );
    }

    private ActiveBackfillCoverage activeBackfillCoverage(QualityLayerCoverageSnapshot snapshot) {
        int activeTotal = snapshot.activeTotal();
        if (activeTotal == 0) {
            return ActiveBackfillCoverage.empty();
        }

        int pendingBackfill = Math.max(0, activeTotal - snapshot.structuredProfileActive());
        return new ActiveBackfillCoverage(
            activeTotal,
            snapshot.structuredProfileActive(),
            ratio(snapshot.structuredProfileActive(), activeTotal),
            pendingBackfill,
            snapshot.partialReadyActive()
        );
    }

    private synchronized RetrievalWindowHealth snapshotWindow() {
        if (retrievalWindow.isEmpty()) {
            return RetrievalWindowHealth.empty();
        }

        int sampleSize = retrievalWindow.size();
        int noContextCount = 0;
        int top1ChangedCount = 0;
        int top1ImprovedCount = 0;
        int appendixDemotions = 0;
        int highTrustPromotions = 0;
        Map<String, Integer> hitDistribution = new LinkedHashMap<>();

        for (RetrievalWindowSample sample : retrievalWindow) {
            if (sample.noContext()) {
                noContextCount += 1;
            }
            if (sample.top1Changed()) {
                top1ChangedCount += 1;
            }
            if (sample.top1Improved()) {
                top1ImprovedCount += 1;
            }
            if (sample.appendixDemotion()) {
                appendixDemotions += 1;
            }
            if (sample.highTrustPromotion()) {
                highTrustPromotions += 1;
            }
            for (DocumentBlockType chunkType : sample.finalChunkTypes()) {
                String key = chunkType == null ? "UNKNOWN" : chunkType.name();
                hitDistribution.merge(key, 1, Integer::sum);
            }
        }

        Map<String, Integer> sortedDistribution = hitDistribution.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
            .collect(
                LinkedHashMap::new,
                (map, entry) -> map.put(entry.getKey(), entry.getValue()),
                LinkedHashMap::putAll
            );

        return new RetrievalWindowHealth(
            sampleSize,
            ratio(noContextCount, sampleSize),
            sortedDistribution,
            new RerankerDelta(top1ChangedCount, top1ImprovedCount, appendixDemotions, highTrustPromotions)
        );
    }

    private static double ratio(int value, int total) {
        if (total <= 0) {
            return 0.0d;
        }
        return (double) value / (double) total;
    }

    private String configuredChunkProfile() {
        return contentSupport == null ? null : contentSupport.configuredChunkProfile().propertyValue();
    }

    private String effectiveChunkProfile() {
        if (contentSupport == null) {
            return rolloutProperties.isStructuredV1()
                ? ChunkProfile.STRUCTURED_V1.propertyValue()
                : ChunkProfile.FIXED_V1.propertyValue();
        }
        return contentSupport.configuredChunkProfile(rolloutProperties.isStructuredV1()).propertyValue();
    }

    private record RetrievalWindowSample(
        boolean noContext,
        List<DocumentBlockType> finalChunkTypes,
        boolean top1Changed,
        boolean top1Improved,
        boolean appendixDemotion,
        boolean highTrustPromotion
    ) {
        private RetrievalWindowSample {
            finalChunkTypes = finalChunkTypes == null ? List.of() : List.copyOf(new ArrayList<>(finalChunkTypes));
        }
    }
}
