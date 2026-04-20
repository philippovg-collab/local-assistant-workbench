package com.example.demo.service.material.port;

import com.example.demo.service.material.MaterialChunkSearchMatch;

import java.util.List;
import java.util.Set;
import com.example.demo.model.RetrievalFilters;

public interface SemanticSearchRepository {

    List<MaterialChunkSearchMatch> searchSemantic(float[] queryEmbedding, int limit);

    default List<MaterialChunkSearchMatch> searchSemantic(
        float[] queryEmbedding,
        int limit,
        Set<String> allowedMaterialIds
    ) {
        if (allowedMaterialIds == null || allowedMaterialIds.isEmpty()) {
            return List.of();
        }

        return searchSemantic(queryEmbedding, limit).stream()
            .filter(match -> allowedMaterialIds.contains(match.materialId()))
            .limit(limit)
            .toList();
    }

    default List<MaterialChunkSearchMatch> searchSemantic(
        float[] queryEmbedding,
        int limit,
        Set<String> allowedMaterialIds,
        RetrievalFilters filters
    ) {
        return searchSemantic(queryEmbedding, limit, allowedMaterialIds);
    }
}
