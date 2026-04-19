package com.example.demo.service;

import com.example.demo.infrastructure.material.LexicalProviderType;
import com.example.demo.infrastructure.material.MaterialChunkSearchMatch;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record LexicalComparisonSummary(
    String productionProvider,
    String shadowProvider,
    int productionCount,
    int shadowCount,
    int overlapCount,
    List<String> onlyInProductionChunkIds,
    List<String> onlyInShadowChunkIds
) {

    public static LexicalComparisonSummary compare(
        LexicalProviderType productionProvider,
        List<MaterialChunkSearchMatch> productionMatches,
        LexicalProviderType shadowProvider,
        List<MaterialChunkSearchMatch> shadowMatches
    ) {
        List<String> productionChunkIds = chunkIds(productionMatches);
        List<String> shadowChunkIds = chunkIds(shadowMatches);
        Set<String> shadowChunkIdSet = new LinkedHashSet<>(shadowChunkIds);
        Set<String> productionChunkIdSet = new LinkedHashSet<>(productionChunkIds);

        int overlapCount = (int) productionChunkIds.stream()
            .filter(shadowChunkIdSet::contains)
            .count();

        List<String> onlyInProductionChunkIds = productionChunkIds.stream()
            .filter(chunkId -> !shadowChunkIdSet.contains(chunkId))
            .toList();
        List<String> onlyInShadowChunkIds = shadowChunkIds.stream()
            .filter(chunkId -> !productionChunkIdSet.contains(chunkId))
            .toList();

        return new LexicalComparisonSummary(
            productionProvider.propertyValue(),
            shadowProvider.propertyValue(),
            productionChunkIds.size(),
            shadowChunkIds.size(),
            overlapCount,
            onlyInProductionChunkIds,
            onlyInShadowChunkIds
        );
    }

    private static List<String> chunkIds(List<MaterialChunkSearchMatch> matches) {
        if (matches == null || matches.isEmpty()) {
            return List.of();
        }

        return matches.stream()
            .map(match -> match.materialId() + ":" + match.chunkIndex())
            .toList();
    }
}
