package com.example.demo.service.material.port;

import com.example.demo.service.material.LexicalProviderType;
import com.example.demo.service.material.MaterialChunkSearchMatch;

import java.util.List;
import java.util.Set;
import com.example.demo.model.RetrievalFilters;

public interface LexicalSearchProvider {

    LexicalProviderType type();

    List<MaterialChunkSearchMatch> search(String query, int limit);

    default List<MaterialChunkSearchMatch> search(String query, int limit, Set<String> allowedMaterialIds) {
        if (allowedMaterialIds == null || allowedMaterialIds.isEmpty()) {
            return search(query, limit);
        }

        return search(query, limit).stream()
            .filter(match -> allowedMaterialIds.contains(match.materialId()))
            .limit(limit)
            .toList();
    }

    default List<MaterialChunkSearchMatch> search(
        String query,
        int limit,
        Set<String> allowedMaterialIds,
        RetrievalFilters filters
    ) {
        return search(query, limit, allowedMaterialIds);
    }
}
