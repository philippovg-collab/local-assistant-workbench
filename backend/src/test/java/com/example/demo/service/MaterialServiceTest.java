package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.config.MaterialProperties;
import com.example.demo.config.OcrProperties;
import com.example.demo.infrastructure.material.DocumentTextExtractor;
import com.example.demo.infrastructure.material.FileMaterialRepository;
import com.example.demo.infrastructure.material.MaterialFormatRegistry;
import com.example.demo.infrastructure.material.OcrCapabilityService;
import com.example.demo.infrastructure.material.OcrClient;
import com.example.demo.infrastructure.material.PdfDocumentExtractionStrategy;
import com.example.demo.infrastructure.material.PlainTextDocumentExtractionStrategy;
import com.example.demo.infrastructure.material.RoutingDocumentTextExtractor;
import com.example.demo.infrastructure.material.TesseractRuntimeProbe;
import com.example.demo.infrastructure.material.TikaDocumentTextExtractor;
import com.example.demo.model.MaterialSummary;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MaterialServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
        .findAndRegisterModules()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @TempDir
    Path tempDir;

    @Test
    void deduplicatesIdenticalContentDuringIngestion() {
        MaterialService service = createService();

        MaterialSummary first = service.saveText("Pricing", "Тариф стоит 12000 тенге.");
        MaterialSummary duplicate = service.saveText("Pricing copy", "Тариф стоит 12000 тенге.");

        assertEquals(first.id(), duplicate.id());
        assertEquals(1, service.listSummaries().size());
    }

    @Test
    void concurrentWritesPersistUniqueMaterials() throws Exception {
        MaterialService service = createService();
        ExecutorService executor = Executors.newFixedThreadPool(6);

        try {
            List<Future<MaterialSummary>> futures = IntStream.range(0, 12)
                .mapToObj(index -> executor.submit(() -> service.saveText(
                    "Material " + index,
                    "Контент " + index + " с уникальным значением."
                )))
                .toList();

            Set<String> ids = new HashSet<>();
            for (Future<MaterialSummary> future : futures) {
                ids.add(future.get().id());
            }

            assertEquals(12, ids.size());
            assertEquals(12, service.listSummaries().size());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void quarantinesBrokenMaterialFilesInsteadOfFailingTheWholeBucket() throws Exception {
        Files.createDirectories(tempDir.resolve("materials"));
        Files.writeString(tempDir.resolve("materials/broken.json"), "{not-json");

        MaterialService service = createService();

        assertTrue(service.listSummaries().isEmpty());
        try (var stream = Files.list(tempDir.resolve("quarantine").resolve("materials"))) {
            assertTrue(stream.findAny().isPresent());
        }
    }

    private MaterialService createService() {
        MaterialProperties properties = new MaterialProperties();
        FileMaterialRepository repository = new FileMaterialRepository(objectMapper, tempDir.toString());
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
        return new MaterialService(repository, extractor, properties, formatRegistry, ocrCapabilityService);
    }
}
