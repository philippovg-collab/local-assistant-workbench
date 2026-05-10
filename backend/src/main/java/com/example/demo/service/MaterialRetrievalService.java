package com.example.demo.service;

import com.example.demo.service.material.MaterialChunkSearchMatch;
import com.example.demo.service.material.MaterialRetrievalScopeSnapshot;
import com.example.demo.service.material.MaterialSearchScope;
import com.example.demo.service.material.StoredMaterialChunk;
import com.example.demo.service.material.StoredMaterialRecord;
import com.example.demo.service.material.port.MaterialCatalogRepository;
import com.example.demo.service.material.port.MaterialChunkingRepository;
import com.example.demo.service.material.port.SemanticSearchRepository;

import com.example.demo.error.ApplicationException;
import com.example.demo.error.ErrorType;
import com.example.demo.config.RolloutProperties;
import com.example.demo.config.RagProperties;
import com.example.demo.embedding.EmbeddingClient;
import com.example.demo.model.KnowledgeScope;
import com.example.demo.model.MaterialMetadataSnapshot;
import com.example.demo.model.MaterialSearchDebug;
import com.example.demo.model.MaterialSearchHit;
import com.example.demo.model.MaterialSearchRequest;
import com.example.demo.model.MaterialSearchResponse;
import com.example.demo.model.QualityLayerFlags;
import com.example.demo.model.RetrievalDebug;
import com.example.demo.model.RetrievalFilters;
import com.example.demo.model.RetrievalQueryHints;
import com.example.demo.model.RetrievalTrace;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final RolloutProperties rolloutProperties;
    private final QualityLayerHealthService qualityLayerHealthService;
    private final RetrievalResultMapper resultMapper;
    private final RetrievalFallbackPolicy fallbackPolicy;
    private final RetrievalWindowRecorder windowRecorder;
    private final RetrievalSearchResponseBuilder searchResponseBuilder;
    private final RetrievalRelevancePolicyResolver relevancePolicyResolver;
    private final RetrievalCandidateMaterialLoader candidateMaterialLoader;

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
        this.resultMapper = new RetrievalResultMapper(chunkingRepository, contentSupport, answerModePostProcessor);
        this.fallbackPolicy = new RetrievalFallbackPolicy(productionLexicalSearchRouter);
        this.windowRecorder = new RetrievalWindowRecorder(this.qualityLayerHealthService);
        this.searchResponseBuilder = new RetrievalSearchResponseBuilder(this.resultMapper);
        this.relevancePolicyResolver = new RetrievalRelevancePolicyResolver(ragProperties, this.rolloutProperties);
        this.candidateMaterialLoader = new RetrievalCandidateMaterialLoader(catalogRepository, chunkingRepository);
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
            throw new ApplicationException(
                ErrorType.CONFLICT,
                "search.api_disabled",
                "Search API is disabled by rollout."
            );
        }
        if (request == null || request.query() == null || request.query().isBlank()) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
                "search.invalid_query",
                "Field 'query' is required"
            );
        }

        int limit = fallbackPolicy.normalizeSearchLimit(
            request.limit(),
            ragProperties.getFinalContextLimit(),
            ragProperties.getMaxSearchLimit()
        );
        RetrievalSearchExecution execution = executeSearch(
            request.query(),
            KnowledgeScope.empty(),
            request.filters(),
            List.of(),
            limit,
            false
        );
        return searchResponseBuilder.build(request, execution);
    }

    private RetrievalSearchExecution executeSearch(
        String prompt,
        KnowledgeScope knowledgeScope,
        RetrievalFilters retrievalFilters,
        List<String> dismissedRetrievalHintKeys,
        int finalLimit,
        boolean recordWindow
    ) {
        Set<String> queryTokens = contentSupport.tokenize(prompt);
        if (queryTokens.isEmpty()) {
            throw new ApplicationException(
                ErrorType.INVALID_REQUEST,
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
        RelevancePolicy relevancePolicy = relevancePolicyResolver.configuredRelevancePolicy();
        RelevanceProfile relevanceProfile = relevancePolicy.profile();
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
        MaterialSearchScope searchScope = MaterialSearchScope.fromRetrievalCriteria(
            effectiveScope,
            effectiveFilters,
            uploadedAfterInclusive,
            uploadedBeforeExclusive
        );

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
            return new RetrievalSearchExecution(
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
                fallbackPolicy.emptyLexicalResult(),
                List.of(),
                0,
                List.of(),
                Map.of(),
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
            searchScope
        );
        ProductionLexicalSearchRouter.LexicalSearchResult lexicalSearchResult = productionLexicalSearchRouter.search(
            prompt,
            ragProperties.getLexicalCandidateLimit(),
            searchScope
        );
        List<MaterialChunkSearchMatch> lexicalMatches = lexicalSearchResult.matches();
        lexicalShadowComparisonService.compareIfEligible(
            prompt,
            lexicalSearchResult.effectiveProvider(),
            lexicalMatches,
            ragProperties.getLexicalCandidateLimit(),
            searchScope
        );
        int rerankPoolLimit = relevanceProfile == RelevanceProfile.HYBRID_RERANK_V1
            ? Math.max(finalLimit, ragProperties.getRerankCandidateLimit())
            : finalLimit;
        List<HybridChunkRanker.RankedChunk> rankedMatches = relevancePolicy.filterRankedMatches(
            semanticMatches,
            lexicalMatches,
            hybridChunkRanker.fuse(semanticMatches, lexicalMatches, rerankPoolLimit),
            ragProperties
        );
        List<HybridChunkRanker.RankedChunk> preRerankMatches = List.copyOf(rankedMatches);
        int rerankCandidateCount = rankedMatches.size();
        Map<String, StoredMaterialRecord> candidateRecordsById =
            candidateMaterialLoader.loadRecordsByMaterialId(rankedMatches);
        Map<String, List<StoredMaterialChunk>> chunksByMaterialId = Map.of();
        if (relevanceProfile == RelevanceProfile.HYBRID_RERANK_V1) {
            chunksByMaterialId = candidateMaterialLoader.loadChunksByMaterialId(rankedMatches);
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
                resultMapper.buildChatSource(chunk, queryTokens, candidateRecordsById.get(chunk.match().materialId()))
            ))
            .toList();

        String supportVerdict = RetrievalTraceBuilder.supportVerdictOf(semanticMatches, lexicalMatches, rankedMatches);
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
            windowRecorder.record(matches, candidateRecordsById, preRerankMatches, rankedMatches);
        }
        return new RetrievalSearchExecution(
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
            chunksByMaterialId,
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

    private void logSuppressedCapability(String capability, String reason) {
        logger.info("Quality-layer capability suppressed: capability={} reason={}", capability, reason);
    }

}
