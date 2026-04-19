package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.config.MaterialProperties;
import com.example.demo.infrastructure.material.ChunkProfile;
import com.example.demo.infrastructure.material.DocumentBlock;
import com.example.demo.infrastructure.material.DocumentBlockConfidence;
import com.example.demo.infrastructure.material.DocumentBlockType;
import com.example.demo.infrastructure.material.DocumentParseResult;
import com.example.demo.infrastructure.material.DocumentParserProfile;
import com.example.demo.infrastructure.material.MaterialMetadataHints;
import com.example.demo.infrastructure.material.MaterialLineageIdentity;
import com.example.demo.infrastructure.material.MaterialLineageIdentityKind;
import com.example.demo.infrastructure.material.StoredMaterialChunk;
import com.example.demo.infrastructure.material.StoredMaterialSegment;
import java.util.List;
import org.junit.jupiter.api.Test;

class MaterialContentSupportTest {

    private final MaterialContentSupport contentSupport = new MaterialContentSupport(new MaterialProperties());

    @Test
    void buildsCanonicalTextIdentityFromExplicitTitle() {
        MaterialLineageIdentity identity = contentSupport.buildLineageIdentity(
            "text",
            "Pricing FAQ",
            null,
            "Новая редакция тарифа 12000 тенге."
        );

        assertEquals(MaterialLineageIdentityKind.EXPLICIT_TITLE, identity.identityKind());
        assertEquals("pricing-faq", identity.identityKey());
        assertEquals("pricing-faq", identity.explicitTitleNorm());
        assertEquals("text", identity.sourceType());
        assertTrue(identity.sourceKey().startsWith("text:"));
    }

    @Test
    void buildsCanonicalTextIdentityFromContentAnchorWhenExplicitTitleIsMissing() {
        MaterialLineageIdentity identity = contentSupport.buildLineageIdentity(
            "text",
            null,
            null,
            "Политика закупки оборудования и бюджетирования."
        );

        assertEquals(MaterialLineageIdentityKind.CONTENT_ANCHOR, identity.identityKind());
        assertEquals(identity.contentAnchor(), identity.identityKey());
        assertEquals("text", identity.sourceType());
    }

    @Test
    void buildsCanonicalFileIdentityFromExplicitTitleWhenPresent() {
        MaterialLineageIdentity identity = contentSupport.buildLineageIdentity(
            "file",
            "Ремонтный план 2026",
            "brief.txt",
            "График ремонта подстанции на май и июнь."
        );

        assertEquals(MaterialLineageIdentityKind.EXPLICIT_TITLE, identity.identityKind());
        assertEquals("ремонтный-план-2026", identity.identityKey());
        assertEquals("brief-txt", identity.originalFileNameNorm());
        assertEquals("brief", identity.fileStemNorm());
        assertTrue(identity.sourceKey().startsWith("file:"));
    }

    @Test
    void buildsCanonicalFileIdentityFromFileStemAndAnchorWhenExplicitTitleIsMissing() {
        MaterialLineageIdentity identity = contentSupport.buildLineageIdentity(
            "file",
            null,
            "brief.txt",
            "план ремонта подстанции север май июнь июль август сентябрь октябрь ноябрь декабрь версия два"
        );

        assertEquals(MaterialLineageIdentityKind.FILE_STEM_AND_CONTENT_ANCHOR, identity.identityKind());
        assertEquals("brief|" + identity.contentAnchor(), identity.identityKey());
        assertEquals("brief", identity.fileStemNorm());
    }

    @Test
    void stripsOutlinePrefixesForRomanAndHierarchicalSections() {
        assertEquals("grid-operations", contentSupport.normalizeSectionKey("IV. Grid operations"));
        assertEquals("tariff-matrix", contentSupport.normalizeSectionKey("2.1. Tariff matrix"));
    }

