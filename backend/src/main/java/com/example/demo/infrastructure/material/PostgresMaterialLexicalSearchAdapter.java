package com.example.demo.infrastructure.material;

import com.example.demo.model.RetrievalFilters;
import com.example.demo.service.material.LexicalProviderType;
import com.example.demo.service.material.MaterialChunkSearchMatch;
import com.example.demo.service.material.MaterialSearchScope;
import com.example.demo.service.material.port.LexicalSearchProvider;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

final class PostgresMaterialLexicalSearchAdapter
    extends PostgresMaterialJdbcSupport
    implements LexicalSearchProvider {

    PostgresMaterialLexicalSearchAdapter(
        JdbcTemplate jdbcTemplate,
        PlatformTransactionManager transactionManager
    ) {
        super(jdbcTemplate, transactionManager);
    }

    @Override
    public LexicalProviderType type() {
        return retrievalSearchDao.type();
    }

    @Override
    public List<MaterialChunkSearchMatch> search(String query, int limit) {
        return retrievalSearchDao.search(query, limit);
    }

    @Override
    public List<MaterialChunkSearchMatch> search(String query, int limit, Set<String> allowedMaterialIds) {
        return retrievalSearchDao.search(query, limit, allowedMaterialIds);
    }

    @Override
    public List<MaterialChunkSearchMatch> search(
        String query,
        int limit,
        Set<String> allowedMaterialIds,
        RetrievalFilters filters
    ) {
        return retrievalSearchDao.search(query, limit, allowedMaterialIds, filters);
    }

    @Override
    public List<MaterialChunkSearchMatch> search(String query, int limit, MaterialSearchScope scope) {
        return retrievalSearchDao.search(query, limit, scope);
    }
}
