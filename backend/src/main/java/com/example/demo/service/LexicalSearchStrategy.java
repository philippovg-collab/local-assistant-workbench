package com.example.demo.service;

import com.example.demo.service.material.LexicalProviderMode;
import com.example.demo.service.material.LexicalProviderType;
import com.example.demo.service.material.port.LexicalSearchProvider;

import com.example.demo.config.RagProperties;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LexicalSearchStrategy {

    private final Map<LexicalProviderType, LexicalSearchProvider> providersByType;
    private final LexicalProviderType legacyDefaultProviderType;

    @Autowired
    public LexicalSearchStrategy(List<LexicalSearchProvider> providers) {
        this.providersByType = indexProviders(providers);
        this.legacyDefaultProviderType = LexicalProviderType.POSTGRES;
    }

    public LexicalSearchStrategy(List<LexicalSearchProvider> providers, RagProperties ragProperties) {
        this.providersByType = indexProviders(providers);
        LexicalProviderMode configuredMode = LexicalProviderMode.fromProperty(ragProperties.getLexicalProvider());
        this.legacyDefaultProviderType = configuredMode == LexicalProviderMode.ELASTICSEARCH
            ? LexicalProviderType.ELASTICSEARCH
            : LexicalProviderType.POSTGRES;
        resolve(legacyDefaultProviderType);
    }

    public LexicalSearchProvider resolve() {
        return resolve(legacyDefaultProviderType);
    }

    public LexicalSearchProvider resolve(LexicalProviderType providerType) {
        LexicalSearchProvider provider = providersByType.get(providerType);
        if (provider == null) {
            throw new IllegalStateException(
                "Configured lexical provider '" + providerType.propertyValue() + "' is not registered."
            );
        }
        return provider;
    }

    public Optional<LexicalSearchProvider> find(LexicalProviderType providerType) {
        return Optional.ofNullable(providersByType.get(providerType));
    }

    public LexicalProviderType defaultProviderType() {
        return legacyDefaultProviderType;
    }

    public Map<LexicalProviderType, LexicalSearchProvider> providers() {
        return Collections.unmodifiableMap(providersByType);
    }

    private Map<LexicalProviderType, LexicalSearchProvider> indexProviders(List<LexicalSearchProvider> providers) {
        if (providers == null || providers.isEmpty()) {
            throw new IllegalStateException("No lexical search providers are registered.");
        }

        Map<LexicalProviderType, LexicalSearchProvider> providersByType = new EnumMap<>(LexicalProviderType.class);
        for (LexicalSearchProvider provider : providers) {
            LexicalSearchProvider previous = providersByType.putIfAbsent(provider.type(), provider);
            if (previous != null) {
                throw new IllegalStateException(
                    "Multiple lexical search providers are registered for type '" + provider.type().propertyValue() + "'."
                );
            }
        }
        return providersByType;
    }
}
