package com.example.demo.service;

import com.example.demo.config.RagProperties;
import com.example.demo.infrastructure.material.LexicalProviderType;
import com.example.demo.infrastructure.material.LexicalSearchProvider;
import com.example.demo.infrastructure.material.MaterialChunkSearchMatch;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SampledLexicalShadowComparisonService implements LexicalShadowComparisonService {

    private static final Logger logger = LoggerFactory.getLogger(SampledLexicalShadowComparisonService.class);

    private final LexicalSearchStrategy lexicalSearchStrategy;
    private final RagProperties ragProperties;

    public SampledLexicalShadowComparisonService(
        LexicalSearchStrategy lexicalSearchStrategy,
        RagProperties ragProperties
    ) {
        this.lexicalSearchStrategy = lexicalSearchStrategy;
        this.ragProperties = ragProperties;
    }

    @Override
    public void compareIfEligible(
        String query,
        LexicalProviderType productionProviderType,
        List<MaterialChunkSearchMatch> productionMatches,
        int limit
    ) {
        if (!ragProperties.isShadowEnabled() || limit <= 0) {
            return;
        }

        String queryFingerprint = fingerprintOf(query);
        if (!shouldSample(queryFingerprint)) {
            return;
        }

        LexicalSearchProvider shadowProvider = lexicalSearchStrategy.find(LexicalProviderType.ELASTICSEARCH)
            .orElse(null);
        if (shadowProvider == null || shadowProvider.type() == productionProviderType) {
            return;
        }

        try {
            List<MaterialChunkSearchMatch> shadowMatches = shadowProvider.search(query, limit);
            LexicalComparisonSummary summary = LexicalComparisonSummary.compare(
                productionProviderType,
                productionMatches,
                shadowProvider.type(),
                shadowMatches
            );
            logger.info(
                "Shadow lexical comparison: queryFingerprint={} providerPair={}->{} productionCount={} shadowCount={} overlapCount={} onlyInProduction={} onlyInShadow={}",
                queryFingerprint,
                summary.productionProvider(),
                summary.shadowProvider(),
                summary.productionCount(),
                summary.shadowCount(),
                summary.overlapCount(),
                summary.onlyInProductionChunkIds(),
                summary.onlyInShadowChunkIds()
            );
        } catch (RuntimeException exception) {
            logger.warn(
                "Shadow lexical comparison failed: queryFingerprint={} providerPair={}->{} message={}",
                queryFingerprint,
                productionProviderType.propertyValue(),
                LexicalProviderType.ELASTICSEARCH.propertyValue(),
                rootMessage(exception),
                exception
            );
        }
    }

    boolean shouldSample(String queryFingerprint) {
        int samplePercent = normalizedSamplePercent();
        if (samplePercent <= 0 || queryFingerprint == null || queryFingerprint.isBlank()) {
            return false;
        }
        if (samplePercent >= 100) {
            return true;
        }

        int bucket = Math.floorMod(queryFingerprint.hashCode(), 100);
        return bucket < samplePercent;
    }

    String fingerprintOf(String query) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (normalizedQuery.isBlank()) {
            return "blank";
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalizedQuery.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to compute shadow comparison fingerprint", exception);
        }
    }

    int samplingBucket(String query) {
        String fingerprint = fingerprintOf(query);
        if ("blank".equals(fingerprint)) {
            return 0;
        }
        byte[] fingerprintBytes = HexFormat.of().parseHex(fingerprint);
        int bucketSeed = ByteBuffer.wrap(fingerprintBytes).getInt();
        return Math.floorMod(bucketSeed, 100);
    }

    private int normalizedSamplePercent() {
        return Math.max(0, Math.min(100, ragProperties.getShadowSamplePercent()));
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
