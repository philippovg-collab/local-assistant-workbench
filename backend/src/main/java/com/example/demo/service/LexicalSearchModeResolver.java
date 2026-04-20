package com.example.demo.service;

import com.example.demo.service.material.LexicalProviderMode;

import com.example.demo.config.RagProperties;
import org.springframework.stereotype.Service;

@Service
public class LexicalSearchModeResolver {

    private final LexicalProviderMode configuredMode;

    public LexicalSearchModeResolver(RagProperties ragProperties) {
        this.configuredMode = LexicalProviderMode.fromProperty(ragProperties.getLexicalProvider());
    }

    public LexicalProviderMode configuredMode() {
        return configuredMode;
    }
}
