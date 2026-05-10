package com.example.demo.service;

import com.example.demo.service.material.ChunkProfile;
import com.example.demo.service.material.DocumentBlock;
import com.example.demo.model.DocumentBlockConfidence;
import com.example.demo.model.DocumentBlockType;
import com.example.demo.service.material.StoredMaterialChunk;
import com.example.demo.service.material.StoredMaterialSegment;

import com.example.demo.config.MaterialProperties;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.util.StringUtils;

public class StructuredChunkingStrategy implements ChunkingStrategy {

    private static final String BODY_SEPARATOR = "\n";
    private static final String NARRATIVE_SEPARATOR = " ";
    private static final int MIN_BODY_BUDGET = 40;

    @Override
    public ChunkProfile profile() {
        return ChunkProfile.STRUCTURED_V1;
    }

    @Override
    public List<StoredMaterialChunk> buildChunks(
        List<StoredMaterialSegment> segments,
        MaterialProperties properties,
        MaterialContentSupport contentSupport
    ) {
        return buildChunksFromBlocks(contentSupport.reconstructBlocks(segments), properties, contentSupport);
    }

    @Override
    public List<StoredMaterialChunk> buildChunksFromBlocks(
        List<DocumentBlock> blocks,
        MaterialProperties properties,
        MaterialContentSupport contentSupport
    ) {
        List<DocumentBlock> normalizedBlocks = contentSupport.normalizeBlocks(blocks);
        if (normalizedBlocks.isEmpty()) {
            return List.of();
        }

        List<ChunkDraft> drafts = new ArrayList<>();
        List<String> headingTrail = new ArrayList<>();
        List<String> sectionPath = new ArrayList<>();
        NarrativeBuffer narrativeBuffer = null;
        int tableCounter = 0;
        int slideCounter = 0;

        for (DocumentBlock block : normalizedBlocks) {
            if (block.type() == DocumentBlockType.TITLE) {
                narrativeBuffer = flushNarrative(narrativeBuffer, drafts, properties, contentSupport);
                updateHeadingTrail(headingTrail, sectionPath, block, contentSupport);
                continue;
            }

            if (block.type() == DocumentBlockType.NARRATIVE) {
                narrativeBuffer = appendNarrative(
                    narrativeBuffer,
                    block,
                    List.copyOf(sectionPath),
                    List.copyOf(headingTrail),
                    drafts,
                    properties,
                    contentSupport
                );
                continue;
            }

            narrativeBuffer = flushNarrative(narrativeBuffer, drafts, properties, contentSupport);

            String tableId = null;
            String slideId = null;
            if (block.type() == DocumentBlockType.TABLE) {
                tableId = "table-" + (++tableCounter);
            } else if (block.type() == DocumentBlockType.SLIDE) {
                slideId = "slide-" + (++slideCounter);
            }

            if (block.type() == DocumentBlockType.CAPTION
                && attachCaptionToPrevious(drafts, block, List.copyOf(sectionPath), List.copyOf(headingTrail))) {
                continue;
            }

            appendSpecialChunks(
                drafts,
                block,
                properties,
                contentSupport,
                List.copyOf(sectionPath),
                List.copyOf(headingTrail),
                tableId,
                slideId
            );
        }

        flushNarrative(narrativeBuffer, drafts, properties, contentSupport);

        List<StoredMaterialChunk> chunks = new ArrayList<>();
        for (int index = 0; index < drafts.size() && index < properties.getMaxChunks(); index++) {
            ChunkDraft draft = drafts.get(index);
            String normalizedText = contentSupport.normalizeStoredContent(draft.text());
            if (!StringUtils.hasText(normalizedText)) {
                continue;
            }
            chunks.add(new StoredMaterialChunk(
                index,
                normalizedText,
                List.copyOf(contentSupport.tokenize(normalizedText)),
                draft.page(),
                contentSupport.normalizeExtractor(draft.extractor()),
                draft.ocrUsed(),
                draft.chunkType(),
                draft.sectionPath(),
                draft.headingTrail(),
                draft.tableId(),
                draft.slideId(),
                draft.parserConfidence()
            ));
        }
        return List.copyOf(chunks);
    }

