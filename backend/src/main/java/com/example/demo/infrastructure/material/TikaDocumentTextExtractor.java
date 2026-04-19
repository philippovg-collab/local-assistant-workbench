package com.example.demo.infrastructure.material;

import com.example.demo.api.ApiException;
import com.example.demo.config.MaterialProperties;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ToXMLContentHandler;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

@Component
public class TikaDocumentTextExtractor implements DocumentTextExtractionStrategy {

    private final MaterialProperties properties;
    private final MaterialFormatRegistry formatRegistry;

    public TikaDocumentTextExtractor(
        MaterialProperties properties,
        MaterialFormatRegistry formatRegistry
    ) {
        this.properties = properties;
        this.formatRegistry = formatRegistry;
    }

    @Override
    public boolean supports(String originalFileName, String mediaType) {
        String extension = formatRegistry.extensionOf(originalFileName);
        return (formatRegistry.isRichDocumentExtension(extension) && !formatRegistry.isPdfExtension(extension))
            || formatRegistry.isTabularExtension(extension)
            || formatRegistry.isPresentationExtension(extension);
    }

    @Override
    public DocumentParseResult extract(String originalFileName, String mediaType, byte[] bytes) {
        DocumentParserProfile parserProfile = resolveParserProfile(originalFileName);
        ParsedXhtml parsedXhtml = extractStructuredDocument(originalFileName, bytes);
        List<DocumentBlock> blocks = mapBlocks(parsedXhtml.document(), parserProfile);

        if (parserProfile == DocumentParserProfile.TABULAR
            && blocks.stream().noneMatch(block -> block.type() == DocumentBlockType.TABLE)) {
            blocks = blocks.stream()
                .map(block -> block.type() == DocumentBlockType.TITLE
                    ? block
                    : new DocumentBlock(
                        block.index(),
                        DocumentBlockType.TABLE,
                        block.text(),
                        block.page(),
                        block.extractor(),
                        block.ocrUsed(),
                        block.confidence(),
                        block.level()
                    ))
                .toList();
        }

        if (blocks.isEmpty()) {
            blocks = DocumentBlockBuilder.fromText(
                parsedXhtml.plainText(),
                null,
                "tika",
                false,
                false,
                parserProfile == DocumentParserProfile.TABULAR ? DocumentBlockType.TABLE : DocumentBlockType.NARRATIVE,
                0
            );
        }

        if (blocks.isEmpty()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "material.extraction_failed",
                "Unable to extract text from the uploaded file"
            );
        }

