package com.example.demo.infrastructure.material;

import com.example.demo.model.DocumentBlockType;
import com.example.demo.service.material.LexicalProviderType;
import com.example.demo.service.material.MaterialChunkSearchMatch;
import com.example.demo.service.material.MaterialSearchScope;
import com.example.demo.service.material.SearchableChunkDocument;
import com.example.demo.service.material.port.LexicalSearchProvider;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.indices.GetMappingResponse;
import co.elastic.clients.elasticsearch.indices.get_mapping.IndexMappingRecord;
import com.example.demo.error.ErrorType;
import com.example.demo.error.ProviderException;
import com.example.demo.config.SearchSyncProperties;
import com.example.demo.model.KnowledgeScope;
import com.example.demo.model.RetrievalFilters;
import com.example.demo.model.SourceTrustLevel;
import co.elastic.clients.json.JsonData;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "app.search-sync", name = "enabled", havingValue = "true")
public class ElasticsearchLexicalSearchProvider implements LexicalSearchProvider {

    private static final Set<String> REQUIRED_SCOPE_MAPPING_FIELDS = Set.of(
        "knowledgeDocumentClass",
        "createdAt",
        "workspaceKey",
        "documentType",
        "documentStatus",
        "projectKey",
        "documentNumber",
        "documentDate",
        "languageCode",
        "tags",
        "periodStart",
        "periodEnd",
        "department",
        "project",
        "counterparty",
        "businessStatus",
        "language",
        "sourceTrust"
    );