    private NarrativeBuffer appendNarrative(
        NarrativeBuffer current,
        DocumentBlock block,
        List<String> sectionPath,
        List<String> headingTrail,
        List<ChunkDraft> drafts,
        MaterialProperties properties,
        MaterialContentSupport contentSupport
    ) {
        if (current == null) {
            return NarrativeBuffer.start(block, sectionPath, headingTrail);
        }

        if (!current.compatibleWith(block, sectionPath, headingTrail)) {
            flushNarrative(current, drafts, properties, contentSupport);
            return NarrativeBuffer.start(block, sectionPath, headingTrail);
        }

        return current.append(block);
    }

    private NarrativeBuffer flushNarrative(
        NarrativeBuffer buffer,
        List<ChunkDraft> drafts,
        MaterialProperties properties,
        MaterialContentSupport contentSupport
    ) {
        if (buffer == null || !StringUtils.hasText(buffer.text())) {
            return null;
        }

        String headingPrefix = headingPrefix(buffer.headingTrail(), properties.getChunkSize());
        int bodyBudget = Math.max(MIN_BODY_BUDGET, properties.getChunkSize() - headingPrefix.length());
        // Preserve whole sentences when they still fit inside the nominal chunk budget and only overflow
        // because the heading trail consumed part of the body allowance.
        int sentencePreservationBudget = Math.max(bodyBudget, properties.getChunkSize());
        List<String> sentences = splitSentences(buffer.text());
        if (sentences.isEmpty()) {
            sentences = List.of(buffer.text());
        }

        if (requiresFixedFallback(sentences, sentencePreservationBudget)) {
            appendFixedNarrativeChunks(drafts, buffer, headingPrefix, bodyBudget, contentSupport);
            return null;
        }

        List<String> currentSentences = new ArrayList<>();
        int currentLength = 0;
        for (String sentence : sentences) {
            if (currentSentences.isEmpty()) {
                currentSentences.add(sentence);
                currentLength = sentence.length();
                continue;
            }

            int candidateLength = currentLength + NARRATIVE_SEPARATOR.length() + sentence.length();
            if (candidateLength <= bodyBudget) {
                currentSentences.add(sentence);
                currentLength = candidateLength;
                continue;
            }

            appendNarrativeChunk(drafts, buffer, headingPrefix, currentSentences, contentSupport);
            currentSentences = overlapTail(currentSentences, Math.min(properties.getChunkOverlap(), bodyBudget), bodyBudget);
            currentLength = joinedLength(currentSentences);
            while (!currentSentences.isEmpty()
                && currentLength + NARRATIVE_SEPARATOR.length() + sentence.length() > bodyBudget) {
                currentSentences.removeFirst();
                currentLength = joinedLength(currentSentences);
            }
            currentSentences.add(sentence);
            currentLength = joinedLength(currentSentences);
        }

        if (!currentSentences.isEmpty()) {
            appendNarrativeChunk(drafts, buffer, headingPrefix, currentSentences, contentSupport);
        }
        return null;
    }

    private void appendNarrativeChunk(
        List<ChunkDraft> drafts,
        NarrativeBuffer buffer,
        String headingPrefix,
        List<String> sentences,
        MaterialContentSupport contentSupport
    ) {
        String body = String.join(NARRATIVE_SEPARATOR, sentences).trim();
        if (!StringUtils.hasText(body)) {
            return;
        }

        drafts.add(new ChunkDraft(
            composeChunkText(headingPrefix, body),
            buffer.page(),
            buffer.extractor(),
            buffer.ocrUsed(),
            DocumentBlockType.NARRATIVE,
            buffer.sectionPath(),
            buffer.headingTrail(),
            null,
            null,
            buffer.parserConfidence()
        ));
    }

    private void appendFixedNarrativeChunks(
        List<ChunkDraft> drafts,
        NarrativeBuffer buffer,
        String headingPrefix,
        int bodyBudget,
        MaterialContentSupport contentSupport
    ) {
        String text = contentSupport.normalizeStoredContent(buffer.text());
        int cursor = 0;
        while (cursor < text.length()) {
            int end = Math.min(text.length(), cursor + bodyBudget);
            String slice = text.substring(cursor, end).trim();
            if (StringUtils.hasText(slice)) {
                drafts.add(new ChunkDraft(
                    composeChunkText(headingPrefix, slice),
                    buffer.page(),
                    buffer.extractor(),
                    buffer.ocrUsed(),
                    DocumentBlockType.NARRATIVE,
                    buffer.sectionPath(),
                    buffer.headingTrail(),
                    null,
                    null,
                    buffer.parserConfidence()
                ));
            }
            if (end == text.length()) {
                break;
            }
            cursor = Math.max(end - Math.min(bodyBudget,  Math.max(0, bodyBudget / 4)), cursor + 1);
        }
    }

