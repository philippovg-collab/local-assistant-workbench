package com.example.demo.infrastructure.material;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PlainTextDocumentExtractionStrategy implements DocumentTextExtractionStrategy {

    private final MaterialFormatRegistry formatRegistry;

    public PlainTextDocumentExtractionStrategy(MaterialFormatRegistry formatRegistry) {
        this.formatRegistry = formatRegistry;
    }

    @Override
    public boolean supports(String originalFileName, String mediaType) {
        String extension = formatRegistry.extensionOf(originalFileName);
        return formatRegistry.isPlainTextExtension(extension)
            || (!StringUtils.hasText(extension)
                && StringUtils.hasText(mediaType)
                && mediaType.toLowerCase(Locale.ROOT).startsWith("text/")
                && !mediaType.toLowerCase(Locale.ROOT).contains("html"));
    }

    @Override
    public ExtractedDocument extract(String originalFileName, String mediaType, byte[] bytes) {
        return new ExtractedDocument(
            List.of(new ExtractedDocumentSegment(new String(bytes, StandardCharsets.UTF_8), null, "plain-text", false)),
            "plain-text",
            false,
            null
        );
    }
}
