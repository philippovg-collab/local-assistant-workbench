package com.example.demo.infrastructure.material;

import com.example.demo.api.ApiException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class RoutingDocumentTextExtractor implements DocumentTextExtractor {

    private final List<DocumentTextExtractionStrategy> strategies;

    public RoutingDocumentTextExtractor(List<DocumentTextExtractionStrategy> strategies) {
        this.strategies = strategies;
    }

    @Override
    public ExtractedDocument extract(String originalFileName, String mediaType, byte[] bytes) {
        return strategies.stream()
            .filter(strategy -> strategy.supports(originalFileName, mediaType))
            .findFirst()
            .orElseThrow(() -> new ApiException(
                HttpStatus.BAD_REQUEST,
                "material.unsupported_format",
                "Unsupported file format. Use one of the configured text or document formats, or paste text directly."
            ))
            .extract(originalFileName, mediaType, bytes);
    }
}
