package com.example.demo.infrastructure.material;

import com.example.demo.service.material.OcrCapability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.config.OcrProperties;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class OcrCapabilityServiceTest {

    @Test
    void reportsEmbeddedTextOnlyWhenOcrIsDisabled() {
        OcrProperties properties = createProperties();
        properties.setEnabled(false);

        OcrCapabilityService service = new OcrCapabilityService(properties, (binaryPath, timeoutSeconds) -> {
            throw new AssertionError("runtime probe should not run when OCR is disabled");
        });

        OcrCapability capability = service.currentCapability();

        assertEquals("embedded_text_only", capability.mode());
        assertFalse(capability.scannedPdfSupport());
        assertEquals("material.ocr_disabled", capability.reasonCode());
    }

    @Test
    void reportsEmbeddedTextOnlyWhenBinaryIsUnavailable() {
        OcrCapabilityService service = new OcrCapabilityService(
            createProperties(),
            (binaryPath, timeoutSeconds) -> {
                throw new IOException("missing binary");
            }
        );

        OcrCapability capability = service.currentCapability();

        assertEquals("embedded_text_only", capability.mode());
        assertFalse(capability.scannedPdfSupport());
        assertEquals("material.ocr_unavailable", capability.reasonCode());
    }

    @Test
    void reportsEmbeddedTextOnlyWhenLanguageDataIsMissing() {
        OcrCapabilityService service = new OcrCapabilityService(
            createProperties(),
            (binaryPath, timeoutSeconds) -> new TesseractRuntimeProbe.CommandResult(
                0,
                """
                List of available languages in "/tmp/tessdata" (2):
                eng
                rus
                """,
                "",
                false
            )
        );

        OcrCapability capability = service.currentCapability();

        assertEquals("embedded_text_only", capability.mode());
        assertFalse(capability.scannedPdfSupport());
        assertEquals("material.ocr_language_data_missing", capability.reasonCode());
    }

    @Test
    void reportsEmbeddedTextOnlyWhenReadinessCheckTimesOut() {
        OcrCapabilityService service = new OcrCapabilityService(
            createProperties(),
            (binaryPath, timeoutSeconds) -> new TesseractRuntimeProbe.CommandResult(
                -1,
                "",
                "Capability check timed out",
                true
            )
        );

        OcrCapability capability = service.currentCapability();

        assertEquals("embedded_text_only", capability.mode());
        assertFalse(capability.scannedPdfSupport());
        assertEquals("material.ocr_unavailable", capability.reasonCode());
    }

    @Test
    void reportsFullSupportWhenRuntimeLooksHealthy() {
        OcrCapabilityService service = new OcrCapabilityService(
            createProperties(),
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

        OcrCapability capability = service.currentCapability();

        assertEquals("embedded_text_and_ocr", capability.mode());
        assertTrue(capability.scannedPdfSupport());
        assertNull(capability.reasonCode());
        assertEquals(List.of("kaz", "rus", "eng"), capability.languages());
        assertEquals(12, capability.maxPages());
    }

    private OcrProperties createProperties() {
        OcrProperties properties = new OcrProperties();
        properties.setEnabled(true);
        properties.setBinaryPath("tesseract");
        properties.setLanguages("kaz+rus+eng");
        properties.setTimeoutSeconds(5);
        properties.setMaxPages(12);
        return properties;
    }
}
