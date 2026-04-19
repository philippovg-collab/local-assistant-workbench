package com.example.demo.infrastructure.material;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class MaterialFormatRegistry {

    private static final List<String> PLAIN_TEXT_EXTENSIONS = List.of(
        "txt",
        "md",
        "markdown",
        "json",
        "xml",
        "yaml",
        "yml",
        "log",
        "sql",
        "java",
        "kt",
        "js",
        "ts",
        "tsx",
        "jsx",
        "py",
        "properties"
    );

    private static final List<String> TABULAR_EXTENSIONS = List.of(
        "csv",
        "xls",
        "xlsx"
    );

    private static final List<String> RICH_DOCUMENT_EXTENSIONS = List.of(
        "html",
        "htm",
        "doc",
        "docx",
        "rtf",
        "odt",
        "pdf"
    );

    private static final List<String> PRESENTATION_EXTENSIONS = List.of(
        "ppt",
        "pptx"
    );

    private static final List<String> ACCEPTED_MIME_HINTS = List.of(
        "text/plain",
        "text/markdown",
        "text/csv",
        "application/json",
        "application/xml",
        "text/xml",
        "application/x-yaml",
        "text/yaml",
        "text/html",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/rtf",
        "text/rtf",
        "application/vnd.oasis.opendocument.text",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "application/pdf"
    );

    private static final Set<String> PLAIN_TEXT_EXTENSION_SET = Set.copyOf(PLAIN_TEXT_EXTENSIONS);
    private static final Set<String> TABULAR_EXTENSION_SET = Set.copyOf(TABULAR_EXTENSIONS);
    private static final Set<String> RICH_DOCUMENT_EXTENSION_SET = Set.copyOf(RICH_DOCUMENT_EXTENSIONS);
    private static final Set<String> PRESENTATION_EXTENSION_SET = Set.copyOf(PRESENTATION_EXTENSIONS);
    private static final List<String> ACCEPTED_EXTENSIONS = buildAcceptedExtensions();

    public List<String> acceptedExtensions() {
        return ACCEPTED_EXTENSIONS;
    }

    public List<String> acceptedMimeHints() {
        return ACCEPTED_MIME_HINTS;
    }

    public boolean supportsRichDocuments() {
        return true;
    }

    public boolean isPdfExtension(String extension) {
        return "pdf".equals(normalizeExtension(extension));
    }

    public boolean isPlainTextExtension(String extension) {
        return PLAIN_TEXT_EXTENSION_SET.contains(normalizeExtension(extension));
    }

    public boolean isTabularExtension(String extension) {
        return TABULAR_EXTENSION_SET.contains(normalizeExtension(extension));
    }

    public boolean isRichDocumentExtension(String extension) {
        return RICH_DOCUMENT_EXTENSION_SET.contains(normalizeExtension(extension));
    }

    public boolean isPresentationExtension(String extension) {
        return PRESENTATION_EXTENSION_SET.contains(normalizeExtension(extension));
    }

    public boolean isSupportedExtension(String extension) {
        String normalized = normalizeExtension(extension);
        return PLAIN_TEXT_EXTENSION_SET.contains(normalized)
            || TABULAR_EXTENSION_SET.contains(normalized)
            || RICH_DOCUMENT_EXTENSION_SET.contains(normalized)
            || PRESENTATION_EXTENSION_SET.contains(normalized);
    }

    public String extensionOf(String originalFileName) {
        if (!StringUtils.hasText(originalFileName) || !originalFileName.contains(".")) {
            return "";
        }

        return normalizeExtension(originalFileName.substring(originalFileName.lastIndexOf('.') + 1));
    }

    private String normalizeExtension(String extension) {
        return extension == null ? "" : extension.trim().toLowerCase(Locale.ROOT);
    }

    private static List<String> buildAcceptedExtensions() {
        LinkedHashSet<String> extensions = new LinkedHashSet<>();
        extensions.addAll(PLAIN_TEXT_EXTENSIONS);
        extensions.addAll(TABULAR_EXTENSIONS);
        extensions.addAll(RICH_DOCUMENT_EXTENSIONS);
        extensions.addAll(PRESENTATION_EXTENSIONS);
        return List.copyOf(extensions);
    }
}