    @Test
    void buildsSentenceAwareChunksWithSentenceOverlapAndMetadata() {
        MaterialProperties properties = new MaterialProperties();
        properties.setChunkSize(45);
        properties.setChunkOverlap(25);
        MaterialContentSupport sentenceAwareSupport = new MaterialContentSupport(properties);

        List<StoredMaterialChunk> chunks = sentenceAwareSupport.buildChunks(
            List.of(new StoredMaterialSegment(
                0,
                "Alpha short sentence. Beta short sentence. Gamma short sentence.",
                3,
                "tika",
                true
            )),
            ChunkProfile.SENTENCE_V1
        );

        assertEquals(2, chunks.size());
        assertEquals("Alpha short sentence. Beta short sentence.", chunks.get(0).text());
        assertEquals("Beta short sentence. Gamma short sentence.", chunks.get(1).text());
        assertEquals(3, chunks.get(0).page());
        assertEquals("tika", chunks.get(1).extractor());
        assertTrue(chunks.get(0).ocrUsed());
    }

    @Test
    void fallsBackToFixedSlicingForOversizedSentence() {
        MaterialProperties properties = new MaterialProperties();
        properties.setChunkSize(20);
        properties.setChunkOverlap(5);
        MaterialContentSupport sentenceAwareSupport = new MaterialContentSupport(properties);

        List<StoredMaterialChunk> chunks = sentenceAwareSupport.buildChunks(
            List.of(new StoredMaterialSegment(
                0,
                "ThisSentenceHasNoNaturalBoundaryAndShouldTriggerFixedFallbackBecauseItIsLong",
                null,
                "direct-text",
                false
            )),
            ChunkProfile.SENTENCE_V1
        );

        assertTrue(chunks.size() > 1);
        assertTrue(chunks.stream().allMatch(chunk -> chunk.text().length() <= 20));
    }

    @Test
    void createsPseudoSegmentsFromLegacyChunksWithoutDroppingMetadata() {
        List<StoredMaterialSegment> segments = contentSupport.pseudoSegmentsFromChunks(
            List.of(new StoredMaterialChunk(
                0,
                "Page level snippet",
                List.of(),
                7,
                "ocr",
                true
            )),
            "legacy",
            false
        );

        assertEquals(1, segments.size());
        assertEquals("Page level snippet", segments.getFirst().text());
        assertEquals(7, segments.getFirst().page());
        assertEquals("ocr", segments.getFirst().extractor());
        assertTrue(segments.getFirst().ocrUsed());
    }

    @Test
    void projectsStructuredParseResultsToLegacySegmentsWithoutDroppingMetadata() {
        DocumentParseResult parseResult = new DocumentParseResult(
            List.of(
                new DocumentBlock(
                    0,
                    DocumentBlockType.TITLE,
                    "Grid policy",
                    1,
                    "pdfbox",
                    false,
                    DocumentBlockConfidence.HIGH,
                    1
                ),
                new DocumentBlock(
                    1,
                    DocumentBlockType.NARRATIVE,
                    "OCR appendix text",
                    2,
                    "tesseract",
                    true,
                    DocumentBlockConfidence.LOW,
                    null
                )
            ),
            MaterialMetadataHints.empty(),
            List.of(),
            2,
            "pdfbox+tesseract",
            true,
            DocumentParserProfile.PDF
        );

        List<StoredMaterialSegment> segments = contentSupport.toStoredSegments(parseResult);

        assertEquals(2, segments.size());
        assertEquals("Grid policy", segments.getFirst().text());
        assertEquals(1, segments.getFirst().page());
        assertEquals("pdfbox", segments.getFirst().extractor());
        assertTrue(segments.get(1).ocrUsed());
        assertEquals("Grid policy\nOCR appendix text", contentSupport.headerTextForHints(parseResult));
        assertEquals("Grid policy\n\nOCR appendix text", contentSupport.joinBlocks(parseResult));
    }

