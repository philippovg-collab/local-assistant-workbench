package com.example.demo.infrastructure.material;

import com.example.demo.service.material.DocumentParseResult;
import com.example.demo.service.material.MaterialFormatRegistry;
import com.example.demo.service.material.port.DocumentTextExtractor;

import com.example.demo.api.ApiException;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RoutingDocumentTextExtractor implements DocumentTextExtractor {

    private final List<DocumentTextExtractionStrategy> strategies;
    private final MaterialFormatRegistry formatRegistry;
    private final Detector detector = new DefaultDetector();

    @org.springframework.beans.factory.annotation.Autowired
    public RoutingDocumentTextExtractor(
        List<DocumentTextExtractionStrategy> strategies,
        MaterialFormatRegistry formatRegistry
    ) {
        this.strategies = strategies;
        this.formatRegistry = formatRegistry;
    }

    public RoutingDocumentTextExtractor(List<DocumentTextExtractionStrategy> strategies) {
        this(strategies, new MaterialFormatRegistry());
    }

    @Override
    public DocumentParseResult extract(String originalFileName, String mediaType, byte[] bytes) {
        String detectedMediaType = detectMediaType(originalFileName, bytes);
        String extension = formatRegistry.extensionOf(originalFileName);
        if (StringUtils.hasText(detectedMediaType) && !formatRegistry.isSupportedMediaType(detectedMediaType)) {
            throw unsupportedFormat();
        }
        if (!isSupportedHint(extension, mediaType) && !formatRegistry.isSupportedMediaType(detectedMediaType)) {
            throw unsupportedFormat();
        }

        String effectiveMediaType = StringUtils.hasText(detectedMediaType) ? detectedMediaType : mediaType;
        if (formatRegistry.isPdfMediaType(effectiveMediaType)) {
            return extractWithSupportedStrategy(originalFileName, "application/pdf", bytes);
        }

        if (formatRegistry.isPdfExtension(extension) && !formatRegistry.isPdfMediaType(effectiveMediaType)) {
            throw unsupportedFormat();
        }

        if (isClearlyMismatched(extension, effectiveMediaType)) {
            throw unsupportedFormat();
        }

        return extractWithSupportedStrategy(originalFileName, effectiveMediaType, bytes);
    }

    private DocumentParseResult extractWithSupportedStrategy(String originalFileName, String mediaType, byte[] bytes) {
        return strategies.stream()
            .filter(strategy -> strategy.supports(originalFileName, mediaType))
            .findFirst()
            .orElseThrow(this::unsupportedFormat)
            .extract(originalFileName, mediaType, bytes);
    }

    private String detectMediaType(String originalFileName, byte[] bytes) {
        Metadata metadata = new Metadata();
        if (StringUtils.hasText(originalFileName)) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, originalFileName);
        }
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes == null ? new byte[0] : bytes)) {
            MediaType detected = detector.detect(inputStream, metadata);
            return detected == null ? null : detected.toString();
        } catch (Exception exception) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "material.unsupported_format",
                "Unable to determine uploaded file format",
                exception
            );
        }
    }

    private boolean isSupportedHint(String extension, String mediaType) {
        return formatRegistry.isSupportedExtension(extension) || formatRegistry.isSupportedMediaType(mediaType);
    }

    private boolean isClearlyMismatched(String extension, String detectedMediaType) {
        if (!StringUtils.hasText(extension) || !formatRegistry.isSupportedMediaType(detectedMediaType)) {
            return false;
        }
        if (formatRegistry.isPlainTextExtension(extension)) {
            return !formatRegistry.isPlainTextMediaType(detectedMediaType);
        }
        if (formatRegistry.isTabularExtension(extension)) {
            return !(formatRegistry.isTabularMediaType(detectedMediaType)
                || ("csv".equals(extension) && formatRegistry.isPlainTextMediaType(detectedMediaType)));
        }
        if (formatRegistry.isPresentationExtension(extension)) {
            return !formatRegistry.isPresentationMediaType(detectedMediaType);
        }
        if (formatRegistry.isRichDocumentExtension(extension) && !formatRegistry.isPdfExtension(extension)) {
            return !formatRegistry.isRichDocumentMediaType(detectedMediaType);
        }
        return false;
    }

    private ApiException unsupportedFormat() {
        return new ApiException(
            HttpStatus.BAD_REQUEST,
            "material.unsupported_format",
            "Unsupported file format. Use one of the configured text or document formats, or paste text directly."
        );
    }
}