        return new DocumentParseResult(
            blocks,
            MaterialMetadataHints.empty(),
            List.of(),
            null,
            "tika",
            false,
            parserProfile
        );
    }

    private DocumentParserProfile resolveParserProfile(String originalFileName) {
        String extension = formatRegistry.extensionOf(originalFileName);
        if (formatRegistry.isTabularExtension(extension)) {
            return DocumentParserProfile.TABULAR;
        }
        if (formatRegistry.isPresentationExtension(extension)) {
            return DocumentParserProfile.PRESENTATION;
        }
        return DocumentParserProfile.RICH_TEXT;
    }

    private ParsedXhtml extractStructuredDocument(String originalFileName, byte[] bytes) {
        ExecutorService executor = Executors.newSingleThreadExecutor(new ExtractionThreadFactory());

        try {
            var task = executor.submit(() -> parseWithTika(originalFileName, bytes));
            return task.get(properties.getExtractionTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException exception) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "material.extraction_timeout",
                "Document extraction timed out",
                exception
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "material.extraction_interrupted",
                "Document extraction was interrupted",
                exception
            );
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "material.extraction_failed",
                "Unable to extract text from the uploaded file",
                cause
            );
        } finally {
            executor.shutdownNow();
        }
    }

    private ParsedXhtml parseWithTika(String originalFileName, byte[] bytes) throws Exception {
        AutoDetectParser parser = new AutoDetectParser();
        Metadata metadata = new Metadata();
        if (StringUtils.hasText(originalFileName)) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, originalFileName);
        }

        ToXMLContentHandler handler = new ToXMLContentHandler();
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes)) {
            parser.parse(inputStream, handler, metadata, new ParseContext());
        }

        String xhtml = handler.toString();
        Document document = parseXmlDocument(xhtml);
        return new ParsedXhtml(document, document.getDocumentElement() == null ? "" : normalizeText(document.getDocumentElement().getTextContent()));
    }

    private Document parseXmlDocument(String xhtml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        try (StringReader reader = new StringReader(xhtml)) {
            return factory.newDocumentBuilder().parse(new InputSource(reader));
        }
    }

    private List<DocumentBlock> mapBlocks(Document document, DocumentParserProfile parserProfile) {
        Element root = document.getDocumentElement();
        if (root == null) {
            return List.of();
        }

        List<DocumentBlock> blocks = new ArrayList<>();
        collectBlocks(root, parserProfile, blocks);
        return reindex(blocks);
    }

    private void collectBlocks(Node node, DocumentParserProfile parserProfile, List<DocumentBlock> blocks) {
        if (!(node instanceof Element element)) {
            return;
        }

        String name = tagName(element);
        if (!StringUtils.hasText(name) || isContainerOnly(name)) {
            for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
                collectBlocks(child, parserProfile, blocks);
            }
            return;
        }

        if (isHeading(name)) {
            addElementBlock(blocks, element, DocumentBlockType.TITLE, headingLevel(name), parserProfile);
            return;
        }

        if ("table".equals(name)) {
            String tableText = tableText(element);
            if (StringUtils.hasText(tableText)) {
                blocks.add(new DocumentBlock(
                    blocks.size(),
                    DocumentBlockType.TABLE,
                    tableText,
                    null,
                    "tika",
                    false,
                    DocumentBlockConfidence.HIGH,
                    null
                ));
            }
            return;
        }

        if ("ul".equals(name) || "ol".equals(name)) {
            String listText = listText(element);
            if (StringUtils.hasText(listText)) {
                blocks.add(new DocumentBlock(
                    blocks.size(),
                    parserProfile == DocumentParserProfile.PRESENTATION ? DocumentBlockType.SLIDE : DocumentBlockType.LIST,
                    listText,
                    null,
                    "tika",
                    false,
                    DocumentBlockConfidence.HIGH,
                    1
                ));
            }
            return;
        }

        if (isParagraphLike(name)) {
            addParagraphBlock(blocks, element, parserProfile);
            return;
        }

        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            collectBlocks(child, parserProfile, blocks);
        }
    }

    private void addParagraphBlock(List<DocumentBlock> blocks, Element element, DocumentParserProfile parserProfile) {
        String text = normalizeText(element.getTextContent());
        if (!StringUtils.hasText(text)) {
            return;
        }

        DocumentBlockType fallbackType = switch (parserProfile) {
            case TABULAR -> DocumentBlockType.CAPTION;
            case PRESENTATION -> DocumentBlockType.SLIDE;
            case RICH_TEXT -> DocumentBlockType.NARRATIVE;
            case PDF -> DocumentBlockType.NARRATIVE;
        };
        DocumentBlockType type = parserProfile == DocumentParserProfile.PRESENTATION
            ? classifyPresentationText(text)
            : DocumentBlockHeuristics.classify(text, fallbackType);
        Integer level = DocumentBlockHeuristics.levelFor(type, text);
        blocks.add(new DocumentBlock(
            blocks.size(),
            type,
            text,
            null,
            "tika",
            false,
            DocumentBlockConfidence.HIGH,
            level
        ));
    }

    private void addElementBlock(
        List<DocumentBlock> blocks,
        Element element,
        DocumentBlockType blockType,
        Integer level,
        DocumentParserProfile parserProfile
    ) {
        String text = normalizeText(element.getTextContent());
        if (!StringUtils.hasText(text)) {
            return;
        }

        DocumentBlockType resolvedType = parserProfile == DocumentParserProfile.PRESENTATION && blockType == DocumentBlockType.TITLE
            ? DocumentBlockType.TITLE
            : blockType;
        blocks.add(new DocumentBlock(
            blocks.size(),
            resolvedType,
            text,
            null,
            "tika",
            false,
            DocumentBlockConfidence.HIGH,
            level
        ));
    }

    private DocumentBlockType classifyPresentationText(String text) {
        if (DocumentBlockHeuristics.looksLikeCaption(text)) {
            return DocumentBlockType.CAPTION;
        }
        if (DocumentBlockHeuristics.looksLikeTitle(text)) {
            return DocumentBlockType.TITLE;
        }
        return DocumentBlockType.SLIDE;
    }

    private List<DocumentBlock> reindex(List<DocumentBlock> rawBlocks) {
        List<DocumentBlock> normalizedBlocks = new ArrayList<>();
        int index = 0;
        for (DocumentBlock block : rawBlocks) {
            if (block == null || !StringUtils.hasText(block.text())) {
                continue;
            }
            normalizedBlocks.add(new DocumentBlock(
                index++,
                block.type(),
                normalizeText(block.text()),
                block.page(),
                block.extractor(),
                block.ocrUsed(),
                block.confidence(),
                block.level()
            ));
        }
        return List.copyOf(normalizedBlocks);
    }

    private boolean isContainerOnly(String name) {
        return "html".equals(name)
            || "body".equals(name)
            || "head".equals(name)
            || "meta".equals(name)
            || "style".equals(name)
            || "script".equals(name);
    }

    private boolean isHeading(String name) {
        return name.length() == 2 && name.charAt(0) == 'h' && Character.isDigit(name.charAt(1));
    }

    private Integer headingLevel(String name) {
        return Integer.parseInt(name.substring(1));
    }

    private boolean isParagraphLike(String name) {
        return "p".equals(name)
            || "div".equals(name)
            || "span".equals(name)
            || "pre".equals(name)
            || "blockquote".equals(name)
            || "li".equals(name);
    }

    private String tagName(Element element) {
        String localName = element.getLocalName();
        if (StringUtils.hasText(localName)) {
            return localName.toLowerCase(Locale.ROOT);
        }
        return element.getTagName().toLowerCase(Locale.ROOT);
    }

    private String listText(Element listElement) {
        List<String> items = new ArrayList<>();
        for (Node child = listElement.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element item && "li".equals(tagName(item))) {
                String text = normalizeText(item.getTextContent());
                if (StringUtils.hasText(text)) {
                    items.add("- " + text);
                }
            }
        }
        return String.join("\n", items);
    }

    private String tableText(Element tableElement) {
        List<String> rows = new ArrayList<>();
        for (Node child = tableElement.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element row && ("tr".equals(tagName(row)) || "thead".equals(tagName(row)) || "tbody".equals(tagName(row)))) {
                collectTableRows(row, rows);
            }
        }
        return String.join("\n", rows);
    }

    private void collectTableRows(Element element, List<String> rows) {
        String name = tagName(element);
        if ("tr".equals(name)) {
            List<String> cells = new ArrayList<>();
            for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child instanceof Element cell) {
                    String cellName = tagName(cell);
                    if ("td".equals(cellName) || "th".equals(cellName)) {
                        String text = normalizeText(cell.getTextContent());
                        if (StringUtils.hasText(text)) {
                            cells.add(text);
                        }
                    }
                }
            }
            if (!cells.isEmpty()) {
                rows.add(String.join("\t", cells));
            }
            return;
        }

        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element childElement) {
                collectTableRows(childElement, rows);
            }
        }
    }

    private String normalizeText(String rawText) {
        if (rawText == null) {
            return "";
        }
        return rawText
            .replace('\u00A0', ' ')
            .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
            .replaceAll("\\n{3,}", "\n\n")
            .trim();
    }

    private record ParsedXhtml(
        Document document,
        String plainText
    ) {
    }

    private static final class ExtractionThreadFactory implements ThreadFactory {

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "material-extraction");
            thread.setDaemon(true);
            return thread;
        }
    }
}
