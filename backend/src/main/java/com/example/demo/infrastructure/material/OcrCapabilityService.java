package com.example.demo.infrastructure.material;

import com.example.demo.service.material.OcrCapability;
import com.example.demo.service.material.port.OcrCapabilityProvider;

import com.example.demo.config.OcrProperties;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OcrCapabilityService implements OcrCapabilityProvider {

    private static final long CACHE_TTL_NANOS = TimeUnit.SECONDS.toNanos(60);

    private final OcrProperties properties;
    private final TesseractRuntimeProbe runtimeProbe;

    private volatile CachedCapability cachedCapability;

    public OcrCapabilityService(OcrProperties properties, TesseractRuntimeProbe runtimeProbe) {
        this.properties = properties;
        this.runtimeProbe = runtimeProbe;
    }

    @Override
    public OcrCapability currentCapability() {
        long now = System.nanoTime();
        CachedCapability cached = cachedCapability;
        if (cached != null && now < cached.expiresAtNanos()) {
            return cached.capability();
        }

        synchronized (this) {
            cached = cachedCapability;
            now = System.nanoTime();
            if (cached != null && now < cached.expiresAtNanos()) {
                return cached.capability();
            }

            OcrCapability capability = probeCapability();
            cachedCapability = new CachedCapability(capability, now + CACHE_TTL_NANOS);
            return capability;
        }
    }

    private OcrCapability probeCapability() {
        List<String> configuredLanguages = properties.languageList();
        if (!properties.isEnabled()) {
            return OcrCapability.embeddedTextOnly(
                "material.ocr_disabled",
                "OCR is disabled on the server.",
                configuredLanguages,
                properties.getMaxPages()
            );
        }

        if (configuredLanguages.isEmpty()) {
            return OcrCapability.embeddedTextOnly(
                "material.ocr_language_data_missing",
                "OCR languages are not configured on the server.",
                configuredLanguages,
                properties.getMaxPages()
            );
        }

        try {
            TesseractRuntimeProbe.CommandResult result = runtimeProbe.listLanguages(
                properties.getBinaryPath(),
                properties.getTimeoutSeconds()
            );

            if (result.timedOut()) {
                return OcrCapability.embeddedTextOnly(
                    "material.ocr_unavailable",
                    "Tesseract OCR readiness check timed out at '" + properties.getBinaryPath() + "'.",
                    configuredLanguages,
                    properties.getMaxPages()
                );
            }

            if (result.exitCode() != 0) {
                return OcrCapability.embeddedTextOnly(
                    "material.ocr_unavailable",
                    "Tesseract OCR binary is unavailable at '" + properties.getBinaryPath() + "'" + formatErrorSuffix(result.standardError()),
                    configuredLanguages,
                    properties.getMaxPages()
                );
            }

            Set<String> availableLanguages = parseAvailableLanguages(result.standardOutput());
            List<String> missingLanguages = configuredLanguages.stream()
                .filter(language -> !availableLanguages.contains(language.toLowerCase(Locale.ROOT)))
                .toList();

            if (!missingLanguages.isEmpty()) {
                return OcrCapability.embeddedTextOnly(
                    "material.ocr_language_data_missing",
                    "Tesseract language data is missing for configured OCR languages: " + String.join("+", missingLanguages),
                    configuredLanguages,
                    properties.getMaxPages()
                );
            }

            return OcrCapability.embeddedTextAndOcr(configuredLanguages, properties.getMaxPages());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return OcrCapability.embeddedTextOnly(
                "material.ocr_unavailable",
                "OCR capability check was interrupted.",
                configuredLanguages,
                properties.getMaxPages()
            );
        } catch (IOException exception) {
            return OcrCapability.embeddedTextOnly(
                "material.ocr_unavailable",
                "Tesseract OCR binary is unavailable at '" + properties.getBinaryPath() + "'.",
                configuredLanguages,
                properties.getMaxPages()
            );
        }
    }

    private Set<String> parseAvailableLanguages(String output) {
        return output.lines()
            .map(String::trim)
            .filter(StringUtils::hasText)
            .filter(line -> !line.toLowerCase(Locale.ROOT).startsWith("list of available languages"))
            .map(line -> line.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());
    }

    private String formatErrorSuffix(String error) {
        if (!StringUtils.hasText(error)) {
            return "";
        }

        return ": " + error.replaceAll("\\s+", " ").trim();
    }

    private record CachedCapability(OcrCapability capability, long expiresAtNanos) {
    }
}
