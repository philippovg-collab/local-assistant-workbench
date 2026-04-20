package com.example.demo.service;

import com.example.demo.service.material.ChunkProfile;
import com.example.demo.service.material.StoredMaterialChunk;
import com.example.demo.service.material.StoredMaterialSegment;

import com.example.demo.config.MaterialProperties;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.util.StringUtils;

public class SentenceChunkingStrategy implements ChunkingStrategy {

    private static final String CHUNK_SEPARATOR = " ";
    private static final double FALLBACK_SENTENCE_RATIO = 1.35d;

    @Override
    public ChunkProfile profile() {
        return ChunkProfile.SENTENCE_V1;
    }

    @Override
    public List<StoredMaterialChunk> buildChunks(
        List<StoredMaterialSegment> segments,
        MaterialProperties properties,
        MaterialContentSupport contentSupport
    ) {
        List<StoredMaterialChunk> chunks = new ArrayList<>();
        int index = 0;
        FixedChunkingStrategy fallback = new FixedChunkingStrategy();

        for (StoredMaterialSegment segment : segments) {
            if (chunks.size() >= properties.getMaxChunks()) {
                break;
            }

            String text = contentSupport.normalizeStoredContent(segment.text());
            if (!StringUtils.hasText(text)) {
                continue;
            }

            List<String> sentences = splitSentences(text);
            if (requiresFixedFallback(text, sentences, properties)) {
                index = appendFallbackChunks(
                    chunks,
                    index,
                    fallback.buildChunks(List.of(segment), properties, contentSupport),
                    properties.getMaxChunks()
                );
                continue;
            }

            List<String> currentSentences = new ArrayList<>();
            int currentLength = 0;

            for (String sentence : sentences) {
                if (currentSentences.isEmpty()) {
                    currentSentences.add(sentence);
                    currentLength = sentence.length();
                    continue;
                }

                int candidateLength = currentLength + CHUNK_SEPARATOR.length() + sentence.length();
                if (candidateLength <= properties.getChunkSize()) {
                    currentSentences.add(sentence);
                    currentLength = candidateLength;
                    continue;
                }

                index = appendChunk(
                    chunks,
                    index,
                    currentSentences,
                    segment,
                    contentSupport,
                    properties.getMaxChunks()
                );
                if (chunks.size() >= properties.getMaxChunks()) {
                    break;
                }

                currentSentences = overlapTail(currentSentences, properties.getChunkOverlap(), properties.getChunkSize());
                currentLength = joinedLength(currentSentences);
                while (!currentSentences.isEmpty()
                    && currentLength + CHUNK_SEPARATOR.length() + sentence.length() > properties.getChunkSize()) {
                    currentSentences.removeFirst();
                    currentLength = joinedLength(currentSentences);
                }
                currentSentences.add(sentence);
                currentLength = joinedLength(currentSentences);
            }

            if (chunks.size() >= properties.getMaxChunks()) {
                break;
            }

            if (!currentSentences.isEmpty()) {
                index = appendChunk(
                    chunks,
                    index,
                    currentSentences,
                    segment,
                    contentSupport,
                    properties.getMaxChunks()
                );
            }
        }

        return chunks;
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

    private boolean requiresFixedFallback(String text, List<String> sentences, MaterialProperties properties) {
        if (sentences.isEmpty()) {
            return true;
        }
        if (sentences.size() == 1 && text.length() > properties.getChunkSize()) {
            return true;
        }

        int maxSentenceLength = sentences.stream()
            .mapToInt(String::length)
            .max()
            .orElse(0);
        return maxSentenceLength > Math.round(properties.getChunkSize() * FALLBACK_SENTENCE_RATIO);
    }

    private int appendFallbackChunks(
        List<StoredMaterialChunk> chunks,
        int nextIndex,
        List<StoredMaterialChunk> fallbackChunks,
        int maxChunks
    ) {
        for (StoredMaterialChunk fallbackChunk : fallbackChunks) {
            if (chunks.size() >= maxChunks) {
                break;
            }
            chunks.add(new StoredMaterialChunk(
                nextIndex++,
                fallbackChunk.text(),
                fallbackChunk.tokens(),
                fallbackChunk.page(),
                fallbackChunk.extractor(),
                fallbackChunk.ocrUsed()
            ));
        }
        return nextIndex;
    }

    private int appendChunk(
        List<StoredMaterialChunk> chunks,
        int nextIndex,
        List<String> sentences,
        StoredMaterialSegment segment,
        MaterialContentSupport contentSupport,
        int maxChunks
    ) {
        if (chunks.size() >= maxChunks) {
            return nextIndex;
        }

        String chunkText = String.join(CHUNK_SEPARATOR, sentences).trim();
        if (chunkText.isEmpty()) {
            return nextIndex;
        }

        chunks.add(new StoredMaterialChunk(
            nextIndex + 1 - 1,
            chunkText,
            List.copyOf(contentSupport.tokenize(chunkText)),
            segment.page(),
            contentSupport.normalizeExtractor(segment.extractor()),
            Boolean.TRUE.equals(segment.ocrUsed())
        ));
        return nextIndex + 1;
    }

    private List<String> overlapTail(List<String> sentences, int overlapBudget, int chunkSize) {
        if (sentences.isEmpty() || overlapBudget <= 0) {
            return new ArrayList<>();
        }

        List<String> overlap = new ArrayList<>();
        int currentLength = 0;
        for (int index = sentences.size() - 1; index >= 0; index--) {
            String sentence = sentences.get(index);
            int candidateLength = overlap.isEmpty()
                ? sentence.length()
                : currentLength + CHUNK_SEPARATOR.length() + sentence.length();
            if (!overlap.isEmpty() && candidateLength > overlapBudget) {
                break;
            }
            if (candidateLength > chunkSize) {
                break;
            }
            overlap.addFirst(sentence);
            currentLength = candidateLength;
        }
        return overlap;
    }

    private int joinedLength(List<String> sentences) {
        if (sentences.isEmpty()) {
            return 0;
        }
        return String.join(CHUNK_SEPARATOR, sentences).length();
    }
}
