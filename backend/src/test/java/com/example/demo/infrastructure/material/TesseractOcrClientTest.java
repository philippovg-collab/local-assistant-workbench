package com.example.demo.infrastructure.material;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.demo.api.ApiException;
import com.example.demo.config.OcrProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TesseractOcrClientTest {

    @TempDir
    Path tempDir;

    @Test
    void reportsUnavailableBinary() {
        OcrProperties properties = new OcrProperties();
        properties.setBinaryPath(tempDir.resolve("missing-tesseract").toString());
        TesseractOcrClient client = new TesseractOcrClient(properties);

        ApiException exception = assertThrows(ApiException.class, () ->
            client.extract(tempDir.resolve("image.png"), 1));

        assertEquals("material.ocr_unavailable", exception.getCode());
    }

    @Test
    void reportsTimeoutFromLongRunningProcess() throws Exception {
        Path script = tempDir.resolve("slow-ocr.sh");
        Files.writeString(script, "#!/bin/sh\nsleep 2\n");
        script.toFile().setExecutable(true);

        OcrProperties properties = new OcrProperties();
        properties.setBinaryPath(script.toString());
        properties.setTimeoutSeconds(1);
        TesseractOcrClient client = new TesseractOcrClient(properties);

        ApiException exception = assertThrows(ApiException.class, () ->
            client.extract(tempDir.resolve("image.png"), 1));

        assertEquals("material.ocr_timeout", exception.getCode());
    }

    @Test
    void reportsMissingLanguageData() throws Exception {
        Path script = tempDir.resolve("lang-error-ocr.sh");
        Files.writeString(script, "#!/bin/sh\necho 'Error opening data file kaz.traineddata' 1>&2\nexit 1\n");
        script.toFile().setExecutable(true);

        OcrProperties properties = new OcrProperties();
        properties.setBinaryPath(script.toString());
        TesseractOcrClient client = new TesseractOcrClient(properties);

        ApiException exception = assertThrows(ApiException.class, () ->
            client.extract(tempDir.resolve("image.png"), 1));

        assertEquals("material.ocr_language_data_missing", exception.getCode());
    }
}