    private void appendSpecialChunks(
        List<ChunkDraft> drafts,
        DocumentBlock block,
        MaterialProperties properties,
        MaterialContentSupport contentSupport,
        List<String> sectionPath,
        List<String> headingTrail,
        String tableId,
        String slideId
    ) {
        String headingPrefix = headingPrefix(headingTrail, properties.getChunkSize());
        int bodyBudget = Math.max(MIN_BODY_BUDGET, properties.getChunkSize() - headingPrefix.length());
        List<String> lines = nonBlankLines(block.text());
        if (lines.isEmpty()) {
            return;
        }

        if (block.type() == DocumentBlockType.TABLE && lines.size() > 1) {
            appendTableChunks(drafts, block, headingPrefix, lines, bodyBudget, sectionPath, headingTrail, tableId);
            return;
        }

        if ((block.type() == DocumentBlockType.LIST || block.type() == DocumentBlockType.SLIDE) && lines.size() > 1) {
            for (String line : lines) {
                appendSpecialChunk(
                    drafts,
                    headingPrefix,
                    List.of(line),
                    block,
                    sectionPath,
                    headingTrail,
                    tableId,
                    slideId
                );
            }
            return;
        }

        List<String> currentLines = new ArrayList<>();
        int currentLength = 0;
        for (String line : lines) {
            if (line.length() > bodyBudget && currentLines.isEmpty()) {
                appendSlicedSpecialLine(
                    drafts,
                    line,
                    headingPrefix,
                    bodyBudget,
                    block,
                    sectionPath,
                    headingTrail,
                    tableId,
                    slideId
                );
                continue;
            }

            int candidateLength = currentLines.isEmpty()
                ? line.length()
                : currentLength + BODY_SEPARATOR.length() + line.length();
            if (!currentLines.isEmpty() && candidateLength > bodyBudget) {
                appendSpecialChunk(
                    drafts,
                    headingPrefix,
                    currentLines,
                    block,
                    sectionPath,
                    headingTrail,
                    tableId,
                    slideId
                );
                currentLines = new ArrayList<>();
                currentLength = 0;
            }
            currentLines.add(line);
            currentLength = currentLines.isEmpty() ? 0 : joinedLength(currentLines, BODY_SEPARATOR);
        }

        if (!currentLines.isEmpty()) {
            appendSpecialChunk(
                drafts,
                headingPrefix,
                currentLines,
                block,
                sectionPath,
                headingTrail,
                tableId,
                slideId
            );
        }
    }

    private void appendTableChunks(
        List<ChunkDraft> drafts,
        DocumentBlock block,
        String headingPrefix,
        List<String> lines,
        int bodyBudget,
        List<String> sectionPath,
        List<String> headingTrail,
        String tableId
    ) {
        List<String> anchorLines;
        int rowStartIndex;
        if (lines.size() >= 3 && looksLikeTableRow(lines.get(0)) && looksLikeTableRow(lines.get(1))) {
            anchorLines = List.of(lines.get(0));
            rowStartIndex = 1;
        } else if (lines.size() >= 3) {
            anchorLines = List.of(lines.get(0), lines.get(1));
            rowStartIndex = 2;
        } else {
            anchorLines = List.of(lines.getFirst());
            rowStartIndex = 1;
        }

        if (rowStartIndex >= lines.size()) {
            appendSpecialChunk(
                drafts,
                headingPrefix,
                lines,
                block,
                sectionPath,
                headingTrail,
                tableId,
                null
            );
            return;
        }

        for (int index = rowStartIndex; index < lines.size(); index++) {
            String row = lines.get(index);
            List<String> chunkLines = new ArrayList<>(anchorLines);
            chunkLines.add(row);
            String body = String.join(BODY_SEPARATOR, chunkLines).trim();
            if (body.length() > bodyBudget) {
                appendSlicedSpecialLine(
                    drafts,
                    body,
                    headingPrefix,
                    bodyBudget,
                    block,
                    sectionPath,
                    headingTrail,
                    tableId,
                    null
                );
                continue;
            }
            appendSpecialChunk(
                drafts,
                headingPrefix,
                chunkLines,
                block,
                sectionPath,
                headingTrail,
                tableId,
                null
            );
        }
    }

