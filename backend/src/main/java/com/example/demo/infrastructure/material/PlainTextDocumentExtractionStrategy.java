package com.example.demo.infrastructure.material;

import com.example.demo.service.material.DocumentBlock;
import com.example.demo.service.material.DocumentBlockBuilder;
import com.example.demo.model.DocumentBlockType;
import com.example.demo.service.material.DocumentParseResult;
import com.example.demo.service.material.DocumentParserProfile;
import com.example.demo.service.material.MaterialFormatRegistry;
import com.example.demo.service.material.MaterialMetadataHints;

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
            || (!StringUtils.hasText(extension) && isPlainTextMediaType(mediaType));
    }

    @Override
    public DocumentParseResult extract(String originalFileName, String mediaType, byte[] bytes) {
        List<DocumentBlock> blocks = DocumentBlockBuilder.fromText(
            new String(bytes, StandardCharsets.UTF_8),
            null,
            "plain-text",
            false,
            false,
            DocumentBlockType.NARRATIVE,
            0
        );
        return new DocumentParseResult(
            blocks,
            MaterialMetadataHints.empty(),
            List.of(),
            null,
            "plain-text",
            false,
            DocumentParserProfile.RICH_TEXT
        );
    }

    private boolean isPlainTextMediaType(String mediaType) {
        if (!StringUtils.hasText(mediaType)) {
            return false;
        }
        String normalized = mediaType.toLowerCase(Locale.ROOT);
        return formatRegistry.isPlainTextMediaType(normalized)
            && !normalized.contains("html")
            && !normalized.contains("csv");
    }
}
