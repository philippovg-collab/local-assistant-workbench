package com.example.demo.service.material.port;

import com.example.demo.service.material.LexicalProviderType;
import com.example.demo.service.material.MaterialChunkSearchMatch;
import com.example.demo.service.material.MaterialSearchScope;

import java.util.List;
import java.util.Set;
import com.example.demo.model.RetrievalFilters;

public interface LexicalSearchProvider {

    LexicalProviderType type();

    List<MaterialChunkSearchMatch> search(String query, int limit);

    default List<MaterialChunkSearchMatch> search(String query, int limit, MaterialSearchScope scope) {
        MaterialSearchScope safeScope = scope == null ? MaterialSearchScope.unscoped() : scope;
        if (safeScope.isNoResults()) {
            return List.of();
        }
        if (safeScope.isUnscoped()) {
            return search(query, limit);
        }
        if (safeScope.isMaterialIds()) {
            if (safeScope.requiresCriteriaFiltering()) {
                throw new UnsupportedOperationException("Lexical search provider does not support filtered material ids");
            }
            return search(query, limit).stream()
                .filter(match -> safeScope.materialIds().contains(match.materialId()))
                .limit(limit)
                .toList();
        }
        throw new UnsupportedOperationException("Lexical search provider does not support filtered material scope");
    }

    @Deprecated
    default List<MaterialChunkSearchMatch> search(String query, int limit, Set<String> allowedMaterialIds) {
        return search(query, limit, MaterialSearchScope.fromLegacyMaterialIds(allowedMaterialIds));
    }

    @Deprecated
    default List<MaterialChunkSearchMatch> search(
        String query,
        int limit,
        Set<String> allowedMaterialIds,
        RetrievalFilters filters
    ) {
        return search(query, limit, MaterialSearchScope.fromLegacyMaterialIds(allowedMaterialIds, filters));
    }
}
