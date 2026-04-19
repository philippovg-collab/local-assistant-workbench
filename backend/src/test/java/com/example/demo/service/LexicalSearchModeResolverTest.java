package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.config.RagProperties;
import com.example.demo.infrastructure.material.LexicalProviderMode;
import org.junit.jupiter.api.Test;

class LexicalSearchModeResolverTest {

    @Test
    void parsesConfiguredAutoMode() {
        RagProperties ragProperties = new RagProperties();
        ragProperties.setLexicalProvider("auto");

        LexicalSearchModeResolver resolver = new LexicalSearchModeResolver(ragProperties);

        assertEquals(LexicalProviderMode.AUTO, resolver.configuredMode());
    }

    @Test
    void failsFastOnUnsupportedConfiguredMode() {
        RagProperties ragProperties = new RagProperties();
        ragProperties.setLexicalProvider("bogus-provider");

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> new LexicalSearchModeResolver(ragProperties)
        );

        assertTrue(exception.getMessage().contains("app.rag.lexical-provider"));
    }
}
