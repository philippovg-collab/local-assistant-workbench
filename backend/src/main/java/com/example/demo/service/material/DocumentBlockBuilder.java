package com.example.demo.service.material;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.util.StringUtils;

public final class DocumentBlockBuilder {

    private DocumentBlockBuilder() {
    }

    public static List<DocumentBlock> fromText(
        String text,
        Integer page,
        String extractor,
        boolean ocrUsed,
        boolean markdownLike,
        DocumentBlockType defaultType,
        int startIndex
    ) {
        String normalized = text == null ? "" : text.replace("\r\n", "\n").trim();
        if (!StringUtils.hasText(normalized)) {
            return List.of();
        }

        List<DocumentBlock> blocks = new ArrayList<>();
        StringBuilder currentText = new StringBuilder();
        DocumentBlockType currentType = null;
        Integer currentLevel = null;
        int nextIndex = startIndex;

        for (String rawLine : normalized.split("\n")) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (!StringUtils.hasText(line)) {
                nextIndex = flushBlock(blocks, currentText, currentType, currentLevel, page, extractor, ocrUsed, nextIndex);
                currentType = null;
                currentLevel = null;
                continue;
            }

            ParsedLine parsedLine = parseLine(line, markdownLike, defaultType);
            if (parsedLine.standalone()) {
                nextIndex = flushBlock(blocks, currentText, currentType, currentLevel, page, extractor, ocrUsed, nextIndex);
                blocks.add(toBlock(nextIndex++, parsedLine.type(), parsedLine.text(), page, extractor, ocrUsed, parsedLine.level()));
                currentType = null;
                currentLevel = null;
                continue;
            }

            if (currentType != null
                && (currentType != parsedLine.type() || !Objects.equals(currentLevel, parsedLine.level()))) {
                nextIndex = flushBlock(blocks, currentText, currentType, currentLevel, page, extractor, ocrUsed, nextIndex);
                currentType = null;
                currentLevel = null;
            }

            if (currentType == null) {
                currentType = parsedLine.type();
                currentLevel = parsedLine.level();
            }

            if (!currentText.isEmpty()) {
                currentText.append('\n');
            }
            currentText.append(parsedLine.text());
        }

        flushBlock(blocks, currentText, currentType, currentLevel, page, extractor, ocrUsed, nextIndex);
        return List.copyOf(blocks);
    }

    private static int flushBlock(
        List<DocumentBlock> blocks,
        StringBuilder currentText,
        DocumentBlockType currentType,
        Integer currentLevel,
        Integer page,
        String extractor,
        boolean ocrUsed,
        int nextIndex
    ) {
        if (currentType != null && StringUtils.hasText(currentText.toString())) {
            blocks.add(toBlock(nextIndex++, currentType, currentText.toString(), page, extractor, ocrUsed, currentLevel));
        }
        currentText.setLength(0);
        return nextIndex;
    }

    private static DocumentBlock toBlock(
        int index,
        DocumentBlockType type,
        String text,
        Integer page,
        String extractor,
        boolean ocrUsed,
        Integer level
    ) {
        return new DocumentBlock(
            index,
            type,
            DocumentBlockHeuristics.normalizeText(text),
            page,
            extractor,
            ocrUsed,
            DocumentBlockHeuristics.confidenceFor(ocrUsed),
            level
        );
    }

    private static ParsedLine parseLine(String line, boolean markdownLike, DocumentBlockType defaultType) {
        if (markdownLike && line.startsWith("#")) {
            int level = 0;
            while (level < line.length() && line.charAt(level) == '#') {
                level++;
            }
            String titleText = line.substring(level).trim();
            return new ParsedLine(DocumentBlockType.TITLE, titleText, level, true);
        }

        DocumentBlockType classified = DocumentBlockHeuristics.classify(line, defaultType);
        Integer level = DocumentBlockHeuristics.levelFor(classified, line);
        boolean standalone = classified == DocumentBlockType.TITLE
            || classified == DocumentBlockType.QA
            || classified == DocumentBlockType.APPENDIX
            || classified == DocumentBlockType.CAPTION;
        return new ParsedLine(classified, line, level, standalone);
    }

    private record ParsedLine(
        DocumentBlockType type,
        String text,
        Integer level,
        boolean standalone
    ) {
    }
}