    private final ElasticsearchClient elasticsearchClient;
    private final SearchSyncProperties searchSyncProperties;
    private volatile boolean readAliasScopeMappingSupported;

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
        return search(query, limit, MaterialSearchScope.unscoped());
    }

    @Deprecated
    @Override
    public List<MaterialChunkSearchMatch> search(String query, int limit, Set<String> allowedMaterialIds) {
        return search(query, limit, MaterialSearchScope.fromLegacyMaterialIds(allowedMaterialIds));
    }

    @Deprecated
    @Override
    public List<MaterialChunkSearchMatch> search(
        String query,
        int limit,
        Set<String> allowedMaterialIds,
        RetrievalFilters filters
    ) {
        return search(query, limit, MaterialSearchScope.fromLegacyMaterialIds(allowedMaterialIds, filters));
    }

    @Override
    public List<MaterialChunkSearchMatch> search(String query, int limit, MaterialSearchScope scope) {
        if (query == null || query.isBlank() || limit <= 0) {
            return List.of();
        }
        MaterialSearchScope safeScope = scope == null ? MaterialSearchScope.unscoped() : scope;
        if (safeScope.isNoResults()) {
            return List.of();
        }

        try {
            assertScopeMappingSupported(safeScope);
            Query queryDsl = buildQuery(query, safeScope);
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
        } catch (ProviderException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new ProviderException(
                ErrorType.INTERNAL,
                "material.elasticsearch_query_failed",
                "Unable to execute Elasticsearch lexical retrieval",
                exception
            );
        } catch (RuntimeException exception) {
            throw new ProviderException(
                ErrorType.INTERNAL,
                "material.elasticsearch_query_failed",
                "Unable to execute Elasticsearch lexical retrieval",
                exception
            );
        }
    }

    private Query buildQuery(String query, MaterialSearchScope scope) {
        List<Query> filterClauses = new ArrayList<>();
        if (scope.isMaterialIds()) {
            filterClauses.add(Query.of(root -> root.terms(terms -> terms
                .field("materialId")
                .terms(values -> values.value(scope.materialIds().stream().map(FieldValue::of).toList()))
            )));
        }

        if (scope.isFiltered()) {
            addKnowledgeScopeFilters(
                filterClauses,
                scope.knowledgeScope(),
                scope.uploadedAfterInclusive(),
                scope.uploadedBeforeExclusive()
            );
        }
        addRetrievalFilterClauses(filterClauses, scope.retrievalFilters());

        return Query.of(root -> root.bool(bool -> {
            bool.should(strictClause(query));
            bool.should(fuzzyClause(query));
            bool.minimumShouldMatch("1");
            filterClauses.forEach(bool::filter);
            return bool;
        }));
    }

    private void addKnowledgeScopeFilters(
        List<Query> filterClauses,
        KnowledgeScope scope,
        Instant uploadedAfterInclusive,
        Instant uploadedBeforeExclusive
    ) {
        KnowledgeScope safeScope = scope == null ? KnowledgeScope.empty() : scope;
        if (!safeScope.documentClasses().isEmpty()) {
            filterClauses.add(termsFilter(
                "knowledgeDocumentClass",
                safeScope.documentClasses().stream().map(Enum::name).toList()
            ));
        }
        if (!safeScope.documentTypes().isEmpty()) {
            filterClauses.add(termsFilter("documentType", safeScope.documentTypes().stream().map(Enum::name).toList()));
        }
        if (!safeScope.documentStatuses().isEmpty()) {
            filterClauses.add(termsFilter("documentStatus", safeScope.documentStatuses().stream().map(Enum::name).toList()));
        }
        if (!safeScope.projectKeys().isEmpty()) {
            filterClauses.add(termsFilter("projectKey", safeScope.projectKeys()));
        }
        if (safeScope.documentNumber() != null) {
            filterClauses.add(exactFilter("documentNumber", safeScope.documentNumber()));
        }
        if (!safeScope.languageCodes().isEmpty()) {
            filterClauses.add(termsFilter("languageCode", safeScope.languageCodes().stream().map(Enum::name).toList()));
        }
        if (!safeScope.tags().isEmpty()) {
            filterClauses.add(termsFilter("tags", safeScope.tags()));
        }
        if (safeScope.workspaceKey() != null) {
            filterClauses.add(exactFilter("workspaceKey", safeScope.workspaceKey()));
        }
        addDateRangeFilters(filterClauses, "periodStart", safeScope.periodStartFrom(), safeScope.periodStartTo());
        addDateRangeFilters(filterClauses, "periodEnd", safeScope.periodEndFrom(), safeScope.periodEndTo());
        addInstantRangeFilters(filterClauses, "createdAt", uploadedAfterInclusive, uploadedBeforeExclusive);
    }

    private void addRetrievalFilterClauses(List<Query> filterClauses, RetrievalFilters filters) {
        RetrievalFilters safeFilters = filters == null ? RetrievalFilters.empty() : filters;
        if (safeFilters.documentNumber() != null) {
            filterClauses.add(exactFilter("documentNumber", safeFilters.documentNumber()));
        }
        if (!safeFilters.documentTypeNames().isEmpty()) {
            filterClauses.add(termsFilter("documentType", safeFilters.documentTypeNames()));
        }
        if (!safeFilters.documentStatusNames().isEmpty()) {
            filterClauses.add(termsFilter("documentStatus", safeFilters.documentStatusNames()));
        }
        if (!safeFilters.lowerCaseProjectKeys().isEmpty()) {
            filterClauses.add(termsFilter("projectKey", safeFilters.lowerCaseProjectKeys()));
        }
        if (!safeFilters.languageCodeNames().isEmpty()) {
            filterClauses.add(termsFilter("languageCode", safeFilters.languageCodeNames()));
        }
        if (safeFilters.periodStartFrom() != null) {
            filterClauses.add(Query.of(root -> root.range(range -> range
                .field("periodStart")
                .gte(JsonData.of(safeFilters.periodStartFrom().toString()))
            )));
        }
        if (safeFilters.periodStartTo() != null) {
            filterClauses.add(Query.of(root -> root.range(range -> range
                .field("periodStart")
                .lte(JsonData.of(safeFilters.periodStartTo().toString()))
            )));
        }
        if (safeFilters.periodEndFrom() != null) {
            filterClauses.add(Query.of(root -> root.range(range -> range
                .field("periodEnd")
                .gte(JsonData.of(safeFilters.periodEndFrom().toString()))
            )));
        }
        if (safeFilters.periodEndTo() != null) {
            filterClauses.add(Query.of(root -> root.range(range -> range
                .field("periodEnd")
                .lte(JsonData.of(safeFilters.periodEndTo().toString()))
            )));
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
            filterClauses.add(projectTextFilter(safeFilters.project()));
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

    private Query projectTextFilter(String value) {
        return Query.of(root -> root.bool(bool -> bool
            .should(exactFilter("project", value))
            .should(exactFilter("projectKey", value))
            .minimumShouldMatch("1")
        ));
    }

    private Query termsFilter(String field, List<String> values) {
        return Query.of(root -> root.terms(terms -> terms
            .field(field)
            .terms(queryValues -> queryValues.value(values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .map(FieldValue::of)
                .toList()))
        ));
    }

    private void addDateRangeFilters(List<Query> filterClauses, String field, LocalDate from, LocalDate to) {
        if (from != null) {
            filterClauses.add(Query.of(root -> root.range(range -> range
                .field(field)
                .gte(JsonData.of(from.toString()))
            )));
        }
        if (to != null) {
            filterClauses.add(Query.of(root -> root.range(range -> range
                .field(field)
                .lte(JsonData.of(to.toString()))
            )));
        }
    }

    private void addInstantRangeFilters(List<Query> filterClauses, String field, Instant from, Instant toExclusive) {
        if (from != null) {
            filterClauses.add(Query.of(root -> root.range(range -> range
                .field(field)
                .gte(JsonData.of(from.toString()))
            )));
        }
        if (toExclusive != null) {
            filterClauses.add(Query.of(root -> root.range(range -> range
                .field(field)
                .lt(JsonData.of(toExclusive.toString()))
            )));
        }
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

    private void assertScopeMappingSupported(MaterialSearchScope scope) throws IOException {
        if (!scope.requiresCriteriaFiltering() || readAliasScopeMappingSupported) {
            return;
        }

        GetMappingResponse response = elasticsearchClient.indices()
            .getMapping(request -> request.index(searchSyncProperties.readAlias()));
        boolean supported = !response.result().isEmpty()
            && response.result().values().stream().allMatch(this::mappingHasRequiredScopeFields);
        if (!supported) {
            throw new ProviderException(
                ErrorType.CONFLICT,
                "material.elasticsearch_scope_mapping_unsupported",
                "Elasticsearch read alias does not support scoped retrieval fields; rebuild and promote the v3 index or use PostgreSQL search."
            );
        }
        readAliasScopeMappingSupported = true;
    }

    private boolean mappingHasRequiredScopeFields(IndexMappingRecord record) {
        return record != null
            && record.mappings() != null
            && record.mappings().properties().keySet().containsAll(REQUIRED_SCOPE_MAPPING_FIELDS);
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