    @Test
    void buildsStructuredChunksWithHeadingTrailAndTypedSpecialBlocks() {
        MaterialProperties properties = new MaterialProperties();
        properties.setChunkProfile(ChunkProfile.STRUCTURED_V1.propertyValue());
        properties.setChunkSize(70);
        properties.setChunkOverlap(25);
        MaterialContentSupport structuredSupport = new MaterialContentSupport(properties);

        DocumentParseResult parseResult = new DocumentParseResult(
            List.of(
                new DocumentBlock(0, DocumentBlockType.TITLE, "Grid operations", 1, "tika", false, DocumentBlockConfidence.HIGH, 1),
                new DocumentBlock(1, DocumentBlockType.TITLE, "Tariff matrix", 1, "tika", false, DocumentBlockConfidence.HIGH, 2),
                new DocumentBlock(2, DocumentBlockType.TABLE, "Тариф | Лимит | Цена\nBravo | 75 МВт | 18000 тенге", 1, "tika", false, DocumentBlockConfidence.HIGH, null),
                new DocumentBlock(3, DocumentBlockType.CAPTION, "Таблица 1. Действующие тарифы.", 1, "tika", false, DocumentBlockConfidence.HIGH, null),
                new DocumentBlock(4, DocumentBlockType.NARRATIVE, "Regional dispatcher approves the outage window. Backup dispatch support remains enabled. Field crews confirm restoration.", 2, "tika", false, DocumentBlockConfidence.HIGH, null),
                new DocumentBlock(5, DocumentBlockType.LIST, "- Primary owner: dispatch team\n- Escalation: reserve bridge", 2, "tika", false, DocumentBlockConfidence.HIGH, 1),
                new DocumentBlock(6, DocumentBlockType.QA, "Q: Who approves repairs?\nA: Regional dispatcher.", 2, "tika", false, DocumentBlockConfidence.HIGH, null),
                new DocumentBlock(7, DocumentBlockType.APPENDIX, "Приложение А. Архивные ставки до 2024 года.", 3, "tika", true, DocumentBlockConfidence.LOW, null),
                new DocumentBlock(8, DocumentBlockType.SLIDE, "Owner: National dispatch center\nEscalation channel: reserve bridge", 4, "tika", false, DocumentBlockConfidence.HIGH, null)
            ),
            MaterialMetadataHints.empty(),
            List.of(),
            4,
            "tika",
            false,
            DocumentParserProfile.RICH_TEXT
        );

        List<StoredMaterialChunk> chunks = structuredSupport.buildChunks(parseResult, ChunkProfile.STRUCTURED_V1);

        assertTrue(chunks.stream().anyMatch(chunk -> chunk.chunkType() == DocumentBlockType.NARRATIVE));
        assertTrue(chunks.stream().anyMatch(chunk -> chunk.chunkType() == DocumentBlockType.TABLE));
        assertTrue(chunks.stream().anyMatch(chunk -> chunk.chunkType() == DocumentBlockType.LIST));
        assertTrue(chunks.stream().anyMatch(chunk -> chunk.chunkType() == DocumentBlockType.QA));
        assertTrue(chunks.stream().anyMatch(chunk -> chunk.chunkType() == DocumentBlockType.APPENDIX));
        assertTrue(chunks.stream().anyMatch(chunk -> chunk.chunkType() == DocumentBlockType.SLIDE));

        StoredMaterialChunk tableChunk = chunks.stream()
            .filter(chunk -> chunk.chunkType() == DocumentBlockType.TABLE)
            .findFirst()
            .orElseThrow();
        assertEquals(List.of("grid-operations", "tariff-matrix"), tableChunk.sectionPath());
        assertEquals(List.of("Grid operations", "Tariff matrix"), tableChunk.headingTrail());
        assertEquals("table-1", tableChunk.tableId());
        assertTrue(chunks.stream()
            .filter(chunk -> "table-1".equals(chunk.tableId()))
            .anyMatch(chunk -> chunk.text().contains("Таблица 1. Действующие тарифы.")));

        List<StoredMaterialChunk> narrativeChunks = chunks.stream()
            .filter(chunk -> chunk.chunkType() == DocumentBlockType.NARRATIVE)
            .toList();
        assertTrue(narrativeChunks.size() >= 2);
        assertTrue(narrativeChunks.getFirst().text().contains("Grid operations"));
        assertTrue(
            String.join(" ", narrativeChunks.stream().map(StoredMaterialChunk::text).toList())
                .contains("Backup dispatch support remains enabled.")
        );

        StoredMaterialChunk appendixChunk = chunks.stream()
            .filter(chunk -> chunk.chunkType() == DocumentBlockType.APPENDIX)
            .findFirst()
            .orElseThrow();
        assertEquals(DocumentBlockConfidence.LOW, appendixChunk.parserConfidence());
        assertTrue(appendixChunk.ocrUsed());

        StoredMaterialChunk slideChunk = chunks.stream()
            .filter(chunk -> chunk.chunkType() == DocumentBlockType.SLIDE)
            .findFirst()
            .orElseThrow();
        assertEquals("slide-1", slideChunk.slideId());
        assertFalse(String.join(" ", slideChunk.headingTrail()).isBlank());
    }

