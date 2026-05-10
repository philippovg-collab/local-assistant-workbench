package com.example.demo.infrastructure.material;

import com.example.demo.service.material.DocumentBlock;
import com.example.demo.model.DocumentBlockConfidence;
import com.example.demo.model.DocumentBlockType;
import com.example.demo.service.material.DocumentParseResult;
import com.example.demo.service.material.DocumentParserProfile;
import com.example.demo.service.material.MaterialMetadataHints;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.demo.error.ErrorType;
import com.example.demo.error.ApplicationException;
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
    void routesDetectedPdfBytesToPdfStrategyEvenWhenExtensionIsSpoofed() {
        RoutingDocumentTextExtractor extractor = new RoutingDocumentTextExtractor(List.of(
            new MediaTypeStrategy("application/pdf", "pdfbox"),
            new StubStrategy(true, "tika")
        ));

        DocumentParseResult result = extractor.extract(
            "spoofed.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            minimalPdf()
        );

        assertEquals("pdfbox", result.extractor());
    }

    @Test
    void rejectsUnsupportedDetectedContentTypesDespiteSupportedExtension() {
        RoutingDocumentTextExtractor extractor = new RoutingDocumentTextExtractor(List.of(
            new StubStrategy(true, "plain-text")
        ));

        ApplicationException exception = assertThrows(ApplicationException.class, () ->
            extractor.extract("sample.txt", "text/plain", pngHeader()));

        assertEquals("material.unsupported_format", exception.getCode());
    }

    @Test
    void rejectsUnsupportedFormatsWhenNoStrategyMatches() {
        RoutingDocumentTextExtractor extractor = new RoutingDocumentTextExtractor(List.of(
            new StubStrategy(false, "ignored")
        ));

        ApplicationException exception = assertThrows(ApplicationException.class, () ->
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

    private record MediaTypeStrategy(String supportedMediaType, String extractorName) implements DocumentTextExtractionStrategy {

        @Override
        public boolean supports(String originalFileName, String mediaType) {
            return supportedMediaType.equals(mediaType);
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
                DocumentParserProfile.PDF
            );
        }
    }

    private byte[] minimalPdf() {
        return ("%PDF-1.4\n"
            + "1 0 obj\n"
            + "<<>>\n"
            + "endobj\n"
            + "trailer\n"
            + "<<>>\n"
            + "%%EOF\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }

    private byte[] pngHeader() {
        return new byte[] {
            (byte) 0x89,
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A
        };
    }
}
