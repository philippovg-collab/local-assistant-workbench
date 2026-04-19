package com.example.demo.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.infrastructure.material.OcrCapability;
import com.example.demo.infrastructure.material.OcrCapabilityProvider;
import com.example.demo.infrastructure.material.OcrClient;
import com.example.demo.model.MaterialSummary;
import com.example.demo.support.IntegrationTestOverrides;
import com.example.demo.support.PostgresIntegrationTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestOverrides.class)
class MaterialControllerIT extends PostgresIntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    void deletingActiveMaterialPromotesLatestSupersededVersion() throws Exception {
        MaterialSummary first = createTextMaterial("Pricing FAQ", "Старая цена: 9000 тенге.");
        MaterialSummary second = createTextMaterial("Pricing FAQ", "Новая цена: 12000 тенге.");

        mockMvc.perform(delete("/api/materials/{id}", second.id()))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/materials"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.hasMore").value(false))
            .andExpect(jsonPath("$.items[0].id").value(first.id()))
            .andExpect(jsonPath("$.items[0].versionState").value("ACTIVE"))
            .andExpect(jsonPath("$.items[0].preview").value(containsString("9000")));
    }

    @Test
    void returnsOrderedLineageForMaterial() throws Exception {
        MaterialSummary first = createTextMaterial("Pricing FAQ", "Старая цена: 9000 тенге.");
        MaterialSummary second = createTextMaterial("Pricing FAQ", "Новая цена: 12000 тенге.");

        mockMvc.perform(get("/api/materials/{id}/lineage", first.id()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.requestedMaterialId").value(first.id()))
            .andExpect(jsonPath("$.activeMaterialId").value(second.id()))
            .andExpect(jsonPath("$.versions[0].id").value(second.id()))
            .andExpect(jsonPath("$.versions[1].id").value(first.id()))
            .andExpect(jsonPath("$.versions[1].supersedeReason").exists());
    }

    @Test
    void reindexesFailedActiveMaterial() throws Exception {
        MaterialSummary material = createTextMaterial("Pricing FAQ", "Цена: 12000 тенге.");

        jdbcTemplate.update(
            """
                UPDATE materials
                SET indexing_status = 'FAILED',
                    status_reason_code = 'embedding.provider_unavailable',
                    status_reason_message = 'Embedding provider is unavailable'
                WHERE id = ?
                """,
            java.util.UUID.fromString(material.id())
        );

        mockMvc.perform(post("/api/materials/{id}/reindex", material.id()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(material.id()))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.indexingAttempts").exists());
    }

    @Test
    void rejectsInvalidRechunkBatchLimit() throws Exception {
        mockMvc.perform(post("/api/materials/rechunk-active/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "limit": 0
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("material.rechunk_batch_invalid_limit"));
    }

    @Test
    void rejectsInvalidRechunkBatchCursor() throws Exception {
        mockMvc.perform(post("/api/materials/rechunk-active/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "cursor": "not-a-valid-cursor"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("material.rechunk_batch_invalid_cursor"));
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

    private MaterialSummary createTextMaterial(String title, String content) throws Exception {
        String responseBody = mockMvc.perform(post("/api/materials")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "%s",
                      "content": "%s"
                    }
                    """.formatted(title, content)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        return objectMapper.readValue(responseBody, MaterialSummary.class);
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
