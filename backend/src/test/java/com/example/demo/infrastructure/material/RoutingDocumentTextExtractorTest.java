package com.example.demo.infrastructure.material;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.demo.api.ApiException;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoutingDocumentTextExtractorTest {

    @Test
    void routesToTheFirstSupportingStrategy() {
        RoutingDocumentTextExtractor extractor = new RoutingDocumentTextExtractor(List.of(
            new StubStrategy(false, "ignored"),
            new StubStrategy(true, "selected")
        ));

        DocumentParseResult result = extractor.extract("sample.pdf", "application/pdf", new byte[0]);

        assertEquals("selected", result.extractor());
    }

    @Test
    void rejectsUnsupportedFormatsWhenNoStrategyMatches() {
        RoutingDocumentTextExtractor extractor = new RoutingDocumentTextExtractor(List.of(
            new StubStrategy(false, "ignored")
        ));

        ApiException exception = assertThrows(ApiException.class, () ->
            extractor.extract("sample.bin", "application/octet-stream", new byte[0]));

        assertEquals("material.unsupported_format", exception.getCode());
    }

    private record StubStrategy(boolean supports, String extractorName) implements DocumentTextExtractionStrategy {

        @Override
        public boolean supports(String originalFileName, String mediaType) {
            return supports;
        }

        @Override
        public DocumentParseResult extract(String originalFileName, String mediaType, byte[] bytes) {
            return new DocumentParseResult(
                List.of(new DocumentBlock(
                    0,
                    DocumentBlockType.NARRATIVE,
                    "ok",
                    null,
                    extractorName,
                    false,
                    DocumentBlockConfidence.HIGH,
                    null
                )),
                MaterialMetadataHints.empty(),
                List.of(),
                null,
                extractorName,
                false,
                DocumentParserProfile.RICH_TEXT
            );
        }
    }
}
