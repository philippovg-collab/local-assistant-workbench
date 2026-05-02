package com.example.demo.infrastructure.material;

import static com.example.demo.infrastructure.material.MaterialJdbcRowMappers.CHUNK_SEARCH_ROW_MAPPER;

import com.example.demo.api.ApiException;
import com.example.demo.model.RetrievalFilters;
import com.example.demo.service.material.LexicalProviderType;
import com.example.demo.service.material.MaterialChunkSearchMatch;
import com.example.demo.service.material.MaterialSearchScope;
import com.pgvector.PGvector;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

final class PostgresMaterialRetrievalSearchDao {

    private final JdbcTemplate jdbcTemplate;
    private final PostgresMaterialFilterSqlBuilder filterSqlBuilder;

    PostgresMaterialRetrievalSearchDao(
        JdbcTemplate jdbcTemplate,
        PostgresMaterialFilterSqlBuilder filterSqlBuilder
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.filterSqlBuilder = filterSqlBuilder;
    }

    LexicalProviderType type() {
        return LexicalProviderType.POSTGRES;
    }

    List<MaterialChunkSearchMatch> searchSemantic(float[] queryEmbedding, int limit) {
        return searchSemantic(queryEmbedding, limit, MaterialSearchScope.unscoped());
    }

    List<MaterialChunkSearchMatch> searchSemantic(float[] queryEmbedding, int limit, Set<String> allowedMaterialIds) {
        return searchSemantic(queryEmbedding, limit, MaterialSearchScope.fromLegacyMaterialIds(allowedMaterialIds));
    }

    List<MaterialChunkSearchMatch> searchSemantic(
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

    List<MaterialChunkSearchMatch> searchSemantic(
        float[] queryEmbedding,
        int limit,
        MaterialSearchScope scope
    ) {
        if (queryEmbedding == null || queryEmbedding.length == 0 || limit <= 0) {
            return List.of();
        }
        MaterialSearchScope safeScope = scope == null ? MaterialSearchScope.unscoped() : scope;
        if (safeScope.isNoResults()) {
            return List.of();
        }

        UUID[] scopedMaterialIds = safeScope.isMaterialIds()
            ? PostgresMaterialJdbcSupport.toUuidArray(safeScope.materialIds())
            : null;
        PostgresMaterialFilterSqlBuilder.RetrievalScopeSql scopeSql = retrievalScopeSql(safeScope);
        PostgresMaterialFilterSqlBuilder.SearchFilterSql filterSql = filterSqlBuilder.buildSearchFilterSql(
            safeScope.retrievalFilters()
        );
        try {
            return jdbcTemplate.query(
                """
                    SELECT
                        m.id AS material_id,
                        c.chunk_index,
                        m.title,
                        c.chunk_text,
                        c.page,
                        c.extractor,
                        c.ocr_used,
                        c.chunk_type,
                        c.embedding <=> ? AS semantic_distance,
                        NULL::DOUBLE PRECISION AS lexical_score
                    FROM material_chunks c
                    JOIN materials m ON m.id = c.material_id
                    WHERE """
                    + retrievalReadyPredicate(safeScope)
                    + """
                      AND c.embedding IS NOT NULL
                    """
                    + materialIdsPredicate(safeScope)
                    + scopeSql.sql()
                    + filterSql.sql()
                    + """
                    ORDER BY semantic_distance ASC, c.page NULLS LAST, c.chunk_index ASC
                    LIMIT ?
                    """,
                preparedStatement -> {
                    preparedStatement.setObject(1, new PGvector(queryEmbedding));
                    int parameterIndex = 2;
                    parameterIndex = bindMaterialIds(preparedStatement, parameterIndex, safeScope, scopedMaterialIds);
                    parameterIndex = filterSqlBuilder.bindRetrievalScope(preparedStatement, parameterIndex, scopeSql);
                    parameterIndex = filterSqlBuilder.bindSearchFilters(preparedStatement, parameterIndex, filterSql);
                    preparedStatement.setInt(parameterIndex, limit);
                },
                CHUNK_SEARCH_ROW_MAPPER
            );
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "material.vector_query_failed",
                "Unable to execute semantic retrieval",
                exception
            );
        }
    }

    List<MaterialChunkSearchMatch> search(String query, int limit) {
        return search(query, limit, MaterialSearchScope.unscoped());
    }

    List<MaterialChunkSearchMatch> search(String query, int limit, Set<String> allowedMaterialIds) {
        return search(query, limit, MaterialSearchScope.fromLegacyMaterialIds(allowedMaterialIds));
    }

