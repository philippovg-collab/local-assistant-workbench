package com.example.demo.infrastructure.material;

import com.example.demo.api.ApiException;
import com.example.demo.config.OcrProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class TesseractOcrClient implements OcrClient {

    private final OcrProperties properties;

    public TesseractOcrClient(OcrProperties properties) {
        this.properties = properties;
    }

    @Override
    public String extract(Path imagePath, int pageNumber) {
        List<String> command = List.of(
            properties.getBinaryPath(),
            imagePath.toString(),
            "stdout",
            "-l",
            properties.getLanguages(),
            "--psm",
            "3"
        );

        try {
            Process process = new ProcessBuilder(command).start();
            boolean finished = process.waitFor(properties.getTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "material.ocr_timeout",
                    "OCR timed out while processing PDF page " + pageNumber
                );
            }

            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String error = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();

            if (process.exitValue() != 0) {
                String normalizedError = error.toLowerCase(Locale.ROOT);
                if (normalizedError.contains("error opening data file")
                    || normalizedError.contains("failed loading language")) {
                    throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "material.ocr_language_data_missing",
                        "Tesseract language data is missing for configured OCR languages: " + properties.getLanguages()
                    );
                }

                throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "material.ocr_failed",
                    "OCR failed while processing PDF page " + pageNumber + formatErrorSuffix(error)
                );
            }

            return output;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "material.ocr_interrupted",
                "OCR processing was interrupted",
                exception
            );
        } catch (IOException exception) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "material.ocr_unavailable",
                "Tesseract OCR binary is unavailable at '" + properties.getBinaryPath() + "'",
                exception
            );
        }
    }

    private String formatErrorSuffix(String error) {
        if (!StringUtils.hasText(error)) {
            return "";
        }

        return ": " + error.replaceAll("\\s+", " ").trim();
    }
}
