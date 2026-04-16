package com.example.demo.controller;

import com.example.demo.config.OcrProperties;
import com.example.demo.infrastructure.material.OcrCapability;
import com.example.demo.infrastructure.material.OcrCapabilityProvider;
import com.example.demo.model.HealthResponse;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final OcrProperties ocrProperties;
    private final OcrCapabilityProvider ocrCapabilityProvider;

    public HealthController(OcrProperties ocrProperties, OcrCapabilityProvider ocrCapabilityProvider) {
        this.ocrProperties = ocrProperties;
        this.ocrCapabilityProvider = ocrCapabilityProvider;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        OcrCapability capability = ocrCapabilityProvider.currentCapability();
        boolean ocrReady = !ocrProperties.isEnabled() || capability.scannedPdfSupport();
        String ocrStatus = !ocrProperties.isEnabled()
            ? "DISABLED"
            : capability.scannedPdfSupport() ? "UP" : "DOWN";

        return new HealthResponse(
            "spring-backend",
            ocrReady ? "UP" : "DEGRADED",
            Instant.now().toString(),
            ocrStatus,
            capability.reasonCode(),
            capability.reasonMessage(),
            capability.languages()
        );
    }
}
