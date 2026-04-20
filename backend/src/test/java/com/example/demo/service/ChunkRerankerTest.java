package com.example.demo.service;

import com.example.demo.service.material.DocumentBlockConfidence;
import com.example.demo.service.material.DocumentBlockType;
import com.example.demo.service.material.MaterialChunkSearchMatch;
import com.example.demo.service.material.StoredMaterialChunk;
import com.example.demo.service.material.StoredMaterialRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.config.MaterialProperties;
import com.example.demo.model.DocumentType;
import com.example.demo.model.MaterialIndexingStatus;
import com.example.demo.model.MaterialMetadataInput;
import com.example.demo.model.MaterialMetadataSnapshot;
import com.example.demo.model.MaterialVersionState;
import com.example.demo.model.RetrievalFilters;
import com.example.demo.model.RetrievalQueryHints;
import com.example.demo.model.SourceTrustLevel;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChunkRerankerTest {

    private final MaterialContentSupport contentSupport = new MaterialContentSupport(new MaterialProperties());
    private final ChunkReranker reranker = new ChunkReranker(contentSupport);

    @Test
    void prefersExactIdentifierAndMetadataMatchOverGenericChunk() {
        HybridChunkRanker.RankedChunk generic = new HybridChunkRanker.RankedChunk(
            new MaterialChunkSearchMatch(
                "generic",
                0,
                "Dispatch appendix",
                "Overview of dispatch procedures.",
                5,
                "structured-v1",
                false,
                DocumentBlockType.APPENDIX,
                0.14d,
                2.0d
            ),
            60,
            1,
            1,
            null
        );
        HybridChunkRanker.RankedChunk exact = new HybridChunkRanker.RankedChunk(
            new MaterialChunkSearchMatch(
                "exact",
                0,
                "Dispatch matrix",
                "Dispatch matrix for contract KZ-2026-0415-ENERGY approved for April 2026.",
                2,
                "structured-v1",
                false,
                DocumentBlockType.TABLE,
                0.18d,
                1.6d
            ),
            55,
            3,
            2,
            null
        );

        List<HybridChunkRanker.RankedChunk> reranked = reranker.rerank(
            "Что указано в договоре KZ-2026-0415-ENERGY по проекту North Upgrade?",
            new RetrievalQueryHints(
                "KZ-2026-0415-ENERGY",
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-04-30"),
                null,
                "ru",
                "North Upgrade",
                null,
                null,
                null
            ),
            new RetrievalFilters(
                "KZ-2026-0415-ENERGY",
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-04-30"),
                null,
                "North Upgrade",
                null,
                null,
                "ru",
                List.of(),
                SourceTrustLevel.MEDIUM
            ),
            List.of(generic, exact),
            Map.of(
                "generic", record("generic", "Appendix note", MaterialMetadataSnapshot.fromInput(new MaterialMetadataInput(
                    DocumentType.OTHER,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "ru",
                    List.of(),
                    SourceTrustLevel.UNKNOWN,
                    null,
                    null,
                    null,
                    null,
                    null
                ))),
                "exact", record("exact", "Dispatch matrix", MaterialMetadataSnapshot.fromInput(new MaterialMetadataInput(
                    DocumentType.CONTRACT,
                    LocalDate.parse("2026-04-15"),
                    "KZ-2026-0415-ENERGY",
                    "Dana Sarsen",
                    "Grid operations",
                    "v2",
                    "ru",
                    List.of("dispatch"),
                    SourceTrustLevel.HIGH,
                    "North Upgrade",
                    "GridBuild LLP",
                    "APPROVED",
                    LocalDate.parse("2026-04-01"),
                    LocalDate.parse("2026-06-30")
                )))
            ),
            Map.of(
                "generic", List.of(new StoredMaterialChunk(
                    0,
                    "Appendix B. Overview of dispatch procedures.",
                    List.of("appendix", "dispatch"),
                    5,
                    "structured-v1",
                    false,
                    DocumentBlockType.APPENDIX,
                    List.of("appendix-b"),
                    List.of("Appendix B"),
                    null,
                    null,
                    DocumentBlockConfidence.HIGH
                )),
                "exact", List.of(new StoredMaterialChunk(
                    0,
                    "Dispatch matrix for contract KZ-2026-0415-ENERGY approved for April 2026.",
                    List.of("dispatch", "matrix", "contract"),
                    2,
                    "structured-v1",
                    false,
                    DocumentBlockType.TABLE,
                    List.of("dispatch", "matrix"),
                    List.of("Dispatch", "Dispatch matrix"),
                    "table-1",
                    null,
                    DocumentBlockConfidence.HIGH
                ))
            ),
            2
        );

        assertEquals("exact", reranked.getFirst().match().materialId());
        assertTrue(reranked.getFirst().scoreBreakdown().identifierBonus() >= 20);
        assertTrue(reranked.getFirst().scoreBreakdown().metadataBonus() >= 8);
        assertTrue(reranked.get(1).scoreBreakdown().appendixPenalty() < 0);
    }

    @Test
    void appliesLowConfidenceAndBoilerplatePenaltiesDeterministically() {
        HybridChunkRanker.RankedChunk candidate = new HybridChunkRanker.RankedChunk(
            new MaterialChunkSearchMatch(
                "low-confidence",
                1,
                "Appendix disclaimer",
                "Настоящий документ. All rights reserved.",
                7,
                "ocr",
                true,
                DocumentBlockType.CAPTION,
                0.2d,
                1.8d
            ),
            58,
            2,
            2,
            null
        );

        List<HybridChunkRanker.RankedChunk> reranked = reranker.rerank(
            "какие права",
            RetrievalQueryHints.empty(),
            RetrievalFilters.empty(),
            List.of(candidate),
            Map.of("low-confidence", record("low-confidence", "Appendix disclaimer", MaterialMetadataSnapshot.empty())),
            Map.of("low-confidence", List.of(new StoredMaterialChunk(
                1,
                "Настоящий документ. All rights reserved.",
                List.of("document"),
                7,
                "ocr",
                true,
                DocumentBlockType.CAPTION,
                List.of("appendix"),
                List.of("Appendix disclaimer"),
                null,
                null,
                DocumentBlockConfidence.LOW
            ))),
            1
        );

        assertEquals(-6, reranked.getFirst().scoreBreakdown().appendixPenalty());
        assertEquals(-10, reranked.getFirst().scoreBreakdown().boilerplatePenalty());
        assertEquals(-8, reranked.getFirst().scoreBreakdown().lowConfidencePenalty());
    }

    private StoredMaterialRecord record(String id, String title, MaterialMetadataSnapshot metadata) {
        Instant timestamp = Instant.parse("2026-04-19T00:00:00Z");
        return new StoredMaterialRecord(
            id,
            title,
            "text",
            null,
            "text/plain",
            title,
            title,
            UUID.randomUUID().toString(),
            id + "-lineage",
            "structured-v1",
            false,
            1,
            List.of(),
            MaterialIndexingStatus.READY,
            MaterialVersionState.ACTIVE,
            null,
            null,
            timestamp,
            timestamp,
            metadata
        );
    }
}
