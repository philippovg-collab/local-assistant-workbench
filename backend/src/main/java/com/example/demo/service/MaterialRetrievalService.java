package com.example.demo.service;

import com.example.demo.api.ApiException;
import com.example.demo.config.RagProperties;
import com.example.demo.embedding.EmbeddingClient;
import com.example.demo.infrastructure.material.MaterialCatalogRepository;
import com.example.demo.infrastructure.material.MaterialChunkSearchMatch;
import com.example.demo.infrastructure.material.MaterialSearchRepository;
import com.example.demo.model.ChatSource;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class MaterialRetrievalService {

    private static final Logger logger = LoggerFactory.getLogger(MaterialRetrievalService.class);

    private final MaterialCatalogRepository catalogRepository;
    private final MaterialSearchRepository searchRepository;
    private final EmbeddingClient embeddingClient;
    private final RagProperties ragProperties;
    private final HybridChunkRanker hybridChunkRanker;
    private final MaterialContentSupport contentSupport;

    public MaterialRetrievalService(
        MaterialCatalogRepository catalogRepository,
        MaterialSearchRepository searchRepository,
        EmbeddingClient embeddingClient,
        RagProperties ragProperties,
        HybridChunkRanker hybridChunkRanker,
        MaterialContentSupport contentSupport
    ) {
        this.catalogRepository = catalogRepository;
        this.searchRepository = searchRepository;
        this.embeddingClient = embeddingClient;
        this.ragProperties = ragProperties;
        this.hybridChunkRanker = hybridChunkRanker;
        this.contentSupport = contentSupport;
    }

    public MaterialRetrievalResult retrieveContext(String prompt) {
        Set<String> queryTokens = contentSupport.tokenize(prompt);
        if (queryTokens.isEmpty()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "chat.invalid_prompt",
                "Prompt must contain at least one alphanumeric token"
            );
        }

        int materialCount = catalogRepository.countMaterials();
        if (materialCount == 0) {
            return new MaterialRetrievalResult(0, 0, 0, List.of());
        }

        int activeMaterialCount = catalogRepository.countActiveMaterials();
        if (activeMaterialCount == 0) {
            return new MaterialRetrievalResult(materialCount, 0, 0, List.of());
        }

        int readyMaterialCount = catalogRepository.countReadyMaterials();
        if (readyMaterialCount == 0) {
            return new MaterialRetrievalResult(materialCount, activeMaterialCount, 0, List.of());
        }

        List<MaterialChunkSearchMatch> semanticMatches = searchRepository.searchSemantic(
            embeddingClient.embed(prompt.trim()),
            ragProperties.getSemanticCandidateLimit()
        );
        List<MaterialChunkSearchMatch> lexicalMatches = searchRepository.searchLexical(
            prompt,
            ragProperties.getLexicalCandidateLimit()
        );
        Set<String> relevantChunkKeys = new LinkedHashSet<>();
        semanticMatches.stream()
            .filter(this::isRelevant)
            .map(this::chunkKeyOf)
            .forEach(relevantChunkKeys::add);
        lexicalMatches.stream()
            .filter(this::isRelevant)
            .map(this::chunkKeyOf)
            .forEach(relevantChunkKeys::add);
        List<HybridChunkRanker.RankedChunk> rankedMatches = hybridChunkRanker.fuse(
            semanticMatches,
            lexicalMatches,
            ragProperties.getFinalContextLimit()
        ).stream()
            .filter(chunk -> relevantChunkKeys.contains(chunkKeyOf(chunk.match())))
            .toList();

        logger.info(
            "RAG retrieval: materials={} activeMaterials={} readyMaterials={} semanticMatches={} lexicalMatches={} fusedMatches={} scoring=hybrid-rrf-v1 top={}",
            materialCount,
            activeMaterialCount,
            readyMaterialCount,
            semanticMatches.size(),
            lexicalMatches.size(),
            rankedMatches.size(),
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
                new ChatSource(
                    chunk.match().materialId(),
                    chunk.match().title(),
                    contentSupport.clip(chunk.match().chunkText(), 280),
                    chunk.score(),
                    chunk.match().page(),
                    chunk.match().extractor(),
                    chunk.match().ocrUsed()
                )
            ))
            .toList();

        return new MaterialRetrievalResult(materialCount, activeMaterialCount, readyMaterialCount, matches);
    }

    private boolean isRelevant(MaterialChunkSearchMatch match) {
        boolean lexicalHit = match.lexicalScore() != null && match.lexicalScore() > 0.0d;
        boolean semanticHit = match.semanticDistance() != null
            && match.semanticDistance() <= ragProperties.getMaxSemanticDistance();
        return lexicalHit || semanticHit;
    }

    private String chunkKeyOf(MaterialChunkSearchMatch match) {
        return match.materialId() + ":" + match.chunkIndex();
    }
}