    @Test
    void reconstructsStructuredChunksFromLegacySegmentsForBackfill() {
        MaterialProperties properties = new MaterialProperties();
        properties.setChunkProfile(ChunkProfile.STRUCTURED_V1.propertyValue());
        properties.setChunkSize(80);
        properties.setChunkOverlap(20);
        MaterialContentSupport structuredSupport = new MaterialContentSupport(properties);

        List<StoredMaterialSegment> legacySegments = List.of(
            new StoredMaterialSegment(0, "2. Dispatch approval\nRegional dispatcher approves the outage window.", 1, "ocr", true),
            new StoredMaterialSegment(1, "Тариф | Лимит | Цена\nBravo | 75 МВт | 18000 тенге", 2, "ocr", true)
        );

        List<DocumentBlock> reconstructedBlocks = structuredSupport.reconstructBlocks(legacySegments);
        List<StoredMaterialChunk> chunks = structuredSupport.buildChunks(legacySegments, ChunkProfile.STRUCTURED_V1);

        assertTrue(reconstructedBlocks.stream().anyMatch(block -> block.type() == DocumentBlockType.TITLE));
        assertTrue(reconstructedBlocks.stream().anyMatch(block -> block.type() == DocumentBlockType.TABLE));
        assertTrue(chunks.stream().anyMatch(chunk -> chunk.chunkType() == DocumentBlockType.NARRATIVE));
        assertTrue(chunks.stream().anyMatch(chunk -> chunk.chunkType() == DocumentBlockType.TABLE));
        assertTrue(chunks.stream().allMatch(chunk -> chunk.parserConfidence() == DocumentBlockConfidence.LOW));

        StoredMaterialChunk narrativeChunk = chunks.stream()
            .filter(chunk -> chunk.chunkType() == DocumentBlockType.NARRATIVE)
            .findFirst()
            .orElseThrow();
        assertEquals(List.of("dispatch-approval"), narrativeChunk.sectionPath());
        assertEquals(List.of("2. Dispatch approval"), narrativeChunk.headingTrail());
    }

    @Test
    void normalizesSectionKeysConsistentlyForParsedBlocksAndLegacyBackfill() {
        MaterialProperties properties = new MaterialProperties();
        properties.setChunkProfile(ChunkProfile.STRUCTURED_V1.propertyValue());
        properties.setChunkSize(80);
        properties.setChunkOverlap(20);
        MaterialContentSupport structuredSupport = new MaterialContentSupport(properties);

        DocumentParseResult parseResult = new DocumentParseResult(
            List.of(
                new DocumentBlock(0, DocumentBlockType.TITLE, "2. Dispatch approval", 1, "tika", false, DocumentBlockConfidence.HIGH, 1),
                new DocumentBlock(
                    1,
                    DocumentBlockType.NARRATIVE,
                    "Regional dispatcher approves the outage window.",
                    1,
                    "tika",
                    false,
                    DocumentBlockConfidence.HIGH,
                    null
                )
            ),
            MaterialMetadataHints.empty(),
            List.of(),
            1,
            "tika",
            false,
            DocumentParserProfile.RICH_TEXT
        );

        List<StoredMaterialSegment> legacySegments = List.of(
            new StoredMaterialSegment(0, "2. Dispatch approval\nRegional dispatcher approves the outage window.", 1, "ocr", true)
        );

        StoredMaterialChunk parsedChunk = structuredSupport.buildChunks(parseResult, ChunkProfile.STRUCTURED_V1).stream()
            .filter(chunk -> chunk.chunkType() == DocumentBlockType.NARRATIVE)
            .findFirst()
            .orElseThrow();
        StoredMaterialChunk backfilledChunk = structuredSupport.buildChunks(legacySegments, ChunkProfile.STRUCTURED_V1).stream()
            .filter(chunk -> chunk.chunkType() == DocumentBlockType.NARRATIVE)
            .findFirst()
            .orElseThrow();

        assertEquals(List.of("dispatch-approval"), parsedChunk.sectionPath());
        assertEquals(parsedChunk.sectionPath(), backfilledChunk.sectionPath());
        assertEquals(List.of("2. Dispatch approval"), parsedChunk.headingTrail());
        assertEquals(parsedChunk.headingTrail(), backfilledChunk.headingTrail());
    }

}