    private void appendSlicedSpecialLine(
        List<ChunkDraft> drafts,
        String line,
        String headingPrefix,
        int bodyBudget,
        DocumentBlock block,
        List<String> sectionPath,
        List<String> headingTrail,
        String tableId,
        String slideId
    ) {
        int cursor = 0;
        while (cursor < line.length()) {
            int end = Math.min(line.length(), cursor + bodyBudget);
            String slice = line.substring(cursor, end).trim();
            if (StringUtils.hasText(slice)) {
                drafts.add(new ChunkDraft(
                    composeChunkText(headingPrefix, slice),
                    block.page(),
                    block.extractor(),
                    block.ocrUsed(),
                    block.type(),
                    sectionPath,
                    headingTrail,
                    tableId,
                    slideId,
                    block.confidence()
                ));
            }
            if (end == line.length()) {
                break;
            }
            cursor = end;
        }
    }

    private void appendSpecialChunk(
        List<ChunkDraft> drafts,
        String headingPrefix,
        List<String> lines,
        DocumentBlock block,
        List<String> sectionPath,
        List<String> headingTrail,
        String tableId,
        String slideId
    ) {
        String body = String.join(BODY_SEPARATOR, lines).trim();
        if (!StringUtils.hasText(body)) {
            return;
        }

        drafts.add(new ChunkDraft(
            composeChunkText(headingPrefix, body),
            block.page(),
            block.extractor(),
            block.ocrUsed(),
            block.type(),
            sectionPath,
            headingTrail,
            tableId,
            slideId,
            block.confidence()
        ));
    }

    private boolean attachCaptionToPrevious(
        List<ChunkDraft> drafts,
        DocumentBlock caption,
        List<String> sectionPath,
        List<String> headingTrail
    ) {
        if (drafts.isEmpty()) {
            return false;
        }
        ChunkDraft previous = drafts.getLast();
        if (previous.chunkType() != DocumentBlockType.TABLE && previous.chunkType() != DocumentBlockType.SLIDE) {
            return false;
        }
        if (!Objects.equals(previous.page(), caption.page())) {
            return false;
        }

        drafts.set(drafts.size() - 1, previous.withCaption(caption.text(), caption.confidence(), sectionPath, headingTrail));
        return true;
    }

    private void updateHeadingTrail(
        List<String> headingTrail,
        List<String> sectionPath,
        DocumentBlock title,
        MaterialContentSupport contentSupport
    ) {
        String headingText = title.text() == null ? "" : title.text().trim();
        if (!StringUtils.hasText(headingText)) {
            return;
        }

        int level = title.level() == null ? 1 : Math.max(1, title.level());
        while (headingTrail.size() >= level) {
            headingTrail.removeLast();
        }
        while (sectionPath.size() >= level) {
            sectionPath.removeLast();
        }

        headingTrail.add(headingText);
        String sectionKey = contentSupport.normalizeSectionKey(headingText);
        sectionPath.add(StringUtils.hasText(sectionKey) ? sectionKey : "section-" + level);
    }

    private String headingPrefix(List<String> headingTrail, int chunkSize) {
        if (headingTrail == null || headingTrail.isEmpty()) {
            return "";
        }

        String fullTrail = String.join("\n", headingTrail).trim();
        if (!StringUtils.hasText(fullTrail)) {
            return "";
        }
        if (fullTrail.length() <= Math.max(MIN_BODY_BUDGET, chunkSize / 2)) {
            return fullTrail + "\n";
        }

        return headingTrail.getLast().trim() + "\n";
    }

    private String composeChunkText(String headingPrefix, String body) {
        if (!StringUtils.hasText(headingPrefix)) {
            return body;
        }
        return headingPrefix + body;
    }

    private List<String> splitSentences(String text) {
        BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.ROOT);
        iterator.setText(text);

