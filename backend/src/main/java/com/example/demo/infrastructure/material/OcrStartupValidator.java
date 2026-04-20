package com.example.demo.infrastructure.material;

import com.example.demo.service.material.OcrCapability;
import com.example.demo.service.material.port.OcrCapabilityProvider;

import com.example.demo.config.OcrProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class OcrStartupValidator implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(OcrStartupValidator.class);

    private final OcrProperties properties;
    private final OcrCapabilityProvider capabilityProvider;

    public OcrStartupValidator(OcrProperties properties, OcrCapabilityProvider capabilityProvider) {
        this.properties = properties;
        this.capabilityProvider = capabilityProvider;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (!properties.isEnabled()) {
            logger.info("OCR startup readiness skipped because app.ocr.enabled=false");
            return;
        }

        OcrCapability capability = capabilityProvider.currentCapability();
        if (capability.scannedPdfSupport()) {
            logger.info(
                "OCR startup readiness confirmed: binaryPath={} languages={} maxPages={}",
                properties.getBinaryPath(),
                capability.languages(),
                capability.maxPages()
            );
            return;
        }

        logger.warn(
            "OCR startup readiness failed: binaryPath={} code={} message={} languages={}",
            properties.getBinaryPath(),
            capability.reasonCode(),
            capability.reasonMessage(),
            capability.languages()
        );
    }
}
