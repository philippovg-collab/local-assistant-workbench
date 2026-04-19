package com.example.demo.infrastructure.material;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.example.demo.api.ApiException;
import com.example.demo.config.SearchSyncProperties;
import com.example.demo.model.RetrievalFilters;
import com.example.demo.model.SourceTrustLevel;
import co.elastic.clients.json.JsonData;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "app.search-sync", name = "enabled", havingValue = "true")
public class ElasticsearchLexicalSearchProvider implements LexicalSearchProvider {

    private final ElasticsearchClient elasticsearchClient;
    private final SearchSyncProperties searchSyncProperties;

    public ElasticsearchLexicalSearchProvider(
        ElasticsearchClient elasticsearchClient,
        SearchSyncProperties searchSyncProperties
    ) {
        this.elasticsearchClient = elasticsearchClient;
        this.searchSyncProperties = searchSyncProperties;
    }

    @Override
    public LexicalProviderType type() {
        return LexicalProviderType.ELASTICSEARCH;
    }

    @Override
    public List<MaterialChunkSearchMatch> search(String query, int limit) {
        return search(query, limit, null, RetrievalFilters.empty());
    }

    @Override
    public List<MaterialChunkSearchMatch> search(String query, int limit, java.util.Set<String> allowedMaterialIds) {
        return search(query, limit, allowedMaterialIds, RetrievalFilters.empty());
    }

    @Override
    public List<MaterialChunkSearchMatch> search(
        String query,
        int limit,
        java.util.Set<String> allowedMaterialIds,
        RetrievalFilters filters
    ) {
        if (query == null || query.isBlank() || limit <= 0) {
            return List.of();
        }

        try {
            Query queryDsl = buildQuery(query, allowedMaterialIds, filters);
            SearchResponse<SearchableChunkDocument> response = elasticsearchClient.search(search -> search
                    .index(searchSyncProperties.readAlias())
                    .size(limit)
                    .query(queryDsl),
                SearchableChunkDocument.class
            );

            return response.hits().hits().stream()
                .map(hit -> toMatch(hit.source(), hit.score()))
                .filter(match -> match != null)
                .toList();
        } catch (IOException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "material.elasticsearch_query_failed",
                "Unable to execute Elasticsearch lexical retrieval",
                exception
            );
        } catch (RuntimeException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "material.elasticsearch_query_failed",
                "Unable to execute Elasticsearch lexical retrieval",
                exception
            );
        }
    }

    private Query buildQuery(String query, java.util.Set<String> allowedMaterialIds, RetrievalFilters filters) {
        List<Query> filterClauses = new ArrayList<>();
        if (allowedMaterialIds != null && !allowedMaterialIds.isEmpty()) {
            filterClauses.add(Query.of(root -> root.terms(terms -> terms
                .field("materialId")
                .terms(values -> values.value(allowedMaterialIds.stream().map(FieldValue::of).toList()))
            )));
        }

        RetrievalFilters safeFilters = filters == null ? RetrievalFilters.empty() : filters;
        if (safeFilters.documentNumber() != null) {
            filterClauses.add(exactFilter("documentNumber", safeFilters.documentNumber()));
        }
        if (safeFilters.documentDateFrom() != null) {
            filterClauses.add(Query.of(root -> root.range(range -> range
                .field("documentDate")
                .gte(JsonData.of(safeFilters.documentDateFrom().toString()))
            )));
        }
        if (safeFilters.documentDateTo() != null) {
            filterClauses.add(Query.of(root -> root.range(range -> range
                .field("documentDate")
                .lte(JsonData.of(safeFilters.documentDateTo().toString()))
            )));
        }
        if (safeFilters.department() != null) {
            filterClauses.add(exactFilter("department", safeFilters.department()));
        }
        if (safeFilters.project() != null) {
            filterClauses.add(exactFilter("project", safeFilters.project()));
        }
        if (safeFilters.counterparty() != null) {
            filterClauses.add(exactFilter("counterparty", safeFilters.counterparty()));
        }
        if (safeFilters.businessStatus() != null) {
            filterClauses.add(exactFilter("businessStatus", safeFilters.businessStatus()));
        }
        if (safeFilters.language() != null) {
            filterClauses.add(exactFilter("language", safeFilters.language()));
        }
        if (!safeFilters.tags().isEmpty()) {
            filterClauses.add(Query.of(root -> root.terms(terms -> terms
                .field("tags")
                .terms(values -> values.value(safeFilters.lowerCaseTags().stream().map(FieldValue::of).toList()))
            )));
        }
        if (safeFilters.sourceTrustMin() != null) {
            filterClauses.add(sourceTrustThresholdFilter(safeFilters.sourceTrustMin()));
        }

        return Query.of(root -> root.bool(bool -> {
            bool.should(strictClause(query));
            bool.should(fuzzyClause(query));
            bool.minimumShouldMatch("1");
            filterClauses.forEach(bool::filter);
            return bool;
        }));
    }

    private co.elastic.clients.elasticsearch._types.query_dsl.Query strictClause(String query) {
        return co.elastic.clients.elasticsearch._types.query_dsl.Query.of(root -> root.multiMatch(multiMatch -> multiMatch
            .query(query)
            .fields("title^2", "chunkText")
            .operator(Operator.And)
        ));
    }

    private co.elastic.clients.elasticsearch._types.query_dsl.Query fuzzyClause(String query) {
        return co.elastic.clients.elasticsearch._types.query_dsl.Query.of(root -> root.multiMatch(multiMatch -> multiMatch
            .query(query)
            .fields("title^2", "chunkText")
            .fuzziness("AUTO")
            .prefixLength(1)
            .boost(0.35f)
        ));
    }

    private Query exactFilter(String field, String value) {
        return Query.of(root -> root.term(term -> term
            .field(field)
            .value(value.toLowerCase(Locale.ROOT))
        ));
    }

    private Query sourceTrustThresholdFilter(SourceTrustLevel minimumLevel) {
        return Query.of(root -> root.bool(bool -> {
            allowedSourceTrustLevels(minimumLevel).forEach(level ->
                bool.should(exactFilter("sourceTrust", level.name()))
            );
            bool.minimumShouldMatch("1");
            return bool;
        }));
    }

    private List<SourceTrustLevel> allowedSourceTrustLevels(SourceTrustLevel minimumLevel) {
        int threshold = RetrievalFilters.trustRank(minimumLevel);
        return java.util.Arrays.stream(SourceTrustLevel.values())
            .filter(level -> RetrievalFilters.trustRank(level) >= threshold)
            .toList();
    }

    private MaterialChunkSearchMatch toMatch(SearchableChunkDocument document, Double score) {
        if (document == null) {
            return null;
        }

        return new MaterialChunkSearchMatch(
            document.materialId(),
            chunkIndexOf(document.chunkId()),
            document.title(),
            document.chunkText(),
            document.page(),
            document.extractor(),
            document.ocrUsed(),
            document.chunkType() == null || document.chunkType().isBlank()
                ? DocumentBlockType.NARRATIVE
                : DocumentBlockType.valueOf(document.chunkType()),
            null,
            score == null ? 0.0d : score
        );
    }

    private int chunkIndexOf(String chunkId) {
        if (chunkId == null || chunkId.isBlank()) {
            throw new IllegalStateException("Elasticsearch searchable chunk document is missing chunkId");
        }

        int separator = chunkId.lastIndexOf(':');
        if (separator < 0 || separator == chunkId.length() - 1) {
            throw new IllegalStateException("Unable to parse chunk index from chunkId '" + chunkId + "'");
        }
        return Integer.parseInt(chunkId.substring(separator + 1));
    }
}
