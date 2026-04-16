package com.example.demo.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.infrastructure.material.OcrCapability;
import com.example.demo.infrastructure.material.OcrCapabilityProvider;
import com.example.demo.infrastructure.material.OcrClient;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "app.storage-dir=${java.io.tmpdir}/rag-studio-material-controller-test-${random.uuid}")
@AutoConfigureMockMvc
class MaterialControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestOcrCapabilityProvider ocrCapabilityProvider;

    @Autowired
    private TestOcrClient ocrClient;

    @BeforeEach
    void resetTestDoubles() {
        ocrCapabilityProvider.setCapability(OcrCapability.embeddedTextAndOcr(List.of("kaz", "rus", "eng"), 12));
        ocrClient.reset();
    }

    @Test
    void returnsUploadPolicy() throws Exception {
        ocrCapabilityProvider.setCapability(OcrCapability.embeddedTextAndOcr(List.of("kaz", "rus", "eng"), 12));

        mockMvc.perform(get("/api/materials/policy"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.maxUploadBytes").value(2_000_000))
            .andExpect(jsonPath("$.richDocumentSupport").value(true))
            .andExpect(jsonPath("$.acceptedExtensions", hasItem("txt")))
            .andExpect(jsonPath("$.acceptedExtensions", hasItem("docx")))
            .andExpect(jsonPath("$.acceptedExtensions", hasItem("pdf")))
            .andExpect(jsonPath("$.pdf.enabled").value(true))
            .andExpect(jsonPath("$.pdf.scannedPdfSupport").value(true))
            .andExpect(jsonPath("$.pdf.mode").value("embedded_text_and_ocr"))
            .andExpect(jsonPath("$.pdf.ocrLanguages", hasItem("kaz")))
            .andExpect(jsonPath("$.pdf.ocrMaxPages").value(12));
    }

    @Test
    void returnsPartialPdfPolicyWhenOcrIsUnavailable() throws Exception {
        ocrCapabilityProvider.setCapability(OcrCapability.embeddedTextOnly(
            "material.ocr_unavailable",
            "Tesseract OCR binary is unavailable at 'tesseract'.",
            List.of("kaz", "rus", "eng"),
            12
        ));

        mockMvc.perform(get("/api/materials/policy"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.acceptedExtensions", hasItem("pdf")))
            .andExpect(jsonPath("$.pdf.enabled").value(true))
            .andExpect(jsonPath("$.pdf.scannedPdfSupport").value(false))
            .andExpect(jsonPath("$.pdf.mode").value("embedded_text_only"))
            .andExpect(jsonPath("$.pdf.ocrReasonCode").value("material.ocr_unavailable"));
    }

    @Test
    void createsTextMaterialsFromJsonPayload() throws Exception {
        mockMvc.perform(post("/api/materials")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "Pricing note",
                      "content": "Тариф Базовый стоит 8000 тенге."
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Pricing note"))
            .andExpect(jsonPath("$.preview").value(containsString("8000")));
    }

    @Test
    void rejectsMalformedJsonPayloads() throws Exception {
        mockMvc.perform(post("/api/materials")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("request.invalid_payload"));
    }

    @Test
    void rejectsTextMaterialsWithoutContent() throws Exception {
        mockMvc.perform(post("/api/materials")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "Pricing note",
                      "content": "   "
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("material.empty_text"));
    }

    @Test
    void uploadsPlainTextMaterials() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "pricing.txt",
            "text/plain",
            "Тариф Премиум стоит 12000 тенге.".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/materials/upload")
                .file(file)
                .param("title", "Pricing TXT"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Pricing TXT"))
            .andExpect(jsonPath("$.preview").value(containsString("12000")));
    }

    @Test
    void uploadsDocxMaterials() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "pricing.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            createDocx("Премиум включает приоритетную поддержку.")
        );

        mockMvc.perform(multipart("/api/materials/upload").file(file))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("pricing.docx"))
            .andExpect(jsonPath("$.preview").value(containsString("приоритетную поддержку")));
    }

    @Test
    void uploadsPdfWithEmbeddedTextWhenOcrCapabilityIsDegraded() throws Exception {
        ocrCapabilityProvider.setCapability(OcrCapability.embeddedTextOnly(
            "material.ocr_unavailable",
            "Tesseract OCR binary is unavailable at 'tesseract'.",
            List.of("kaz", "rus", "eng"),
            12
        ));

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "pricing.pdf",
            "application/pdf",
            createTextPdf("Premium price is 12000 tenge.")
        );

        mockMvc.perform(multipart("/api/materials/upload").file(file))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("pricing.pdf"))
            .andExpect(jsonPath("$.preview").value(containsString("12000")));
    }

    @Test
    void uploadsScannedPdfUsingOcrFallback() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "scan.pdf",
            "application/pdf",
            createScannedPdf()
        );

        mockMvc.perform(multipart("/api/materials/upload").file(file))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("scan.pdf"))
            .andExpect(jsonPath("$.preview").value(containsString("OCR fallback")));

        Assertions.assertEquals(1, ocrClient.calls());
    }

    @Test
    void rejectsScannedPdfWhenOcrCapabilityIsUnavailable() throws Exception {
        ocrCapabilityProvider.setCapability(OcrCapability.embeddedTextOnly(
            "material.ocr_unavailable",
            "Tesseract OCR binary is unavailable at 'tesseract'.",
            List.of("kaz", "rus", "eng"),
            12
        ));

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "scan.pdf",
            "application/pdf",
            createScannedPdf()
        );

        mockMvc.perform(multipart("/api/materials/upload").file(file))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("material.ocr_unavailable"));

        Assertions.assertEquals(0, ocrClient.calls());
    }

    @Test
    void rejectsScannedPdfWhenOcrLanguageDataIsMissing() throws Exception {
        ocrCapabilityProvider.setCapability(OcrCapability.embeddedTextOnly(
            "material.ocr_language_data_missing",
            "Tesseract language data is missing for configured OCR languages: kaz+rus+eng",
            List.of("kaz", "rus", "eng"),
            12
        ));

        MockMultipartFile file = new MockMultipartFile(
            "file",
            "scan.pdf",
            "application/pdf",
            createScannedPdf()
        );

        mockMvc.perform(multipart("/api/materials/upload").file(file))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("material.ocr_language_data_missing"));

        Assertions.assertEquals(0, ocrClient.calls());
    }

    @Test
    void rejectsUnsupportedFormats() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "diagram.png",
            "image/png",
            "not-supported".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/materials/upload").file(file))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("material.unsupported_format"));
    }

    @Test
    void rejectsFilesAboveTheConfiguredLimit() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "big.txt",
            "text/plain",
            new byte[2_000_001]
        );

        mockMvc.perform(multipart("/api/materials/upload").file(file))
            .andExpect(status().isPayloadTooLarge())
            .andExpect(jsonPath("$.code").value("material.upload_too_large"));
    }

    @Test
    void rejectsRequestsWithoutTheFilePart() throws Exception {
        mockMvc.perform(multipart("/api/materials/upload").param("title", "Missing file"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("material.missing_file_part"));
    }

    @Test
    void rejectsBrokenDocxDocumentsWithExtractionError() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "broken.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            createBrokenDocx()
        );

        mockMvc.perform(multipart("/api/materials/upload").file(file))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("material.extraction_failed"));
    }

    private byte[] createDocx(String text) throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText(text);
            document.write(outputStream);
            return outputStream.toByteArray();
        }
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

    private byte[] createBrokenDocx() throws Exception {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            zipOutputStream.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zipOutputStream.write("<Types></Types>".getBytes(StandardCharsets.UTF_8));
            zipOutputStream.closeEntry();
            return outputStream.toByteArray();
        }
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        @Primary
        TestOcrCapabilityProvider ocrCapabilityProvider() {
            return new TestOcrCapabilityProvider();
        }

        @Bean
        @Primary
        TestOcrClient ocrClient() {
            return new TestOcrClient();
        }
    }

    static final class TestOcrCapabilityProvider implements OcrCapabilityProvider {

        private OcrCapability capability = OcrCapability.embeddedTextAndOcr(List.of("kaz", "rus", "eng"), 12);

        void setCapability(OcrCapability capability) {
            this.capability = capability;
        }

        @Override
        public OcrCapability currentCapability() {
            return capability;
        }
    }

    static final class TestOcrClient implements OcrClient {

        private int calls = 0;

        void reset() {
            calls = 0;
        }

        int calls() {
            return calls;
        }

        @Override
        public String extract(java.nio.file.Path imagePath, int pageNumber) {
            calls++;
            return "OCR fallback text for PDF page " + pageNumber;
        }
    }
}
