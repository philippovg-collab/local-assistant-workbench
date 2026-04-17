package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.api.ApiException;
import com.example.demo.config.MaterialProperties;
import com.example.demo.config.OcrProperties;
import com.example.demo.config.RagProperties;
import com.example.demo.embedding.EmbeddingClient;
import com.example.demo.infrastructure.material.DocumentTextExtractor;
import com.example.demo.infrastructure.material.MaterialCatalogRepository;
import com.example.demo.infrastructure.material.MaterialFormatRegistry;
import com.example.demo.infrastructure.material.OcrCapabilityService;
import com.example.demo.infrastructure.material.OcrClient;
import com.example.demo.infrastructure.material.PdfDocumentExtractionStrategy;
import com.example.demo.infrastructure.material.PlainTextDocumentExtractionStrategy;
import com.example.demo.infrastructure.material.RoutingDocumentTextExtractor;
import com.example.demo.infrastructure.material.TesseractRuntimeProbe;
import com.example.demo.infrastructure.material.TikaDocumentTextExtractor;
import com.example.demo.model.MaterialIndexingStatus;
import com.example.demo.model.MaterialSummary;
import com.example.demo.model.MaterialVersionState;
import com.example.demo.support.DeterministicEmbeddingClient;
import com.example.demo.support.InMemoryMaterialRepository;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class MaterialServiceTest {

    @Test
    void deduplicatesIdenticalContentDuringIngestion() {
        MaterialService service = createService(new DeterministicEmbeddingClient());

        MaterialSummary first = service.saveText("Pricing", "Тариф стоит 12000 тенге.");
        MaterialSummary duplicate = service.saveText("Pricing copy", "Тариф стоит 12000 тенге.");

        assertEquals(first.id(), duplicate.id());
        assertEquals(1, service.listSummaries().size());
    }

    @Test
    void concurrentWritesPersistUniqueMaterials() throws Exception {
        MaterialService service = createService(new DeterministicEmbeddingClient());
        ExecutorService executor = Executors.newFixedThreadPool(6);

        try {
            List<Future<MaterialSummary>> futures = IntStream.range(0, 12)
                .mapToObj(index -> executor.submit(() -> service.saveText(
                    "Material " + index,
                    "Контент " + index + " с уникальным значением."
                )))
                .toList();

            for (Future<MaterialSummary> future : futures) {
                future.get();
            }

            assertEquals(12, service.listSummaries().size());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void acceptsMaterialAndMarksItFailedWhenEmbeddingGenerationFails() {
        MaterialService service = createService(new FailingEmbeddingClient());

        MaterialSummary submission = service.saveText(
            "Broken material",
            "Контент, для которого embedding client падает."
        );

        MaterialSummary summary = service.listSummaries().stream()
            .filter(item -> item.id().equals(submission.id()))
            .findFirst()
            .orElseThrow();

        assertEquals(MaterialIndexingStatus.PENDING, summary.status());
        assertEquals("embedding.provider_unavailable", summary.statusReasonCode());
        assertEquals("Embedding provider is unavailable in the test", summary.statusReasonMessage());
        assertEquals(1, service.listSummaries().size());
    }

    @Test
    void keepsDifferentFilesWithSameNameAsSeparateActiveLineages() {
        MaterialService service = createService(new DeterministicEmbeddingClient());

        MaterialSummary first = service.saveUpload(
            null,
            new MockMultipartFile(
                "file",
                "brief.txt",
                "text/plain",
                "Первый документ описывает график профилактики подстанции.".getBytes(StandardCharsets.UTF_8)
            )
        );
        MaterialSummary second = service.saveUpload(
            null,
            new MockMultipartFile(
                "file",
                "brief.txt",
                "text/plain",
                "Второй документ описывает регламент закупки оборудования.".getBytes(StandardCharsets.UTF_8)
            )
        );

        List<MaterialSummary> summaries = service.listSummaries();

        assertEquals(2, summaries.size());
        assertEquals(2, summaries.stream().filter(summary -> summary.versionState() == MaterialVersionState.ACTIVE).count());
        assertTrue(summaries.stream().anyMatch(summary -> summary.id().equals(first.id())));
        assertTrue(summaries.stream().anyMatch(summary -> summary.id().equals(second.id())));
    }

    @Test
    void deletingActiveVersionPromotesLatestSupersededVersion() {
        MaterialService service = createService(new DeterministicEmbeddingClient());

        MaterialSummary first = service.saveText("Pricing FAQ", "Старая цена: 9000 тенге.");
        MaterialSummary second = service.saveText("Pricing FAQ", "Новая цена: 12000 тенге.");

        service.delete(second.id());

        List<MaterialSummary> summaries = service.listSummaries();
        MaterialSummary remaining = summaries.getFirst();
        MaterialRetrievalResult retrievalResult = service.retrieveContext("9000");

        assertEquals(1, summaries.size());
        assertEquals(first.id(), remaining.id());
        assertEquals(MaterialVersionState.ACTIVE, remaining.versionState());
        assertEquals(1, retrievalResult.activeMaterialCount());
        assertEquals(1, retrievalResult.readyMaterialCount());
        assertFalse(retrievalResult.sources().isEmpty());
        assertTrue(retrievalResult.sources().getFirst().excerpt().contains("9000"));
    }

    @Test
    void rejectsBlankTextMaterials() {
        MaterialService service = createService(new DeterministicEmbeddingClient());

        ApiException exception = assertThrows(ApiException.class, () -> service.saveText("Pricing", "   "));

        assertEquals("material.empty_text", exception.getCode());
    }

    @Test
    void rejectsOversizedUploadsBeforeExtraction() {
        MaterialService service = createService(new DeterministicEmbeddingClient());
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "big.txt",
            "text/plain",
            new byte[2_000_001]
        );

        ApiException exception = assertThrows(ApiException.class, () -> service.saveUpload("Big", file));

        assertEquals("material.upload_too_large", exception.getCode());
    }

    @Test
    void rejectsUnsupportedUploadFormats() {
        MaterialService service = createService(new DeterministicEmbeddingClient());
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "diagram.png",
            "image/png",
            "not-supported".getBytes(StandardCharsets.UTF_8)
        );

        ApiException exception = assertThrows(ApiException.class, () -> service.saveUpload("Diagram", file));

        assertEquals("material.unsupported_format", exception.getCode());
    }

    @Test
    void rejectsBrokenDocxDocumentsWithExtractionError() throws Exception {
        MaterialService service = createService(new DeterministicEmbeddingClient());
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "broken.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            createBrokenDocx()
        );

        ApiException exception = assertThrows(ApiException.class, () -> service.saveUpload("Broken", file));

        assertEquals("material.extraction_failed", exception.getCode());
    }

    private MaterialService createService(EmbeddingClient embeddingClient) {
        MaterialProperties properties = new MaterialProperties();
        MaterialFormatRegistry formatRegistry = new MaterialFormatRegistry();
        OcrProperties ocrProperties = new OcrProperties();
        OcrClient ocrClient = (imagePath, pageNumber) -> "OCR fallback text for page " + pageNumber;
        OcrCapabilityService ocrCapabilityService = new OcrCapabilityService(
            ocrProperties,
            (binaryPath, timeoutSeconds) -> new TesseractRuntimeProbe.CommandResult(
                0,
                """
                List of available languages in "/tmp/tessdata" (3):
                kaz
                rus
                eng
                """,
                "",
                false
            )
        );
        DocumentTextExtractor extractor = new RoutingDocumentTextExtractor(List.of(
            new PlainTextDocumentExtractionStrategy(formatRegistry),
            new PdfDocumentExtractionStrategy(formatRegistry, ocrProperties, ocrClient, ocrCapabilityService),
            new TikaDocumentTextExtractor(properties, formatRegistry)
        ));
        InMemoryMaterialRepository repository = new InMemoryMaterialRepository();
        MaterialContentSupport contentSupport = new MaterialContentSupport(properties);
        MaterialIndexingService indexingService = new MaterialIndexingService(
            repository,
            repository,
            contentSupport,
            embeddingClient,
            properties,
            Runnable::run
        );
        return new MaterialService(
            new MaterialQueryService(
                repository,
                repository,
                properties,
                formatRegistry,
                ocrCapabilityService,
                contentSupport,
                indexingService
            ),
            new MaterialIngestionService(
                repository,
                repository,
                extractor,
                properties,
                contentSupport,
                indexingService
            ),
            new MaterialRetrievalService(
                repository,
                repository,
                embeddingClient,
                new RagProperties(),
                new HybridChunkRanker(),
                contentSupport
            )
        );
    }

    private static final class FailingEmbeddingClient implements EmbeddingClient {

        @Override
        public float[] embed(String input) {
            throw failure();
        }

        @Override
        public List<float[]> embedAll(List<String> inputs) {
            throw failure();
        }

        private ApiException failure() {
            return new ApiException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                "embedding.provider_unavailable",
                "Embedding provider is unavailable in the test"
            );
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
}
