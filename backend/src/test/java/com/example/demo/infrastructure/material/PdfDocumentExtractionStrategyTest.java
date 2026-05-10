package com.example.demo.infrastructure.material;

import com.example.demo.model.DocumentBlockConfidence;
import com.example.demo.service.material.DocumentParseResult;
import com.example.demo.service.material.DocumentParserProfile;
import com.example.demo.service.material.MaterialFormatRegistry;
import com.example.demo.service.material.OcrCapability;
import com.example.demo.service.material.port.OcrCapabilityProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.error.ErrorType;
import com.example.demo.error.ProviderException;
import com.example.demo.config.MaterialProperties;
import com.example.demo.config.OcrProperties;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;

class PdfDocumentExtractionStrategyTest {

    private static final OcrCapabilityProvider FULL_OCR_CAPABILITY =
        () -> OcrCapability.embeddedTextAndOcr(List.of("kaz", "rus", "eng"), 12);

    @Test
    void extractsEmbeddedTextWithoutUsingOcr() throws Exception {
        CountingOcrClient ocrClient = new CountingOcrClient();
        PdfDocumentExtractionStrategy strategy = new PdfDocumentExtractionStrategy(
            new MaterialFormatRegistry(),
            new OcrProperties(),
            ocrClient,
            FULL_OCR_CAPABILITY
        );

        DocumentParseResult result = strategy.extract("pricing.pdf", "application/pdf", createTextPdf("Premium price is 12000."));

        assertFalse(result.ocrUsed());
        assertEquals("pdfbox", result.extractor());
        assertEquals(DocumentParserProfile.PDF, result.parserProfile());
        assertEquals(0, ocrClient.calls);
        assertTrue(result.blocks().getFirst().text().contains("12000"));
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void fallsBackToOcrForScannedPdf() throws Exception {
        CountingOcrClient ocrClient = new CountingOcrClient();
        PdfDocumentExtractionStrategy strategy = new PdfDocumentExtractionStrategy(
            new MaterialFormatRegistry(),
            new OcrProperties(),
            ocrClient,
            FULL_OCR_CAPABILITY
        );

        DocumentParseResult result = strategy.extract("scan.pdf", "application/pdf", createScannedPdf());

        assertTrue(result.ocrUsed());
        assertEquals("pdfbox+tesseract", result.extractor());
        assertEquals(1, ocrClient.calls);
        assertEquals(1, result.blocks().getFirst().page());
        assertEquals("tesseract", result.blocks().getFirst().extractor());
        assertEquals(DocumentBlockConfidence.LOW, result.blocks().getFirst().confidence());
    }

    @Test
    void usesSelectiveOcrForMixedPdfInsteadOfFailingWholeDocument() throws Exception {
        CountingOcrClient ocrClient = new CountingOcrClient();
        PdfDocumentExtractionStrategy strategy = new PdfDocumentExtractionStrategy(
            new MaterialFormatRegistry(),
            new OcrProperties(),
            ocrClient,
            FULL_OCR_CAPABILITY
        );

        DocumentParseResult result = strategy.extract("mixed.pdf", "application/pdf", createMixedPdf());

        assertTrue(result.ocrUsed());
        assertEquals(2, result.blocks().size());
        assertEquals(1, ocrClient.calls);
        assertEquals("pdfbox", result.blocks().get(0).extractor());
        assertEquals("tesseract", result.blocks().get(1).extractor());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void keepsPartialExtractionWarningsAsStructuredWarnings() throws Exception {
        CountingOcrClient ocrClient = new CountingOcrClient();
        PdfDocumentExtractionStrategy strategy = new PdfDocumentExtractionStrategy(
            new MaterialFormatRegistry(),
            new OcrProperties(),
            ocrClient,
            () -> OcrCapability.embeddedTextAndOcr(List.of("kaz", "rus", "eng"), 0)
        );

        DocumentParseResult result = strategy.extract("mixed.pdf", "application/pdf", createMixedPdf());

        assertFalse(result.warnings().isEmpty());
        assertEquals("material.partial_extraction", result.warnings().getFirst().code());
        assertEquals(List.of(2), result.warnings().getFirst().pages());
    }

    @Test
    void failsWhenScannedPdfNeedsOcrButCapabilityReportsOcrDisabled() throws Exception {
        PdfDocumentExtractionStrategy strategy = new PdfDocumentExtractionStrategy(
            new MaterialFormatRegistry(),
            new OcrProperties(),
            new CountingOcrClient(),
            () -> OcrCapability.embeddedTextOnly(
                "material.ocr_disabled",
                "OCR is disabled on the server.",
                List.of("kaz", "rus", "eng"),
                12
            )
        );

        ProviderException exception = assertThrows(ProviderException.class, () ->
            strategy.extract("scan.pdf", "application/pdf", createScannedPdf()));

        assertEquals("material.ocr_disabled", exception.getCode());
    }

    @Test
    void rejectsScannedPdfBeforeRasterizationWhenOcrCapabilityIsUnavailable() throws Exception {
        CountingOcrClient ocrClient = new CountingOcrClient();
        PdfDocumentExtractionStrategy strategy = new PdfDocumentExtractionStrategy(
            new MaterialFormatRegistry(),
            new OcrProperties(),
            ocrClient,
            () -> OcrCapability.embeddedTextOnly(
                "material.ocr_unavailable",
                "Tesseract OCR binary is unavailable at 'tesseract'.",
                List.of("kaz", "rus", "eng"),
                12
            )
        );

        ProviderException exception = assertThrows(ProviderException.class, () ->
            strategy.extract("scan.pdf", "application/pdf", createScannedPdf()));

        assertEquals("material.ocr_unavailable", exception.getCode());
        assertEquals(0, ocrClient.calls);
    }

    @Test
    void rejectsScannedPdfPagesThatExceedPreRenderBudget() throws Exception {
        CountingOcrClient ocrClient = new CountingOcrClient();
        OcrProperties properties = new OcrProperties();
        properties.setMaxRenderedImageBytes(1_024);
        PdfDocumentExtractionStrategy strategy = new PdfDocumentExtractionStrategy(
            new MaterialFormatRegistry(),
            properties,
            ocrClient,
            FULL_OCR_CAPABILITY
        );

        ProviderException exception = assertThrows(ProviderException.class, () ->
            strategy.extract("scan.pdf", "application/pdf", createScannedPdf()));

        assertEquals("material.ocr_render_budget_exceeded", exception.getCode());
        assertEquals(0, ocrClient.calls);
    }

    @Test
    void rejectsPdfBeforeTextExtractionWhenPageCountExceedsBudget() throws Exception {
        MaterialProperties materialProperties = new MaterialProperties();
        materialProperties.setMaxPdfPages(1);
        CountingOcrClient ocrClient = new CountingOcrClient();
        PdfDocumentExtractionStrategy strategy = new PdfDocumentExtractionStrategy(
            new MaterialFormatRegistry(),
            materialProperties,
            new OcrProperties(),
            ocrClient,
            FULL_OCR_CAPABILITY
        );

        ProviderException exception = assertThrows(ProviderException.class, () ->
            strategy.extract("mixed.pdf", "application/pdf", createMixedPdf()));

        assertEquals("material.extraction_too_large", exception.getCode());
        assertEquals(0, ocrClient.calls);
    }

    private byte[] createTextPdf(String text) throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA, 12);
                contentStream.newLineAtOffset(72, 720);
                contentStream.showText(text);
                contentStream.endText();
            }
            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }

    private byte[] createScannedPdf() throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            BufferedImage image = new BufferedImage(900, 1200, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(Color.BLACK);
            graphics.drawString("Scanned page", 80, 100);
            graphics.dispose();

            PDImageXObject xObject = LosslessFactory.createFromImage(document, image);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.drawImage(xObject, 40, 80, 520, 680);
            }

            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }

    private byte[] createMixedPdf() throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDPage firstPage = new PDPage(PDRectangle.A4);
            PDPage secondPage = new PDPage(PDRectangle.A4);
            document.addPage(firstPage);
            document.addPage(secondPage);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, firstPage)) {
                contentStream.beginText();
                contentStream.setFont(PDType1Font.HELVETICA, 12);
                contentStream.newLineAtOffset(72, 720);
                contentStream.showText("Embedded premium price is 12000.");
                contentStream.endText();
            }

            BufferedImage image = new BufferedImage(900, 1200, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(Color.BLACK);
            graphics.drawString("Scanned appendix", 80, 100);
            graphics.dispose();

            PDImageXObject xObject = LosslessFactory.createFromImage(document, image);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, secondPage)) {
                contentStream.drawImage(xObject, 40, 80, 520, 680);
            }

            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }

    private static final class CountingOcrClient implements OcrClient {
        private int calls = 0;

        @Override
        public String extract(Path imagePath, int pageNumber) {
            calls++;
            return "OCR fallback text for page " + pageNumber;
        }
    }
}
