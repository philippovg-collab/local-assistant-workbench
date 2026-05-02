package com.example.demo.service;

import com.example.demo.service.material.LexicalProviderType;
import com.example.demo.service.material.MaterialChunkSearchMatch;
import com.example.demo.service.material.MaterialSearchScope;

import java.util.List;

public interface LexicalShadowComparisonService {

    void compareIfEligible(
        String query,
        LexicalProviderType productionProviderType,
        List<MaterialChunkSearchMatch> productionMatches,
        int limit
    );

    default void compareIfEligible(
        String query,
        LexicalProviderType productionProviderType,
        List<MaterialChunkSearchMatch> productionMatches,
        int limit,
        MaterialSearchScope scope
    ) {
        if (scope != null && scope.isNoResults()) {
            return;
        }
        compareIfEligible(query, productionProviderType, productionMatches, limit);
    }
}
