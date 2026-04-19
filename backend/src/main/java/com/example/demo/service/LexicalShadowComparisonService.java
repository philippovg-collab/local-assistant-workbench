package com.example.demo.service;

import com.example.demo.infrastructure.material.LexicalProviderType;
import com.example.demo.infrastructure.material.MaterialChunkSearchMatch;
import java.util.List;

public interface LexicalShadowComparisonService {

    void compareIfEligible(
        String query,
        LexicalProviderType productionProviderType,
        List<MaterialChunkSearchMatch> productionMatches,
        int limit
    );
}
