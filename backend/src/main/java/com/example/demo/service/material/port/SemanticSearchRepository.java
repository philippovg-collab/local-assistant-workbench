package com.example.demo.service.material.port;

import com.example.demo.service.material.MaterialChunkSearchMatch;
import com.example.demo.service.material.MaterialSearchScope;

import java.util.List;
import java.util.Set;
import com.example.demo.model.RetrievalFilters;

public interface SemanticSearchRepository {

    List<MaterialChunkSearchMatch> searchSemantic(float[] queryEmbedding, int limit);

    default List<MaterialChunkSearchMatch> searchSemantic(
        float[] queryEmbedding,
        int limit,
        MaterialSearchScope scope
    ) {
        MaterialSearchScope safeScope = scope == null ? MaterialSearchScope.unscoped() : scope;
        if (safeScope.isNoResults()) {
            return List.of();
        }
        if (safeScope.isUnscoped()) {
            return searchSemantic(queryEmbedding, limit);
        }
        if (safeScope.isMaterialIds()) {
            if (safeScope.requiresCriteriaFiltering()) {
                throw new UnsupportedOperationException("Semantic search provider does not support filtered material ids");
            }
            return searchSemantic(queryEmbedding, limit).stream()
                .filter(match -> safeScope.materialIds().contains(match.materialId()))
                .limit(limit)
                .toList();
        }
        throw new UnsupportedOperationException("Semantic search provider does not support filtered material scope");
    }

    @Deprecated
    default List<MaterialChunkSearchMatch> searchSemantic(
        float[] queryEmbedding,
        int limit,
        Set<String> allowedMaterialIds
    ) {
        return searchSemantic(queryEmbedding, limit, MaterialSearchScope.fromLegacyMaterialIds(allowedMaterialIds));
    }

    @Deprecated
    default List<MaterialChunkSearchMatch> searchSemantic(
        float[] queryEmbedding,
        int limit,
        Set<String> allowedMaterialIds,
        RetrievalFilters filters
    ) {
        return searchSemantic(
            queryEmbedding,
            limit,
            MaterialSearchScope.fromLegacyMaterialIds(allowedMaterialIds, filters)
        );
    }
}