        List<String> sentences = new ArrayList<>();
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            String sentence = text.substring(start, end).trim();
            if (!sentence.isEmpty()) {
                sentences.add(sentence);
            }
        }
        return sentences;
    }

    private boolean requiresFixedFallback(List<String> sentences, int sentencePreservationBudget) {
        if (sentences.isEmpty()) {
            return true;
        }
        return sentences.stream().mapToInt(String::length).max().orElse(0) > sentencePreservationBudget;
    }

    private List<String> overlapTail(List<String> sentences, int overlapBudget, int bodyBudget) {
        if (sentences.isEmpty() || overlapBudget <= 0) {
            return new ArrayList<>();
        }

        List<String> overlap = new ArrayList<>();
        int currentLength = 0;
        for (int index = sentences.size() - 1; index >= 0; index--) {
            String sentence = sentences.get(index);
            int candidateLength = overlap.isEmpty()
                ? sentence.length()
                : currentLength + NARRATIVE_SEPARATOR.length() + sentence.length();
            if (!overlap.isEmpty() && candidateLength > overlapBudget) {
                break;
            }
            if (candidateLength > bodyBudget) {
                break;
            }
            overlap.addFirst(sentence);
            currentLength = candidateLength;
        }
        return overlap;
    }

    private int joinedLength(List<String> items) {
        return joinedLength(items, NARRATIVE_SEPARATOR);
    }

    private int joinedLength(List<String> items, String separator) {
        if (items.isEmpty()) {
            return 0;
        }
        return String.join(separator, items).length();
    }

    private List<String> nonBlankLines(String text) {
        return text == null ? List.of() : java.util.Arrays.stream(text.split("\\n"))
            .map(String::trim)
            .filter(StringUtils::hasText)
            .toList();
    }

    private boolean looksLikeTableRow(String line) {
        return StringUtils.hasText(line)
            && (line.contains("|") || line.contains("\t") || line.matches("^.+\\s{2,}.+$"));
    }

    private record NarrativeBuffer(
        String text,
        Integer page,
        String extractor,
        boolean ocrUsed,
        DocumentBlockConfidence parserConfidence,
        List<String> sectionPath,
        List<String> headingTrail
    ) {
        private static NarrativeBuffer start(DocumentBlock block, List<String> sectionPath, List<String> headingTrail) {
            return new NarrativeBuffer(
                block.text(),
                block.page(),
                block.extractor(),
                block.ocrUsed(),
                block.confidence(),
                sectionPath,
                headingTrail
            );
        }

        private boolean compatibleWith(DocumentBlock block, List<String> nextSectionPath, List<String> nextHeadingTrail) {
            return Objects.equals(page, block.page())
                && Objects.equals(extractor, block.extractor())
                && ocrUsed == block.ocrUsed()
                && Objects.equals(sectionPath, nextSectionPath)
                && Objects.equals(headingTrail, nextHeadingTrail);
        }

        private NarrativeBuffer append(DocumentBlock block) {
            String mergedText = StringUtils.hasText(text) ? text + "\n\n" + block.text() : block.text();
            return new NarrativeBuffer(
                mergedText,
                page,
                extractor,
                ocrUsed,
                worst(parserConfidence, block.confidence()),
                sectionPath,
                headingTrail
            );
        }
    }

    private record ChunkDraft(
        String text,
        Integer page,
        String extractor,
        boolean ocrUsed,
        DocumentBlockType chunkType,
        List<String> sectionPath,
        List<String> headingTrail,
        String tableId,
        String slideId,
        DocumentBlockConfidence parserConfidence
    ) {
        private ChunkDraft withCaption(
            String captionText,
            DocumentBlockConfidence captionConfidence,
            List<String> fallbackSectionPath,
            List<String> fallbackHeadingTrail
        ) {
            String merged = StringUtils.hasText(captionText) ? text + "\n" + captionText.trim() : text;
            return new ChunkDraft(
                merged,
                page,
                extractor,
                ocrUsed,
                chunkType,
                sectionPath == null || sectionPath.isEmpty() ? fallbackSectionPath : sectionPath,
                headingTrail == null || headingTrail.isEmpty() ? fallbackHeadingTrail : headingTrail,
                tableId,
                slideId,
                worst(parserConfidence, captionConfidence)
            );
        }
    }

    private static DocumentBlockConfidence worst(
        DocumentBlockConfidence left,
        DocumentBlockConfidence right
    ) {
        if (left == DocumentBlockConfidence.LOW || right == DocumentBlockConfidence.LOW) {
            return DocumentBlockConfidence.LOW;
        }
        return DocumentBlockConfidence.HIGH;
    }
}
