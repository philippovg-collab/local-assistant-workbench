package com.example.demo.infrastructure.material;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.demo.model.DocumentType;
import com.example.demo.model.MaterialMetadataInput;
import com.example.demo.model.MaterialMetadataSnapshot;
import com.example.demo.model.SourceTrustLevel;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class SearchableChunkDocumentTest {

    @Test
    void mapsSearchableSnapshotIntoStableChunkDocuments() {
        SearchableMaterialSnapshot snapshot = new SearchableMaterialSnapshot(
            "material-123",
            true,
            "pricing-faq",
            "Pricing FAQ",
            "file",
            "pricing.txt",
            "text/plain",
            Instant.parse("2026-04-17T10:00:00Z"),
            MaterialMetadataSnapshot.fromInput(new MaterialMetadataInput(
                DocumentType.CONTRACT,
                LocalDate.parse("2026-04-15"),
                "KZ-2026-0415-ENERGY",
                "Dana Sarsen",
                "Grid operations",
                "v2",
                "ru",
                List.of("dispatch", "grid"),
                SourceTrustLevel.HIGH,
                "North Upgrade",
                "GridBuild LLP",
                "APPROVED",
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-06-30")
            )),
            List.of(
                new SearchableMaterialChunkSnapshot(
                    0,
                    "Первый чанк",
                    1,
                    "pdfbox",
                    false,
                    DocumentBlockType.TABLE,
                    List.of("grid-operations", "tariff-matrix"),
                    List.of("Grid operations", "Tariff matrix"),
                    "table-1",
                    null,
                    DocumentBlockConfidence.HIGH
                ),
                new SearchableMaterialChunkSnapshot(
                    1,
                    "Второй чанк",
                    2,
                    "tika",
                    true,
                    DocumentBlockType.APPENDIX,
                    List.of("appendix-a"),
                    List.of("Appendix A"),
                    null,
                    null,
                    DocumentBlockConfidence.LOW
                )
            )
        );

        List<SearchableChunkDocument> documents = SearchableChunkDocument.fromSnapshot(snapshot);

        assertEquals(2, documents.size());
        assertEquals("material-123:0", documents.get(0).documentId());
        assertEquals("material-123:0", documents.get(0).chunkId());
        assertEquals("material-123", documents.get(0).materialId());
        assertEquals("pricing-faq", documents.get(0).sourceKey());
        assertEquals("Pricing FAQ", documents.get(0).title());
        assertEquals("Первый чанк", documents.get(0).chunkText());
        assertEquals(1, documents.get(0).page());
        assertEquals("pdfbox", documents.get(0).extractor());
        assertFalse(documents.get(0).ocrUsed());
        assertEquals("TABLE", documents.get(0).chunkType());
        assertEquals(List.of("grid-operations", "tariff-matrix"), documents.get(0).sectionPath());
        assertEquals(List.of("Grid operations", "Tariff matrix"), documents.get(0).headingTrail());
        assertEquals("table-1", documents.get(0).tableId());
        assertEquals("HIGH", documents.get(0).parserConfidence());
        assertEquals("KZ-2026-0415-ENERGY", documents.get(0).documentNumber());
        assertEquals(LocalDate.parse("2026-04-15"), documents.get(0).documentDate());
        assertEquals("Grid operations", documents.get(0).department());
        assertEquals("North Upgrade", documents.get(0).project());
        assertEquals("GridBuild LLP", documents.get(0).counterparty());
        assertEquals("APPROVED", documents.get(0).businessStatus());
        assertEquals("ru", documents.get(0).language());
        assertEquals(List.of("dispatch", "grid"), documents.get(0).tags());
        assertEquals("HIGH", documents.get(0).sourceTrust());
        assertEquals("file", documents.get(0).sourceType());
        assertEquals(snapshot.updatedAt(), documents.get(0).updatedAt());
        assertEquals("material-123:1", documents.get(1).documentId());
        assertEquals("material-123:1", documents.get(1).chunkId());
        assertEquals("APPENDIX", documents.get(1).chunkType());
        assertEquals("LOW", documents.get(1).parserConfidence());
    }

    @Test
    void elasticsearchStrictMappingCoversEverySerializedDocumentField() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        SearchableChunkDocument document = new SearchableChunkDocument(
            "material-123:0",
            "material-123:0",
            "material-123",
            "pricing-faq",
            "Pricing FAQ",
            "Первый чанк",
            1,
            "pdfbox",
            false,
            "TABLE",
            List.of("grid-operations", "tariff-matrix"),
            List.of("Grid operations", "Tariff matrix"),
            "table-1",
            "slide-1",
            "HIGH",
            "KZ-2026-0415-ENERGY",
            null,
            "Grid operations",
            "North Upgrade",
            "GridBuild LLP",
            "APPROVED",
            "ru",
            List.of("dispatch", "grid"),
            "HIGH",
            "file",
            null
        );

        var serializedDocument = objectMapper.valueToTree(document);
        var mappingResource = new ClassPathResource("elasticsearch/searchable-chunks-index.json");
        try (var inputStream = mappingResource.getInputStream()) {
            var mappedProperties = objectMapper.readTree(inputStream)
                .path("mappings")
                .path("properties");

            assertFalse(serializedDocument.has("documentId"));
            serializedDocument.fieldNames().forEachRemaining(fieldName ->
                assertTrue(mappedProperties.has(fieldName), "Missing Elasticsearch mapping for " + fieldName)
            );
        }
    }
}
