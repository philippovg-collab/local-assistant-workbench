package com.example.demo.service.eval;

import com.example.demo.config.EmbeddingProperties;
import com.example.demo.config.RagProperties;
import com.example.demo.config.RolloutProperties;
import com.example.demo.config.SearchSyncProperties;
import com.example.demo.llmprovider.ActiveLlmProvider;
import com.example.demo.llmprovider.ActiveLlmProviderResolver;
import com.example.demo.model.eval.EvalRuntimeStateSnapshot;
import com.example.demo.service.ElasticsearchIndexManagementClient;
import com.example.demo.service.ElasticsearchMappingDefinition;
import com.example.demo.service.LexicalSearchStrategy;
import com.example.demo.service.material.LexicalProviderMode;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class EvalRuntimeStateService {

    private final RagProperties ragProperties;
    private final EmbeddingProperties embeddingProperties;
    private final RolloutProperties rolloutProperties;
    private final SearchSyncProperties searchSyncProperties;
    private final ActiveLlmProviderResolver activeProviderResolver;
    private final LexicalSearchStrategy lexicalSearchStrategy;
    private final ObjectProvider<ElasticsearchIndexManagementClient> indexManagementClientProvider;
    private final ElasticsearchMappingDefinition mappingDefinition;
    private final EvalHashService hashService;
    private final Clock clock;

    public EvalRuntimeStateService(
        RagProperties ragProperties,
        EmbeddingProperties embeddingProperties,
        RolloutProperties rolloutProperties,
        SearchSyncProperties searchSyncProperties,
        ActiveLlmProviderResolver activeProviderResolver,
        LexicalSearchStrategy lexicalSearchStrategy,
        ObjectProvider<ElasticsearchIndexManagementClient> indexManagementClientProvider,
        ElasticsearchMappingDefinition mappingDefinition,
        EvalHashService hashService,
        Clock clock
    ) {
        this.ragProperties = ragProperties;
        this.embeddingProperties = embeddingProperties;
        this.rolloutProperties = rolloutProperties;
        this.searchSyncProperties = searchSyncProperties;
        this.activeProviderResolver = activeProviderResolver;
        this.lexicalSearchStrategy = lexicalSearchStrategy;
        this.indexManagementClientProvider = indexManagementClientProvider;
        this.mappingDefinition = mappingDefinition;
        this.hashService = hashService;
        this.clock = clock;
    }

    public EvalRuntimeStateSnapshot capture() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("chatProvider", providerState(activeProviderResolver.resolveChatProvider(), false));
        state.put("embeddingProvider", providerState(activeProviderResolver.resolveEmbeddingProvider(), true));
        state.put("rag", ragState());
        state.put("lexical", lexicalState());
        state.put("rolloutFlags", rolloutState());
        state.put("searchSync", searchSyncState());
        state.put("elasticsearchAliases", elasticsearchAliasState());
        return new EvalRuntimeStateSnapshot(state, hashService.hash(state), clock.instant());
    }

    private Map<String, Object> providerState(ActiveLlmProvider provider, boolean embedding) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("id", provider.id());
        state.put("name", provider.name());
        state.put("providerType", provider.providerType().name());
        state.put("baseUrl", provider.baseUrl());
        state.put("fallback", provider.fallback());
        state.put("status", provider.status().name());
        state.put("defaultModel", provider.defaultModel());
        state.put("embeddingModel", embedding ? provider.embeddingModel() : null);
        state.put("expectedEmbeddingDimension", embedding ? provider.expectedEmbeddingDimension() : null);
        return state;
    }

    private Map<String, Object> ragState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("semanticCandidateLimit", ragProperties.getSemanticCandidateLimit());
        state.put("lexicalCandidateLimit", ragProperties.getLexicalCandidateLimit());
        state.put("finalContextLimit", ragProperties.getFinalContextLimit());
        state.put("maxSearchLimit", ragProperties.getMaxSearchLimit());
        state.put("rerankCandidateLimit", ragProperties.getRerankCandidateLimit());
        state.put("maxSemanticDistance", ragProperties.getMaxSemanticDistance());
        state.put("relevanceProfile", ragProperties.getRelevanceProfile());
        state.put("shadowEnabled", ragProperties.isShadowEnabled());
        state.put("shadowSamplePercent", ragProperties.getShadowSamplePercent());
        return state;
    }

    private Map<String, Object> lexicalState() {
        LexicalProviderMode configuredMode = LexicalProviderMode.fromProperty(ragProperties.getLexicalProvider());
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("configuredMode", configuredMode.propertyValue());
        state.put("effectiveProvider", lexicalSearchStrategy.defaultProviderType().propertyValue());
        return state;
    }

    private Map<String, Object> rolloutState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("metadataV1", rolloutProperties.isMetadataV1());
        state.put("structuredV1", rolloutProperties.isStructuredV1());
        state.put("metadataFiltersV1", rolloutProperties.isMetadataFiltersV1());
        state.put("searchApiV1", rolloutProperties.isSearchApiV1());
        state.put("rerankerV1", rolloutProperties.isRerankerV1());
        state.put("queryHintsV1", rolloutProperties.isQueryHintsV1());
        return state;
    }

    private Map<String, Object> searchSyncState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("enabled", searchSyncProperties.isEnabled());
        state.put("indexPrefix", searchSyncProperties.getIndexPrefix());
        state.put("indexVersion", searchSyncProperties.getIndexVersion());
        state.put("indexName", searchSyncProperties.indexName());
        state.put("readAlias", searchSyncProperties.readAlias());
        state.put("writeAlias", searchSyncProperties.writeAlias());
        state.put("mappingHash", mappingDefinition.mappingHash());
        state.put("mappingDynamic", mappingDefinition.dynamicMode());
        state.put("mappedFieldCount", mappingDefinition.mappedFields().size());
        state.put("embeddingModel", embeddingProperties.getModel());
        state.put("embeddingExpectedDimension", embeddingProperties.getExpectedDimension());
        return state;
    }

    private Map<String, Object> elasticsearchAliasState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("readAlias", searchSyncProperties.readAlias());
        state.put("writeAlias", searchSyncProperties.writeAlias());
        state.put("readTargets", List.of());
        state.put("writeTargets", List.of());
        if (!searchSyncProperties.isEnabled()) {
            state.put("lookupStatus", "disabled");
            return state;
        }

        ElasticsearchIndexManagementClient client = indexManagementClientProvider.getIfAvailable();
        if (client == null) {
            state.put("lookupStatus", "unavailable");
            return state;
        }

        try {
            state.put("readTargets", new TreeSet<>(client.aliasTargets(searchSyncProperties.readAlias())).stream().toList());
            state.put("writeTargets", new TreeSet<>(client.aliasTargets(searchSyncProperties.writeAlias())).stream().toList());
            state.put("lookupStatus", "available");
        } catch (RuntimeException exception) {
            state.put("lookupStatus", "unavailable");
            state.put("errorCode", exception.getClass().getSimpleName());
        }
        return state;
    }
}
