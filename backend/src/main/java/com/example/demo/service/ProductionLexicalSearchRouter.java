package com.example.demo.service;

import com.example.demo.infrastructure.material.LexicalProviderMode;
import com.example.demo.infrastructure.material.LexicalProviderType;
import com.example.demo.infrastructure.material.LexicalSearchProvider;
import com.example.demo.infrastructure.material.MaterialChunkSearchMatch;
import com.example.demo.model.RetrievalFilters;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ProductionLexicalSearchRouter {

    private static final Logger logger = LoggerFactory.getLogger(ProductionLexicalSearchRouter.class);

    private final LexicalSearchStrategy lexicalSearchStrategy;
    private final LexicalSearchModeResolver lexicalSearchModeResolver;
    private final ElasticsearchHealthService elasticsearchHealthService;

    public ProductionLexicalSearchRouter(
        LexicalSearchStrategy lexicalSearchStrategy,
        LexicalSearchModeResolver lexicalSearchModeResolver,
        ElasticsearchHealthService elasticsearchHealthService
    ) {
        this.lexicalSearchStrategy = lexicalSearchStrategy;
        this.lexicalSearchModeResolver = lexicalSearchModeResolver;
        this.elasticsearchHealthService = elasticsearchHealthService;

        if (lexicalSearchModeResolver.configuredMode() == LexicalProviderMode.ELASTICSEARCH) {
            lexicalSearchStrategy.resolve(LexicalProviderType.ELASTICSEARCH);
        }
    }

    public LexicalRoutingDecision currentDecision() {
        return currentDecision(elasticsearchHealthService.currentHealth());
    }

    public LexicalRoutingDecision currentDecisionWithRefresh() {
        return currentDecision(elasticsearchHealthService.refreshHealthSnapshotIfStale());
    }

    public LexicalRoutingDecision currentDecision(ElasticsearchHealthService.SearchSyncHealth searchHealth) {
        LexicalProviderMode configuredMode = lexicalSearchModeResolver.configuredMode();
        return switch (configuredMode) {
            case POSTGRES -> new LexicalRoutingDecision(
                configuredMode,
                LexicalProviderType.POSTGRES,
                false,
                searchHealth.reasonCode(),
                searchHealth.reasonMessage(),
                searchHealth
            );
            case ELASTICSEARCH -> {
                lexicalSearchStrategy.resolve(LexicalProviderType.ELASTICSEARCH);
                yield new LexicalRoutingDecision(
                    configuredMode,
                    LexicalProviderType.ELASTICSEARCH,
                    false,
                    searchHealth.reasonCode(),
                    searchHealth.reasonMessage(),
                    searchHealth
                );
            }
            case AUTO -> resolveAutoDecision(searchHealth);
        };
    }

    public LexicalSearchResult search(String query, int limit) {
        return search(query, limit, null);
    }

    public LexicalSearchResult search(String query, int limit, Set<String> allowedMaterialIds) {
        return search(query, limit, allowedMaterialIds, RetrievalFilters.empty());
    }

    public LexicalSearchResult search(String query, int limit, Set<String> allowedMaterialIds, RetrievalFilters filters) {
        LexicalRoutingDecision decision = currentDecision();
        if (query == null || query.isBlank() || limit <= 0) {
            return decision.toResult(List.of());
        }

        try {
            return decision.toResult(providerOf(decision.effectiveProvider()).search(query, limit, allowedMaterialIds, filters));
        } catch (RuntimeException exception) {
            ElasticsearchHealthService.SearchSyncHealth runtimeFailureHealth = decision.searchHealth();
            if (decision.effectiveProvider() == LexicalProviderType.ELASTICSEARCH) {
                runtimeFailureHealth = elasticsearchHealthService.recordRuntimeFailure(exception);
            }
            if (!decision.runtimeFallbackEligible()) {
                throw exception;
            }

            List<MaterialChunkSearchMatch> fallbackMatches = providerOf(LexicalProviderType.POSTGRES)
                .search(query, limit, allowedMaterialIds, filters);
            String fallbackReasonMessage = rootMessage(exception);
            logger.warn(
                "Production lexical runtime fallback: configuredMode={} from={} to={} reasonCode={} message={}",
                decision.configuredMode().propertyValue(),
                decision.effectiveProvider().propertyValue(),
                LexicalProviderType.POSTGRES.propertyValue(),
                "search.runtime_failure",
                fallbackReasonMessage,
                exception
            );
            return new LexicalSearchResult(
                decision.configuredMode(),
                LexicalProviderType.POSTGRES,
                true,
                "search.runtime_failure",
                "Elasticsearch query failed at runtime, falling back to PostgreSQL: " + fallbackReasonMessage,
                runtimeFailureHealth,
                fallbackMatches
            );
        }
    }

    private LexicalRoutingDecision resolveAutoDecision(ElasticsearchHealthService.SearchSyncHealth searchHealth) {
        if ("UP".equals(searchHealth.clusterStatus()) && lexicalSearchStrategy.find(LexicalProviderType.ELASTICSEARCH).isPresent()) {
            return new LexicalRoutingDecision(
                LexicalProviderMode.AUTO,
                LexicalProviderType.ELASTICSEARCH,
                false,
                searchHealth.reasonCode(),
                searchHealth.reasonMessage(),
                searchHealth
            );
        }

        String fallbackReasonCode = searchHealth.reasonCode();
        String fallbackReasonMessage = searchHealth.reasonMessage();
        if (fallbackReasonCode == null) {
            fallbackReasonCode = "search.provider_unavailable";
            fallbackReasonMessage = "Elasticsearch provider is not registered, using PostgreSQL fallback.";
        }
        return new LexicalRoutingDecision(
            LexicalProviderMode.AUTO,
            LexicalProviderType.POSTGRES,
            true,
            fallbackReasonCode,
            fallbackReasonMessage,
            searchHealth
        );
    }

    private LexicalSearchProvider providerOf(LexicalProviderType providerType) {
        return lexicalSearchStrategy.resolve(providerType);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    public record LexicalRoutingDecision(
        LexicalProviderMode configuredMode,
        LexicalProviderType effectiveProvider,
        boolean fallbackApplied,
        String fallbackReasonCode,
        String fallbackReasonMessage,
        ElasticsearchHealthService.SearchSyncHealth searchHealth
    ) {
        boolean runtimeFallbackEligible() {
            return configuredMode == LexicalProviderMode.AUTO && effectiveProvider == LexicalProviderType.ELASTICSEARCH;
        }

        LexicalSearchResult toResult(List<MaterialChunkSearchMatch> matches) {
            return new LexicalSearchResult(
                configuredMode,
                effectiveProvider,
                fallbackApplied,
                fallbackReasonCode,
                fallbackReasonMessage,
                searchHealth,
                matches
            );
        }
    }

    public record LexicalSearchResult(
        LexicalProviderMode configuredMode,
        LexicalProviderType effectiveProvider,
        boolean fallbackApplied,
        String fallbackReasonCode,
        String fallbackReasonMessage,
        ElasticsearchHealthService.SearchSyncHealth searchHealth,
        List<MaterialChunkSearchMatch> matches
    ) {
    }
}
