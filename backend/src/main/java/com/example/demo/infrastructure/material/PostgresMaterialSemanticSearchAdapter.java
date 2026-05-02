package com.example.demo.infrastructure.material;

import com.example.demo.model.RetrievalFilters;
import com.example.demo.service.material.MaterialChunkSearchMatch;
import com.example.demo.service.material.MaterialSearchScope;
import com.example.demo.service.material.port.SemanticSearchRepository;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

final class PostgresMaterialSemanticSearchAdapter
    extends PostgresMaterialJdbcSupport
    implements SemanticSearchRepository {

    PostgresMaterialSemanticSearchAdapter(
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        super(jdbcTemplate, transactionManager);
    }

    @Override
    public List<MaterialChunkSearchMatch> searchSemantic(float[] queryEmbedding, int limit) {
        return retrievalSearchDao.searchSemantic(queryEmbedding, limit);
    }

    @Override
    public List<MaterialChunkSearchMatch> searchSemantic(float[] queryEmbedding, int limit, Set<String> allowedMaterialIds) {
        return retrievalSearchDao.searchSemantic(queryEmbedding, limit, allowedMaterialIds);
    }

    @Override
    public List<MaterialChunkSearchMatch> searchSemantic(
        float[] queryEmbedding,
        int limit,
        Set<String> allowedMaterialIds,
        RetrievalFilters filters
    ) {
        return retrievalSearchDao.searchSemantic(queryEmbedding, limit, allowedMaterialIds, filters);
    }

    @Override
    public List<MaterialChunkSearchMatch> searchSemantic(float[] queryEmbedding, int limit, MaterialSearchScope scope) {
        return retrievalSearchDao.searchSemantic(queryEmbedding, limit, scope);
    }
}
