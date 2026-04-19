package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.demo.config.RagProperties;
import com.example.demo.infrastructure.material.LexicalProviderType;
import com.example.demo.infrastructure.material.LexicalSearchProvider;
import com.example.demo.infrastructure.material.MaterialChunkSearchMatch;
import java.util.List;
import org.junit.jupiter.api.Test;

class LexicalSearchStrategyTest {

    @Test
    void defaultsToPostgresProvider() {
        RagProperties ragProperties = new RagProperties();
        StubLexicalSearchProvider provider = new StubLexicalSearchProvider(LexicalProviderType.POSTGRES);

        LexicalSearchStrategy strategy = new LexicalSearchStrategy(List.of(provider), ragProperties);

        assertSame(provider, strategy.resolve());
        assertEquals(LexicalProviderType.POSTGRES, strategy.resolve().type());
    }

    @Test
    void resolvesExplicitPostgresProvider() {
        RagProperties ragProperties = new RagProperties();
        ragProperties.setLexicalProvider("postgres");
        StubLexicalSearchProvider provider = new StubLexicalSearchProvider(LexicalProviderType.POSTGRES);

        LexicalSearchStrategy strategy = new LexicalSearchStrategy(List.of(provider), ragProperties);

        assertSame(provider, strategy.resolve());
        assertEquals(LexicalProviderType.POSTGRES, strategy.resolve().type());
    }

    @Test
    void exposesRegisteredProvidersAndResolvesNonDefaultProviderExplicitly() {
        RagProperties ragProperties = new RagProperties();
        StubLexicalSearchProvider postgres = new StubLexicalSearchProvider(LexicalProviderType.POSTGRES);
        StubLexicalSearchProvider elasticsearch = new StubLexicalSearchProvider(LexicalProviderType.ELASTICSEARCH);

        LexicalSearchStrategy strategy = new LexicalSearchStrategy(List.of(postgres, elasticsearch), ragProperties);

        assertEquals(LexicalProviderType.POSTGRES, strategy.defaultProviderType());
        assertSame(postgres, strategy.resolve());
        assertSame(elasticsearch, strategy.resolve(LexicalProviderType.ELASTICSEARCH));
        assertTrue(strategy.find(LexicalProviderType.ELASTICSEARCH).isPresent());
        assertEquals(2, strategy.providers().size());
    }

    @Test
    void returnsEmptyOptionalWhenProviderIsNotRegistered() {
        RagProperties ragProperties = new RagProperties();
        StubLexicalSearchProvider provider = new StubLexicalSearchProvider(LexicalProviderType.POSTGRES);

        LexicalSearchStrategy strategy = new LexicalSearchStrategy(List.of(provider), ragProperties);

        assertFalse(strategy.find(LexicalProviderType.ELASTICSEARCH).isPresent());
    }

    @Test
    void failsFastOnUnsupportedConfiguredProvider() {
        RagProperties ragProperties = new RagProperties();
        ragProperties.setLexicalProvider("bogus-provider");
        StubLexicalSearchProvider provider = new StubLexicalSearchProvider(LexicalProviderType.POSTGRES);

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> new LexicalSearchStrategy(List.of(provider), ragProperties)
        );

        assertTrue(exception.getMessage().contains("app.rag.lexical-provider"));
        assertTrue(exception.getMessage().contains("postgres"));
    }

    @Test
    void failsFastWhenConfiguredProviderIsValidButNotRegistered() {
        RagProperties ragProperties = new RagProperties();
        ragProperties.setLexicalProvider("elasticsearch");
        StubLexicalSearchProvider provider = new StubLexicalSearchProvider(LexicalProviderType.POSTGRES);

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> new LexicalSearchStrategy(List.of(provider), ragProperties)
        );

        assertTrue(exception.getMessage().contains("elasticsearch"));
        assertTrue(exception.getMessage().contains("not registered"));
    }

    private static final class StubLexicalSearchProvider implements LexicalSearchProvider {

        private final LexicalProviderType type;

        private StubLexicalSearchProvider(LexicalProviderType type) {
            this.type = type;
        }

        @Override
        public LexicalProviderType type() {
            return type;
        }

        @Override
        public List<MaterialChunkSearchMatch> search(String query, int limit) {
            return List.of();
        }
    }
}
