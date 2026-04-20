package com.example.demo.service;

import com.example.demo.service.material.LexicalProviderType;
import com.example.demo.service.material.MaterialChunkSearchMatch;
import com.example.demo.service.material.port.LexicalSearchProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.config.RagProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class SampledLexicalShadowComparisonServiceTest {

    @Test
    void skipsShadowComparisonWhenShadowModeIsDisabled() {
        CountingLexicalSearchProvider postgres = new CountingLexicalSearchProvider(LexicalProviderType.POSTGRES);
        CountingLexicalSearchProvider elasticsearch = new CountingLexicalSearchProvider(LexicalProviderType.ELASTICSEARCH);
        RagProperties ragProperties = new RagProperties();
        ragProperties.setShadowEnabled(false);
        ragProperties.setShadowSamplePercent(100);
        SampledLexicalShadowComparisonService service = new SampledLexicalShadowComparisonService(
            new LexicalSearchStrategy(List.of(postgres, elasticsearch), ragProperties),
            ragProperties
        );

        service.compareIfEligible("pricing premium", LexicalProviderType.POSTGRES, List.of(), 5);

        assertEquals(0, elasticsearch.searchCalls);
    }

    @Test
    void samplesShadowComparisonDeterministicallyByFingerprint() {
        RagProperties ragProperties = new RagProperties();
        ragProperties.setShadowEnabled(true);
        ragProperties.setShadowSamplePercent(100);
        SampledLexicalShadowComparisonService service = new SampledLexicalShadowComparisonService(
            new LexicalSearchStrategy(List.of(
                new CountingLexicalSearchProvider(LexicalProviderType.POSTGRES),
                new CountingLexicalSearchProvider(LexicalProviderType.ELASTICSEARCH)
            ), ragProperties),
            ragProperties
        );

        String queryFingerprint = service.fingerprintOf("Premium Tariff");

        assertFalse("Premium Tariff".equals(queryFingerprint));
        assertEquals(queryFingerprint, service.fingerprintOf("premium tariff"));
        assertTrue(service.shouldSample(queryFingerprint));
    }

    @Test
    void invokesElasticsearchShadowProviderWhenSampled() {
        CountingLexicalSearchProvider postgres = new CountingLexicalSearchProvider(LexicalProviderType.POSTGRES);
        CountingLexicalSearchProvider elasticsearch = new CountingLexicalSearchProvider(LexicalProviderType.ELASTICSEARCH);
        RagProperties ragProperties = new RagProperties();
        ragProperties.setShadowEnabled(true);
        ragProperties.setShadowSamplePercent(100);
        SampledLexicalShadowComparisonService service = new SampledLexicalShadowComparisonService(
            new LexicalSearchStrategy(List.of(postgres, elasticsearch), ragProperties),
            ragProperties
        );

        service.compareIfEligible(
            "электроэнергия",
            LexicalProviderType.POSTGRES,
            List.of(new MaterialChunkSearchMatch("material-1", 0, "Energy", "электричество", 1, "ocr", false, null, 1.0d)),
            5
        );

        assertEquals(1, elasticsearch.searchCalls);
        assertEquals("электроэнергия", elasticsearch.lastQuery);
        assertEquals(5, elasticsearch.lastLimit);
    }

    private static final class CountingLexicalSearchProvider implements LexicalSearchProvider {

        private final LexicalProviderType providerType;
        private int searchCalls;
        private String lastQuery;
        private int lastLimit;

        private CountingLexicalSearchProvider(LexicalProviderType providerType) {
            this.providerType = providerType;
        }

        @Override
        public LexicalProviderType type() {
            return providerType;
        }

        @Override
        public List<MaterialChunkSearchMatch> search(String query, int limit) {
            searchCalls++;
            lastQuery = query;
            lastLimit = limit;
            return List.of(new MaterialChunkSearchMatch(
                providerType.propertyValue() + "-material",
                0,
                providerType.propertyValue(),
                providerType.propertyValue(),
                null,
                "direct-text",
                false,
                null,
                1.0d
            ));
        }
    }
}
