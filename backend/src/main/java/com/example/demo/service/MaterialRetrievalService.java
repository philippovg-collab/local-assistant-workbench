package com.example.demo.service;

import com.example.demo.service.material.DocumentBlockType;
import com.example.demo.service.material.LexicalProviderMode;
import com.example.demo.service.material.LexicalProviderType;
import com.example.demo.service.material.MaterialChunkSearchMatch;
import com.example.demo.service.material.MaterialRetrievalScopeSnapshot;
import com.example.demo.service.material.StoredMaterialChunk;
import com.example.demo.service.material.StoredMaterialRecord;
import com.example.demo.service.material.StoredMaterialSegment;
import com.example.demo.service.material.port.MaterialCatalogRepository;
import com.example.demo.service.material.port.MaterialChunkingRepository;
import com.example.demo.service.material.port.SemanticSearchRepository;

import com.example.demo.api.ApiException;
import com.example.demo.config.RolloutProperties;
import com.example.demo.config.RagProperties;
import com.example.demo.embedding.EmbeddingClient;
import com.example.demo.model.ChatSource;
import com.example.demo.model.KnowledgeScope;
import com.example.demo.model.MaterialMetadataSnapshot;
import com.example.demo.model.MaterialSearchDebug;
import com.example.demo.model.MaterialSearchHit;
import com.example.demo.model.MaterialSearchHitNeighbor;
import com.example.demo.model.MaterialSearchRequest;
import com.example.demo.model.MaterialSearchResponse;
import com.example.demo.model.QualityLayerFlags;
import com.example.demo.model.RetrievalDebug;
import com.example.demo.model.RetrievalFilters;
import com.example.demo.model.RetrievalQueryHints;
import com.example.demo.model.RetrievalTrace;
import com.example.demo.model.SourceTrustLevel;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class MaterialRetrievalService {

    private static final Logger logger = LoggerFactory.getLogger(MaterialRetrievalService.class);

    private final MaterialCatalogRepository catalogRepository;
    private final MaterialChunkingRepository chunkingRepository;
    private final SemanticSearchRepository semanticSearchRepository;
    private final ProductionLexicalSearchRouter productionLexicalSearchRouter;
    private final EmbeddingClient embeddingClient;
    private final RagProperties ragProperties;
    private final HybridChunkRanker hybridChunkRanker;
    private final ChunkReranker chunkReranker;
    private final MaterialContentSupport contentSupport;
    private final RetrievalQueryHintExtractor retrievalQueryHintExtractor;
    private final LexicalShadowComparisonService lexicalShadowComparisonService;
    private final AnswerModePostProcessor answerModePostProcessor;
    private final Map<RelevanceProfile, RelevancePolicy> relevancePolicies;
    private final RolloutProperties rolloutProperties;
    private final QualityLayerHealthService qualityLayerHealthService;

    @Autowired
    public MaterialRetrievalService(
        MaterialCatalogRepository catalogRepository,
        MaterialChunkingRepository chunkingRepository,
        SemanticSearchRepository semanticSearchRepository,
        ProductionLexicalSearchRouter productionLexicalSearchRouter,
        EmbeddingClient embeddingClient,
        RagProperties ragProperties,
        HybridChunkRanker hybridChunkRanker,
        ChunkReranker chunkReranker,
        MaterialContentSupport contentSupport,
        RetrievalQueryHintExtractor retrievalQueryHintExtractor,
        LexicalShadowComparisonService lexicalShadowComparisonService,
        AnswerModePostProcessor answerModePostProcessor,
        RolloutProperties rolloutProperties,
        QualityLayerHealthService qualityLayerHealthService
    ) {
        this.catalogRepository = catalogRepository;
        this.chunkingRepository = chunkingRepository;
        this.semanticSearchRepository = semanticSearchRepository;
        this.productionLexicalSearchRouter = productionLexicalSearchRouter;
        this.embeddingClient = embeddingClient;
        this.ragProperties = ragProperties;
        this.hybridChunkRanker = hybridChunkRanker;
        this.chunkReranker = chunkReranker;
        this.contentSupport = contentSupport;
        this.retrievalQueryHintExtractor = retrievalQueryHintExtractor;
        this.lexicalShadowComparisonService = lexicalShadowComparisonService;
        this.answerModePostProcessor = answerModePostProcessor;
        this.rolloutProperties = rolloutProperties == null ? new RolloutProperties() : rolloutProperties;
        this.qualityLayerHealthService = qualityLayerHealthService == null
            ? QualityLayerHealthService.noop(this.rolloutProperties)
            : qualityLayerHealthService;
        this.relevancePolicies = Map.of(
            RelevanceProfile.LEGACY, new LegacyRelevancePolicy(),
            RelevanceProfile.HYBRID_V1, new HybridV1RelevancePolicy(),
            RelevanceProfile.HYBRID_RERANK_V1, new HybridRerankV1RelevancePolicy()
        );
    }

    public MaterialRetrievalService(
        MaterialCatalogRepository catalogRepository,
        MaterialChunkingRepository chunkingRepository,
        SemanticSearchRepository semanticSearchRepository,
        ProductionLexicalSearchRouter productionLexicalSearchRouter,
        EmbeddingClient embeddingClient,
        RagProperties ragProperties,
        HybridChunkRanker hybridChunkRanker,
        MaterialContentSupport contentSupport,
        LexicalShadowComparisonService lexicalShadowComparisonService,
        AnswerModePostProcessor answerModePostProcessor
    ) {
        this(
            catalogRepository,
            chunkingRepository,
            semanticSearchRepository,
            productionLexicalSearchRouter,
            embeddingClient,
            ragProperties,
            hybridChunkRanker,
            new ChunkReranker(contentSupport),
            contentSupport,
            new RetrievalQueryHintExtractor(),
            lexicalShadowComparisonService,
            answerModePostProcessor,
            RolloutProperties.enabledForTests(),
            QualityLayerHealthService.noop(RolloutProperties.enabledForTests())
        );
    }

    public MaterialRetrievalService(
        MaterialCatalogRepository catalogRepository,
        SemanticSearchRepository semanticSearchRepository,
        ProductionLexicalSearchRouter productionLexicalSearchRouter,
        EmbeddingClient embeddingClient,
        RagProperties ragProperties,
        HybridChunkRanker hybridChunkRanker,
        MaterialContentSupport contentSupport,
        LexicalShadowComparisonService lexicalShadowComparisonService,
        AnswerModePostProcessor answerModePostProcessor
    ) {
        this(
            catalogRepository,
            resolveChunkingRepository(catalogRepository),
            semanticSearchRepository,
            productionLexicalSearchRouter,
            embeddingClient,
            ragProperties,
            hybridChunkRanker,
            new ChunkReranker(contentSupport),
            contentSupport,
            new RetrievalQueryHintExtractor(),
            lexicalShadowComparisonService,
            answerModePostProcessor,
            RolloutProperties.enabledForTests(),
            QualityLayerHealthService.noop(RolloutProperties.enabledForTests())
        );
    }

    public MaterialRetrievalService(
        MaterialCatalogRepository catalogRepository,
        MaterialChunkingRepository chunkingRepository,
        ProductionLexicalSearchRouter productionLexicalSearchRouter,
        EmbeddingClient embeddingClient,
        RagProperties ragProperties,
        HybridChunkRanker hybridChunkRanker,
        MaterialContentSupport contentSupport,
        LexicalShadowComparisonService lexicalShadowComparisonService,
        AnswerModePostProcessor answerModePostProcessor
    ) {
        this(
            catalogRepository,
            chunkingRepository,
            resolveSemanticSearchRepository(catalogRepository),
            productionLexicalSearchRouter,
            embeddingClient,
            ragProperties,
            hybridChunkRanker,
            new ChunkReranker(contentSupport),
            contentSupport,
            new RetrievalQueryHintExtractor(),
            lexicalShadowComparisonService,
            answerModePostProcessor,
            RolloutProperties.enabledForTests(),
            QualityLayerHealthService.noop(RolloutProperties.enabledForTests())
        );
    }

    public MaterialRetrievalService(
        MaterialCatalogRepository catalogRepository,
        MaterialChunkingRepository chunkingRepository,
        ProductionLexicalSearchRouter productionLexicalSearchRouter,
        EmbeddingClient embeddingClient,
        RagProperties ragProperties,
        HybridChunkRanker hybridChunkRanker,
        MaterialContentSupport contentSupport
    ) {
        this(
            catalogRepository,
            chunkingRepository,
            resolveSemanticSearchRepository(catalogRepository),
            productionLexicalSearchRouter,
            embeddingClient,
            ragProperties,
            hybridChunkRanker,
            new ChunkReranker(contentSupport),
            contentSupport,
            new RetrievalQueryHintExtractor(),
            (query, productionProvider, productionMatches, limit) -> {
            },
            new AnswerModePostProcessor(contentSupport),
            RolloutProperties.enabledForTests(),
            QualityLayerHealthService.noop(RolloutProperties.enabledForTests())
        );
    }

    public MaterialRetrievalResult retrieveContext(String prompt) {
        return retrieveContext(prompt, KnowledgeScope.empty(), RetrievalFilters.empty(), List.of());
    }

    public MaterialRetrievalResult retrieveContext(String prompt, KnowledgeScope knowledgeScope) {
        return retrieveContext(prompt, knowledgeScope, RetrievalFilters.empty(), List.of());
    }

    public MaterialRetrievalResult retrieveContext(
        String prompt,
        KnowledgeScope knowledgeScope,
        RetrievalFilters retrievalFilters
    ) {
        return retrieveContext(prompt, knowledgeScope, retrievalFilters, List.of());
    }

    public MaterialRetrievalResult retrieveContext(
        String prompt,
        KnowledgeScope knowledgeScope,
        RetrievalFilters retrievalFilters,
        List<String> dismissedRetrievalHintKeys
    ) {
        return executeSearch(
            prompt,
            knowledgeScope,
            retrievalFilters,
            dismissedRetrievalHintKeys,
            ragProperties.getFinalContextLimit(),
            true
        ).toMaterialRetrievalResult();
    }

    public MaterialSearchResponse search(MaterialSearchRequest request) {
        if (!rolloutProperties.isSearchApiV1()) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                "search.api_disabled",
                "Search API is disabled by rollout."
            );
        }
        if (request == null || request.query() == null || request.query().isBlank()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "search.invalid_query",
                "Field 'query' is required"
            );
        }

        int limit = normalizeSearchLimit(request.limit());
        SearchExecution execution = executeSearch(
            request.query(),
            KnowledgeScope.empty(),
            request.filters(),
            List.of(),
            limit,
            false
        );
        List<MaterialSearchHit> hits = buildSearchHits(
            execution,
            request.includeNeighborsOrDefault()
        );
        MaterialSearchDebug debug = request.debugOrDefault()
            ? new MaterialSearchDebug(
                execution.effectiveFilters(),
                execution.manualFilters(),
                execution.effectiveFilters(),
                execution.queryHints(),
                execution.semanticMatches().size(),
                execution.lexicalSearchResult().matches().size(),
                execution.rerankCandidateCount(),
                execution.rankedMatches().size(),
                execution.lexicalSearchResult().configuredMode().propertyValue(),
                execution.lexicalSearchResult().effectiveProvider().propertyValue(),
                execution.lexicalSearchResult().fallbackApplied(),
                execution.lexicalSearchResult().fallbackReasonCode(),
                execution.retrievalTrace().supportVerdict(),
                execution.relevanceProfile() == RelevanceProfile.HYBRID_RERANK_V1,
                execution.relevanceProfile() == RelevanceProfile.HYBRID_RERANK_V1
                    ? RelevanceProfile.HYBRID_RERANK_V1.propertyValue()
                    : null,
                execution.relevanceProfile().propertyValue(),
                execution.activeRolloutFlags(),
                withCapability(execution.appliedCapabilities(), "search-api-v1"),
                execution.suppressedCapabilities()
            )
            : null;

        return new MaterialSearchResponse(
            request.query().trim(),
            hits,
            debug
        );
    }

    private SearchExecution executeSearch(
        String prompt,
        KnowledgeScope knowledgeScope,
        RetrievalFilters retrievalFilters,
        List<String> dismissedRetrievalHintKeys,
        int finalLimit,
        boolean recordWindow
    ) {
        Set<String> queryTokens = contentSupport.tokenize(prompt);
        if (queryTokens.isEmpty()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "chat.invalid_prompt",
                "Prompt must contain at least one alphanumeric token"
            );
        }

        RetrievalFilters safeFilters = retrievalFilters == null ? RetrievalFilters.empty() : retrievalFilters;
        QualityLayerFlags activeRolloutFlags = qualityLayerHealthService.flags();
        RetrievalFilters effectiveFilters = safeFilters;
        RetrievalQueryHints queryHints = RetrievalQueryHints.empty();
        List<String> appliedCapabilities = new ArrayList<>();
        List<String> suppressedCapabilities = new ArrayList<>();
        boolean metadataFiltersEnabled = rolloutProperties.isMetadataFiltersV1();
        if (!metadataFiltersEnabled && !safeFilters.isEmpty()) {
            logSuppressedCapability("metadata-filters-v1", "manual retrieval filters");
            suppressedCapabilities.add("metadata-filters-v1");
            effectiveFilters = RetrievalFilters.empty();
        }
        if (metadataFiltersEnabled && !effectiveFilters.isEmpty()) {
            appliedCapabilities.add("metadata-filters-v1");
        }
        boolean queryHintsEnabled = metadataFiltersEnabled && rolloutProperties.isQueryHintsV1();
        if (queryHintsEnabled) {
            queryHints = retrievalQueryHintExtractor.extract(prompt)
                .withoutDismissedFilterKeys(dismissedRetrievalHintKeys);
            if (!queryHints.equals(RetrievalQueryHints.empty())) {
                appliedCapabilities.add("query-hints-v1");
            }
            effectiveFilters = effectiveFilters.mergeMissing(queryHints.toRetrievalFilters());
        } else if (rolloutProperties.isQueryHintsV1() && !metadataFiltersEnabled) {
            logSuppressedCapability("query-hints-v1", "metadata filters rollout is disabled");
            suppressedCapabilities.add("query-hints-v1");
        }
        KnowledgeScope effectiveScope = knowledgeScope == null ? KnowledgeScope.empty() : knowledgeScope;
        RelevanceProfile relevanceProfile = configuredRelevancePolicy().profile();
        ZoneId currentZone = ZoneId.systemDefault();
        Instant uploadedAfterInclusive = null;
        Instant uploadedBeforeExclusive = null;
        if (effectiveScope.uploadedTodayOnly()) {
            java.time.LocalDate today = java.time.LocalDate.now(currentZone);
            uploadedAfterInclusive = today.atStartOfDay(currentZone).toInstant();
            uploadedBeforeExclusive = today.plusDays(1).atStartOfDay(currentZone).toInstant();
        }
        MaterialRetrievalScopeSnapshot scopeSnapshot = catalogRepository.describeRetrievalScope(
            effectiveScope,
            effectiveFilters,
            uploadedAfterInclusive,
            uploadedBeforeExclusive
        );
        int materialCount = scopeSnapshot.materialCount();
        int activeMaterialCount = scopeSnapshot.activeMaterialCount();
        int readyMaterialCount = scopeSnapshot.readyMaterialCount();
        int scopedMaterialCount = scopeSnapshot.scopedMaterialCount();
        int scopedActiveMaterialCount = scopeSnapshot.scopedActiveMaterialCount();
        int scopedReadyMaterialCount = scopeSnapshot.scopedReadyMaterialCount();
        Set<String> scopedReadyMaterialIds = scopeSnapshot.scopedReadyMaterialIds();

        if (materialCount == 0 || activeMaterialCount == 0 || readyMaterialCount == 0
            || scopedMaterialCount == 0 || scopedActiveMaterialCount == 0 || scopedReadyMaterialCount == 0) {
            RetrievalTrace trace = new RetrievalTrace(
                materialCount,
                activeMaterialCount,
                readyMaterialCount,
                scopedMaterialCount,
                scopedActiveMaterialCount,
                scopedReadyMaterialCount,
                0,
                0,
                0,
                "none"
            );
            if (recordWindow) {
                qualityLayerHealthService.recordRetrieval(List.of(), true, false, false, false, false);
            }
            return new SearchExecution(
                queryTokens,
                safeFilters,
                effectiveFilters,
                queryHints,
                materialCount,
                activeMaterialCount,
                readyMaterialCount,
                scopedMaterialCount,
                scopedActiveMaterialCount,
                scopedReadyMaterialCount,
                List.of(),
                emptyLexicalResult(),
                List.of(),
                0,
                List.of(),
                Map.of(),
                trace,
                new RetrievalDebug(
                    queryHints,
                    safeFilters,
                    effectiveFilters,
                    0,
                    0,
                    0,
                    0,
                    trace.supportVerdict(),
                    relevanceProfile.propertyValue(),
                    activeRolloutFlags,
                    appliedCapabilities,
                    suppressedCapabilities
                ),
                relevanceProfile,
                activeRolloutFlags,
                appliedCapabilities,
                suppressedCapabilities
            );
        }

        List<MaterialChunkSearchMatch> semanticMatches = semanticSearchRepository.searchSemantic(
            embeddingClient.embed(prompt.trim()),
            ragProperties.getSemanticCandidateLimit(),
            scopedReadyMaterialIds,
            effectiveFilters
        );
        ProductionLexicalSearchRouter.LexicalSearchResult lexicalSearchResult = productionLexicalSearchRouter.search(
            prompt,
            ragProperties.getLexicalCandidateLimit(),
            scopedReadyMaterialIds,
            effectiveFilters
        );
        List<MaterialChunkSearchMatch> lexicalMatches = lexicalSearchResult.matches();
        lexicalShadowComparisonService.compareIfEligible(
            prompt,
            lexicalSearchResult.effectiveProvider(),
            lexicalMatches,
            ragProperties.getLexicalCandidateLimit()
        );
        int rerankPoolLimit = relevanceProfile == RelevanceProfile.HYBRID_RERANK_V1
            ? Math.max(finalLimit, ragProperties.getRerankCandidateLimit())
            : finalLimit;
        List<HybridChunkRanker.RankedChunk> rankedMatches = configuredRelevancePolicy().filterRankedMatches(
            semanticMatches,
            lexicalMatches,
            hybridChunkRanker.fuse(semanticMatches, lexicalMatches, rerankPoolLimit),
            ragProperties
        );
        List<HybridChunkRanker.RankedChunk> preRerankMatches = List.copyOf(rankedMatches);
        int rerankCandidateCount = rankedMatches.size();
        Map<String, StoredMaterialRecord> candidateRecordsById = loadRecordsByMaterialId(rankedMatches);
        if (relevanceProfile == RelevanceProfile.HYBRID_RERANK_V1) {
            Map<String, List<StoredMaterialChunk>> chunksByMaterialId = loadChunksByMaterialId(rankedMatches);
            rankedMatches = chunkReranker.rerank(
                prompt,
                queryHints,
                effectiveFilters,
                rankedMatches,
                candidateRecordsById,
                chunksByMaterialId,
                finalLimit
            );
            appliedCapabilities.add("reranker-v1");
        }

        logger.info(
            "RAG retrieval: materials={} activeMaterials={} readyMaterials={} semanticMatches={} lexicalMatches={} configuredMode={} lexicalProvider={} fallbackApplied={} fallbackReasonCode={} fusedMatches={} scoring={} top={}",
            materialCount,
            activeMaterialCount,
            readyMaterialCount,
            semanticMatches.size(),
            lexicalMatches.size(),
            lexicalSearchResult.configuredMode().propertyValue(),
            lexicalSearchResult.effectiveProvider().propertyValue(),
            lexicalSearchResult.fallbackApplied(),
            lexicalSearchResult.fallbackReasonCode(),
            rankedMatches.size(),
            relevanceProfile.propertyValue(),
            rankedMatches.stream()
                .map(chunk -> "%s[p%s]=%d".formatted(
                    chunk.match().title(),
                    chunk.match().page() == null ? "-" : chunk.match().page(),
                    chunk.score()
                ))
                .toList()
        );

        List<RetrievedMaterialChunk> matches = rankedMatches.stream()
            .map(chunk -> new RetrievedMaterialChunk(
                chunk.match().chunkText(),
                buildChatSource(chunk, queryTokens, candidateRecordsById.get(chunk.match().materialId()))
            ))
            .toList();

        String supportVerdict = supportVerdictOf(semanticMatches, lexicalMatches, rankedMatches);
        RetrievalTrace trace = new RetrievalTrace(
            materialCount,
            activeMaterialCount,
            readyMaterialCount,
            scopedMaterialCount,
            scopedActiveMaterialCount,
            scopedReadyMaterialCount,
            semanticMatches.size(),
            lexicalMatches.size(),
            matches.size(),
            supportVerdict
        );
        if (recordWindow) {
            recordRetrievalWindow(matches, candidateRecordsById, preRerankMatches, rankedMatches);
        }
        return new SearchExecution(
            queryTokens,
            safeFilters,
            effectiveFilters,
            queryHints,
            materialCount,
            activeMaterialCount,
            readyMaterialCount,
            scopedMaterialCount,
            scopedActiveMaterialCount,
            scopedReadyMaterialCount,
            semanticMatches,
            lexicalSearchResult,
            rankedMatches,
            rerankCandidateCount,
            matches,
            candidateRecordsById,
            trace,
            new RetrievalDebug(
                queryHints,
                safeFilters,
                effectiveFilters,
                semanticMatches.size(),
                lexicalMatches.size(),
                rerankCandidateCount,
                matches.size(),
                supportVerdict,
                relevanceProfile.propertyValue(),
                activeRolloutFlags,
                appliedCapabilities,
                suppressedCapabilities
            ),
            relevanceProfile,
            activeRolloutFlags,
            appliedCapabilities,
            suppressedCapabilities
        );
    }

    private List<MaterialSearchHit> buildSearchHits(SearchExecution execution, boolean includeNeighbors) {
        Map<String, List<StoredMaterialChunk>> chunksByMaterialId = includeNeighbors ? new LinkedHashMap<>() : Map.of();
        List<MaterialSearchHit> hits = new ArrayList<>();
        for (HybridChunkRanker.RankedChunk rankedChunk : execution.rankedMatches()) {
            MaterialChunkSearchMatch match = rankedChunk.match();
            StoredMaterialRecord record = execution.recordsById().get(match.materialId());
            MaterialMetadataSnapshot metadata = record == null ? MaterialMetadataSnapshot.empty() : record.metadata();
            List<MaterialSearchHitNeighbor> neighbors = includeNeighbors
                ? neighborsFor(match.materialId(), match.chunkIndex(), chunksByMaterialId)
                : null;
            hits.add(new MaterialSearchHit(
                match.materialId(),
                match.materialId() + ":" + match.chunkIndex(),
                match.title(),
                match.chunkText(),
                match.chunkIndex(),
                match.page(),
                match.chunkType(),
                rankedChunk.score(),
                match.semanticDistance(),
                match.lexicalScore(),
                matchedTerms(execution.queryTokens(), match),
                answerModePostProcessor.buildOpenSourceUrl(
                    match.materialId(),
                    match.materialId() + ":" + match.chunkIndex(),
                    match.chunkIndex(),
                    match.page()
                ),
                metadata,
                neighbors,
                execution.relevanceProfile() == RelevanceProfile.HYBRID_RERANK_V1 ? rankedChunk.scoreBreakdown() : null
            ));
        }
        return List.copyOf(hits);
    }

    private List<MaterialSearchHitNeighbor> neighborsFor(
        String materialId,
        int chunkIndex,
        Map<String, List<StoredMaterialChunk>> chunksByMaterialId
    ) {
        List<StoredMaterialChunk> chunks = chunksByMaterialId.computeIfAbsent(
            materialId,
            chunkingRepository::findChunks
        );
        if (chunks.isEmpty()) {
            return List.of();
        }

        List<MaterialSearchHitNeighbor> neighbors = new ArrayList<>();
        chunks.stream()
            .filter(chunk -> chunk.index() == chunkIndex - 1)
            .findFirst()
            .ifPresent(chunk -> neighbors.add(toNeighbor(materialId, chunk)));
        chunks.stream()
            .filter(chunk -> chunk.index() == chunkIndex + 1)
            .findFirst()
            .ifPresent(chunk -> neighbors.add(toNeighbor(materialId, chunk)));
        return neighbors.isEmpty() ? List.of() : List.copyOf(neighbors);
    }

    private MaterialSearchHitNeighbor toNeighbor(String materialId, StoredMaterialChunk chunk) {
        return new MaterialSearchHitNeighbor(
            materialId + ":" + chunk.index(),
            chunk.index(),
            chunk.text(),
            chunk.page(),
            chunk.chunkType()
        );
    }

    private ChatSource buildChatSource(
        HybridChunkRanker.RankedChunk rankedChunk,
        Set<String> queryTokens,
        StoredMaterialRecord record
    ) {
        MaterialChunkSearchMatch match = rankedChunk.match();
        MaterialMetadataSnapshot metadata = record == null ? MaterialMetadataSnapshot.empty() : record.metadata();
        return new ChatSource(
            match.materialId(),
            match.materialId() + ":" + match.chunkIndex(),
            match.title(),
            contentSupport.clip(match.chunkText(), 280),
            rankedChunk.score(),
            confidenceOf(match, rankedChunk.score()),
            matchedTerms(queryTokens, match),
            answerModePostProcessor.buildOpenSourceUrl(
                match.materialId(),
                match.materialId() + ":" + match.chunkIndex(),
                match.chunkIndex(),
                match.page()
            ),
            match.chunkIndex(),
            match.page(),
            match.extractor(),
            match.ocrUsed(),
            match.chunkType(),
            metadata,
            match.semanticDistance(),
            match.lexicalScore(),
            rankedChunk.scoreBreakdown()
        );
    }

    private ProductionLexicalSearchRouter.LexicalSearchResult emptyLexicalResult() {
        ProductionLexicalSearchRouter.LexicalRoutingDecision decision = productionLexicalSearchRouter.currentDecision();
        if (decision == null) {
            return new ProductionLexicalSearchRouter.LexicalSearchResult(
                LexicalProviderMode.POSTGRES,
                LexicalProviderType.POSTGRES,
                false,
                "search.sync_disabled",
                "Elasticsearch search sync is disabled by configuration.",
                null,
                List.of()
            );
        }
        return new ProductionLexicalSearchRouter.LexicalSearchResult(
            decision.configuredMode(),
            decision.effectiveProvider(),
            decision.fallbackApplied(),
            decision.fallbackReasonCode(),
            decision.fallbackReasonMessage(),
            decision.searchHealth(),
            List.of()
        );
    }

    private int normalizeSearchLimit(Integer limit) {
        if (limit == null) {
            return ragProperties.getFinalContextLimit();
        }
        if (limit <= 0) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "search.invalid_limit",
                "Field 'limit' must be greater than zero"
            );
        }
        int maxSearchLimit = Math.max(1, ragProperties.getMaxSearchLimit());
        if (limit > maxSearchLimit) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "search.limit_too_large",
                "Field 'limit' must not exceed " + maxSearchLimit
            );
        }
        return limit;
    }

    private RelevancePolicy configuredRelevancePolicy() {
        RelevanceProfile profile = configuredRelevanceProfile();
        RelevancePolicy policy = relevancePolicies.get(profile);
        if (policy == null) {
            throw new IllegalArgumentException(
                "No relevance policy registered for profile '" + profile.propertyValue() + "'."
            );
        }
        return policy;
    }

    private RelevanceProfile configuredRelevanceProfile() {
        RelevanceProfile configuredProfile = RelevanceProfile.fromProperty(ragProperties.getRelevanceProfile());
        if (!rolloutProperties.isRerankerV1() && configuredProfile == RelevanceProfile.HYBRID_RERANK_V1) {
            logSuppressedCapability("reranker-v1", "relevance profile forced to hybrid-v1");
            return RelevanceProfile.HYBRID_V1;
        }
        return configuredProfile;
    }

    private Map<String, List<StoredMaterialChunk>> loadChunksByMaterialId(List<HybridChunkRanker.RankedChunk> rankedMatches) {
        Map<String, List<StoredMaterialChunk>> chunksByMaterialId = new LinkedHashMap<>();
        for (HybridChunkRanker.RankedChunk rankedChunk : rankedMatches) {
            chunksByMaterialId.computeIfAbsent(
                rankedChunk.match().materialId(),
                chunkingRepository::findChunks
            );
        }
        return chunksByMaterialId;
    }

    private Map<String, StoredMaterialRecord> loadRecordsByMaterialId(List<HybridChunkRanker.RankedChunk> rankedMatches) {
        if (rankedMatches == null || rankedMatches.isEmpty()) {
            return Map.of();
        }
        List<String> materialIds = rankedMatches.stream()
            .map(rankedChunk -> rankedChunk.match().materialId())
            .distinct()
            .toList();
        List<StoredMaterialRecord> records = catalogRepository.findByIds(materialIds);
        if (records == null) {
            records = materialIds.stream()
                .map(catalogRepository::findById)
                .flatMap(java.util.Optional::stream)
                .toList();
        }
        return records.stream()
            .collect(Collectors.toMap(
                StoredMaterialRecord::id,
                record -> record,
                (left, right) -> left,
                LinkedHashMap::new
            ));
    }

    private double confidenceOf(MaterialChunkSearchMatch match, int score) {
        double scoreConfidence = Math.max(0.0d, Math.min(1.0d, score / 100.0d));
        if (match.semanticDistance() == null) {
            return scoreConfidence;
        }
        double semanticConfidence = Math.max(0.0d, Math.min(1.0d, 1.0d - match.semanticDistance()));
        return Math.max(scoreConfidence, semanticConfidence);
    }

    private String supportVerdictOf(
        List<MaterialChunkSearchMatch> semanticMatches,
        List<MaterialChunkSearchMatch> lexicalMatches,
        List<HybridChunkRanker.RankedChunk> rankedMatches
    ) {
        if (rankedMatches == null || rankedMatches.isEmpty()) {
            return "none";
        }

        Set<String> semanticKeys = semanticMatches.stream().map(this::chunkKeyOf).collect(Collectors.toSet());
        Set<String> lexicalKeys = lexicalMatches.stream().map(this::chunkKeyOf).collect(Collectors.toSet());
        HybridChunkRanker.RankedChunk topMatch = rankedMatches.getFirst();
        String topKey = chunkKeyOf(topMatch.match());
        boolean corroboratedByBothSearches = semanticKeys.contains(topKey) && lexicalKeys.contains(topKey);

        if (corroboratedByBothSearches || topMatch.score() >= 70 || (rankedMatches.size() >= 2 && topMatch.score() >= 45)) {
            return "sufficient";
        }
        return "weak";
    }

    private String chunkKeyOf(MaterialChunkSearchMatch match) {
        return match.materialId() + ":" + match.chunkIndex();
    }

    private void recordRetrievalWindow(
        List<RetrievedMaterialChunk> matches,
        Map<String, StoredMaterialRecord> scopedReadyRecordsById,
        List<HybridChunkRanker.RankedChunk> preRerankMatches,
        List<HybridChunkRanker.RankedChunk> finalRankedMatches
    ) {
        HybridChunkRanker.RankedChunk preTop = preRerankMatches == null || preRerankMatches.isEmpty() ? null : preRerankMatches.getFirst();
        HybridChunkRanker.RankedChunk finalTop = finalRankedMatches == null || finalRankedMatches.isEmpty() ? null : finalRankedMatches.getFirst();
        boolean top1Changed = preTop != null && finalTop != null && !chunkKeyOf(preTop.match()).equals(chunkKeyOf(finalTop.match()));
        boolean top1Improved = top1Changed
            && finalTop.scoreBreakdown() != null
            && finalTop.scoreBreakdown().finalScore() > preTop.score();
        boolean appendixDemotion = top1Changed
            && preTop != null
            && (preTop.match().chunkType() == DocumentBlockType.APPENDIX
                || preTop.match().chunkType() == DocumentBlockType.CAPTION);
        boolean highTrustPromotion = top1Changed
            && trustLevelOf(scopedReadyRecordsById.get(finalTop == null ? null : finalTop.match().materialId())) == SourceTrustLevel.HIGH
            && trustLevelOf(scopedReadyRecordsById.get(preTop == null ? null : preTop.match().materialId())) != SourceTrustLevel.HIGH;
        List<DocumentBlockType> finalChunkTypes = matches == null
            ? List.of()
            : matches.stream()
                .map(RetrievedMaterialChunk::source)
                .map(ChatSource::chunkType)
                .toList();
        qualityLayerHealthService.recordRetrieval(
            finalChunkTypes,
            matches == null || matches.isEmpty(),
            top1Changed,
            top1Improved,
            appendixDemotion,
            highTrustPromotion
        );
    }

    private SourceTrustLevel trustLevelOf(StoredMaterialRecord record) {
        if (record == null || record.metadata() == null || record.metadata().sourceTrust() == null) {
            return SourceTrustLevel.UNKNOWN;
        }
        return record.metadata().sourceTrust();
    }

    private List<String> matchedTerms(Set<String> queryTokens, MaterialChunkSearchMatch match) {
        if (queryTokens == null || queryTokens.isEmpty()) {
            return List.of();
        }

        Set<String> contextTokens = contentSupport.tokenize(match.title() + "\n" + match.chunkText());
        return queryTokens.stream()
            .filter(contextTokens::contains)
            .limit(8)
            .toList();
    }

    private List<String> withCapability(List<String> appliedCapabilities, String capability) {
        List<String> safeCapabilities = appliedCapabilities == null ? List.of() : appliedCapabilities;
        if (safeCapabilities.contains(capability)) {
            return safeCapabilities;
        }
        List<String> extended = new ArrayList<>(safeCapabilities);
        extended.add(capability);
        return List.copyOf(extended);
    }

    private void logSuppressedCapability(String capability, String reason) {
        logger.info("Quality-layer capability suppressed: capability={} reason={}", capability, reason);
    }

    private record SearchExecution(
        Set<String> queryTokens,
        RetrievalFilters manualFilters,
        RetrievalFilters effectiveFilters,
        RetrievalQueryHints queryHints,
        int materialCount,
        int activeMaterialCount,
        int readyMaterialCount,
        int scopedMaterialCount,
        int scopedActiveMaterialCount,
        int scopedReadyMaterialCount,
        List<MaterialChunkSearchMatch> semanticMatches,
        ProductionLexicalSearchRouter.LexicalSearchResult lexicalSearchResult,
        List<HybridChunkRanker.RankedChunk> rankedMatches,
        int rerankCandidateCount,
        List<RetrievedMaterialChunk> matches,
        Map<String, StoredMaterialRecord> recordsById,
        RetrievalTrace retrievalTrace,
        RetrievalDebug retrievalDebug,
        RelevanceProfile relevanceProfile,
        QualityLayerFlags activeRolloutFlags,
        List<String> appliedCapabilities,
        List<String> suppressedCapabilities
    ) {
        private SearchExecution {
            queryTokens = queryTokens == null ? Set.of() : Set.copyOf(queryTokens);
            manualFilters = manualFilters == null ? RetrievalFilters.empty() : manualFilters;
            effectiveFilters = effectiveFilters == null ? RetrievalFilters.empty() : effectiveFilters;
            queryHints = queryHints == null ? RetrievalQueryHints.empty() : queryHints;
            semanticMatches = semanticMatches == null ? List.of() : List.copyOf(semanticMatches);
            rankedMatches = rankedMatches == null ? List.of() : List.copyOf(rankedMatches);
            matches = matches == null ? List.of() : List.copyOf(matches);
            recordsById = recordsById == null ? Map.of() : Map.copyOf(recordsById);
            activeRolloutFlags = activeRolloutFlags == null ? QualityLayerFlags.none() : activeRolloutFlags;
            appliedCapabilities = appliedCapabilities == null ? List.of() : List.copyOf(appliedCapabilities);
            suppressedCapabilities = suppressedCapabilities == null ? List.of() : List.copyOf(suppressedCapabilities);
            retrievalDebug = retrievalDebug == null
                ? new RetrievalDebug(
                    queryHints,
                    manualFilters,
                    effectiveFilters,
                    semanticMatches.size(),
                    lexicalSearchResult == null ? 0 : lexicalSearchResult.matches().size(),
                    rerankCandidateCount,
                    matches.size(),
                    retrievalTrace == null ? "none" : retrievalTrace.supportVerdict(),
                    relevanceProfile == null ? RelevanceProfile.LEGACY.propertyValue() : relevanceProfile.propertyValue(),
                    activeRolloutFlags,
                    appliedCapabilities,
                    suppressedCapabilities
                )
                : retrievalDebug;
            relevanceProfile = relevanceProfile == null ? RelevanceProfile.LEGACY : relevanceProfile;
        }

        private MaterialRetrievalResult toMaterialRetrievalResult() {
            return new MaterialRetrievalResult(
                materialCount,
                activeMaterialCount,
                readyMaterialCount,
                scopedMaterialCount,
                scopedActiveMaterialCount,
                scopedReadyMaterialCount,
                matches,
                retrievalTrace,
                retrievalDebug
            );
        }
    }

    private static MaterialChunkingRepository resolveChunkingRepository(MaterialCatalogRepository catalogRepository) {
        if (catalogRepository instanceof MaterialChunkingRepository chunkingRepository) {
            return chunkingRepository;
        }
        return new MaterialChunkingRepository() {
            @Override
            public List<StoredMaterialChunk> findChunks(String materialId) {
                return List.of();
            }

            @Override
            public List<StoredMaterialSegment> findSegments(String materialId) {
                return List.of();
            }

            @Override
            public String findChunkProfile(String materialId) {
                return "fixed-v1";
            }

            @Override
            public void replaceChunking(
                String materialId,
                String chunkProfile,
                List<StoredMaterialChunk> chunks,
                List<StoredMaterialSegment> segments,
                Instant updatedAt
            ) {
            }
        };
    }

    private static SemanticSearchRepository resolveSemanticSearchRepository(MaterialCatalogRepository catalogRepository) {
        if (catalogRepository instanceof SemanticSearchRepository semanticSearchRepository) {
            return semanticSearchRepository;
        }
        return new SemanticSearchRepository() {
            @Override
            public List<MaterialChunkSearchMatch> searchSemantic(float[] queryEmbedding, int limit) {
                return List.of();
            }
        };
    }

}