    List<MaterialChunkSearchMatch> search(
        String query,
        int limit,
        Set<String> allowedMaterialIds,
        RetrievalFilters filters
    ) {
        return search(query, limit, MaterialSearchScope.fromLegacyMaterialIds(allowedMaterialIds, filters));
    }

    List<MaterialChunkSearchMatch> search(
        String query,
        int limit,
        MaterialSearchScope scope
    ) {
        if (query == null || query.isBlank() || limit <= 0) {
            return List.of();
        }
        MaterialSearchScope safeScope = scope == null ? MaterialSearchScope.unscoped() : scope;
        if (safeScope.isNoResults()) {
            return List.of();
        }

        UUID[] scopedMaterialIds = safeScope.isMaterialIds()
            ? PostgresMaterialJdbcSupport.toUuidArray(safeScope.materialIds())
            : null;
        PostgresMaterialFilterSqlBuilder.RetrievalScopeSql scopeSql = retrievalScopeSql(safeScope);
        PostgresMaterialFilterSqlBuilder.SearchFilterSql filterSql = filterSqlBuilder.buildSearchFilterSql(
            safeScope.retrievalFilters()
        );
        try {
            return jdbcTemplate.query(
                """
                    WITH query_term AS (
                        SELECT plainto_tsquery('simple', ?) AS q
                    )
                    SELECT
                        m.id AS material_id,
                        c.chunk_index,
                        m.title,
                        c.chunk_text,
                        c.page,
                        c.extractor,
                        c.ocr_used,
                        c.chunk_type,
                        NULL::DOUBLE PRECISION AS semantic_distance,
                        ts_rank_cd(c.search_vector, qt.q) AS lexical_score
                    FROM material_chunks c
                    JOIN materials m ON m.id = c.material_id
                    JOIN query_term qt ON TRUE
                    WHERE """
                    + retrievalReadyPredicate(safeScope)
                    + """
                      AND c.search_vector @@ qt.q
                    """
                    + materialIdsPredicate(safeScope)
                    + scopeSql.sql()
                    + filterSql.sql()
                    + """
                    ORDER BY lexical_score DESC, c.page NULLS LAST, c.chunk_index ASC
                    LIMIT ?
                    """,
                preparedStatement -> {
                    int parameterIndex = 1;
                    preparedStatement.setString(parameterIndex++, query);
                    parameterIndex = bindMaterialIds(preparedStatement, parameterIndex, safeScope, scopedMaterialIds);
                    parameterIndex = filterSqlBuilder.bindRetrievalScope(preparedStatement, parameterIndex, scopeSql);
                    parameterIndex = filterSqlBuilder.bindSearchFilters(preparedStatement, parameterIndex, filterSql);
                    preparedStatement.setInt(parameterIndex, limit);
                },
                CHUNK_SEARCH_ROW_MAPPER
            );
        } catch (DataAccessException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "material.lexical_query_failed",
                "Unable to execute lexical retrieval",
                exception
            );
        }
    }

    private String retrievalReadyPredicate(MaterialSearchScope scope) {
        return filterSqlBuilder.retrievalReadyPredicate(
            "m",
            scope.isFiltered() ? scope.knowledgeScope() : null,
            scope.retrievalFilters()
        );
    }

    private PostgresMaterialFilterSqlBuilder.RetrievalScopeSql retrievalScopeSql(MaterialSearchScope scope) {
        if (!scope.isFiltered()) {
            return PostgresMaterialFilterSqlBuilder.RetrievalScopeSql.empty();
        }
        return filterSqlBuilder.buildRetrievalScopeSql(
            scope.knowledgeScope(),
            scope.uploadedAfterInclusive(),
            scope.uploadedBeforeExclusive()
        );
    }

    private String materialIdsPredicate(MaterialSearchScope scope) {
        if (!scope.isMaterialIds()) {
            return "";
        }
        return "  AND m.id = ANY (?)\n";
    }

    private int bindMaterialIds(
        java.sql.PreparedStatement preparedStatement,
        int parameterIndex,
        MaterialSearchScope scope,
        UUID[] scopedMaterialIds
    ) throws java.sql.SQLException {
        if (!scope.isMaterialIds()) {
            return parameterIndex;
        }
        PostgresMaterialJdbcSupport.bindUuidArray(preparedStatement, parameterIndex++, scopedMaterialIds);
        return parameterIndex;
    }
}
