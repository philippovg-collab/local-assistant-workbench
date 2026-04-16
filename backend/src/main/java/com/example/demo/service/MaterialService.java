package com.example.demo.service;

import com.example.demo.api.ApiException;
import com.example.demo.config.MaterialProperties;
import com.example.demo.infrastructure.material.DocumentTextExtractor;
import com.example.demo.infrastructure.material.ExtractedDocument;
import com.example.demo.infrastructure.material.ExtractedDocumentSegment;
import com.example.demo.infrastructure.material.FileMaterialRepository;
import com.example.demo.infrastructure.material.MaterialFormatRegistry;
import com.example.demo.infrastructure.material.OcrCapability;
import com.example.demo.infrastructure.material.OcrCapabilityProvider;
import com.example.demo.infrastructure.material.StoredMaterialChunk;
import com.example.demo.infrastructure.material.StoredMaterialRecord;
import com.example.demo.model.ChatSource;
import com.example.demo.model.MaterialPdfUploadPolicyResponse;
import com.example.demo.model.MaterialSummary;
import com.example.demo.model.MaterialUploadPolicyResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MaterialService {

    private static final Logger logger = LoggerFactory.getLogger(MaterialService.class);
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{N}]+");

    private final FileMaterialRepository repository;
    private final DocumentTextExtractor extractor;
    private final MaterialProperties properties;
    private final MaterialFormatRegistry formatRegistry;
    private final OcrCapabilityProvider ocrCapabilityService;

    public MaterialService(
        FileMaterialRepository repository,
        DocumentTextExtractor extractor,
        MaterialProperties properties,
        MaterialFormatRegistry formatRegistry,
        OcrCapabilityProvider ocrCapabilityService
    ) {
        this.repository = repository;
        this.extractor = extractor;
        this.properties = properties;
        this.formatRegistry = formatRegistry;
        this.ocrCapabilityService = ocrCapabilityService;
    }

    public List<MaterialSummary> listSummaries() {
        return loadAllMaterials().stream()
            .sorted(Comparator.comparing(StoredMaterialRecord::createdAt).reversed())
            .map(this::toSummary)
            .toList();
    }

    public MaterialUploadPolicyResponse getUploadPolicy() {
        OcrCapability ocrCapability = ocrCapabilityService.currentCapability();
        return new MaterialUploadPolicyResponse(
            properties.getMaxUploadBytes(),
            formatRegistry.acceptedExtensions(),
            formatRegistry.acceptedMimeHints(),
            formatRegistry.supportsRichDocuments(),
            new MaterialPdfUploadPolicyResponse(
                formatRegistry.isPdfExtension("pdf"),
                ocrCapability.scannedPdfSupport(),
                ocrCapability.mode(),
                ocrCapability.reasonCode(),
                ocrCapability.reasonMessage(),
                ocrCapability.languages(),
                ocrCapability.maxPages()
            )
        );
    }

    public MaterialSummary saveText(String title, String content) {
        if (!StringUtils.hasText(content)) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "material.empty_text",
                "Material text is empty"
            );
        }

        String resolvedTitle = StringUtils.hasText(title) ? title.trim() : "Text material";
        try {
            return persistMaterial(
                resolvedTitle,
                "text",
                null,
                "text/plain",
                new ExtractedDocument(
                    List.of(new ExtractedDocumentSegment(content, null, "direct-text", false)),
                    "direct-text",
                    false,
                    null
                )
            );
        } catch (ApiException exception) {
            logKnownMaterialFailure("persist", "text", resolvedTitle, null, "text/plain", exception);
            throw exception;
        } catch (RuntimeException exception) {
            logUnexpectedMaterialFailure("persist", "text", resolvedTitle, null, "text/plain", exception);
            throw exception;
        }
    }

    public MaterialSummary saveUpload(String title, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "material.empty_upload",
                "Upload is empty"
            );
        }

        if (file.getSize() > properties.getMaxUploadBytes()) {
            throw new ApiException(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "material.upload_too_large",
                "Uploaded file exceeds the configured size limit"
            );
        }

        String originalFileName = file.getOriginalFilename();
        String resolvedTitle = StringUtils.hasText(title)
            ? title.trim()
            : StringUtils.hasText(originalFileName) ? originalFileName.trim() : "Uploaded material";
        String mediaType = file.getContentType();

        try {
            byte[] fileBytes = file.getBytes();
            ExtractedDocument document;
            try {
                document = extractor.extract(originalFileName, mediaType, fileBytes);
            } catch (ApiException exception) {
                logKnownMaterialFailure("extract", "file", resolvedTitle, originalFileName, mediaType, exception);
                throw exception;
            } catch (RuntimeException exception) {
                logUnexpectedMaterialFailure("extract", "file", resolvedTitle, originalFileName, mediaType, exception);
                throw exception;
            }

            try {
                return persistMaterial(
                    resolvedTitle,
                    "file",
                    originalFileName,
                    mediaType,
                    document
                );
            } catch (ApiException exception) {
                logKnownMaterialFailure("persist", "file", resolvedTitle, originalFileName, mediaType, exception);
                throw exception;
            } catch (RuntimeException exception) {
                logUnexpectedMaterialFailure("persist", "file", resolvedTitle, originalFileName, mediaType, exception);
                throw exception;
            }
        } catch (IOException exception) {
            logger.error(
                "Material upload failed at stage=receive sourceType=file title={} originalFileName={} mediaType={} cause={}",
                resolvedTitle,
                originalFileName,
                mediaType,
                exception.getMessage(),
                exception
            );
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "material.upload_read_failed",
                "Unable to read uploaded file bytes",
                exception
            );
        }
    }

    public void delete(String id) {
        repository.delete(id);
    }

    public RetrievalResult retrieveContext(String prompt) {
        Set<String> queryTokens = tokenize(prompt);
        if (queryTokens.isEmpty()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "chat.invalid_prompt",
                "Prompt must contain at least one alphanumeric token"
            );
        }

        String normalizedPrompt = normalizeForSearch(prompt);
        List<String> queryPhrases = buildPhrases(queryTokens);
        List<StoredMaterialRecord> allMaterials = loadAllMaterials();
        if (allMaterials.isEmpty()) {
            return new RetrievalResult(0, List.of());
        }

        Map<String, StoredMaterialRecord> latestBySourceKey = allMaterials.stream()
            .collect(Collectors.toMap(
                StoredMaterialRecord::sourceKey,
                Function.identity(),
                this::pickNewerRecord
            ));

        List<ScoredChunk> bestChunks = latestBySourceKey.values().stream()
            .flatMap(material -> material.chunks().stream()
                .map(chunk -> scoreChunk(material, chunk, normalizedPrompt, queryTokens, queryPhrases)))
            .filter(chunk -> chunk.score() > 0)
            .sorted(Comparator
                .comparingInt(ScoredChunk::score)
                .reversed()
                .thenComparing(chunk -> chunk.page() == null ? Integer.MAX_VALUE : chunk.page()))
            .limit(4)
            .toList();

        logger.info(
            "RAG retrieval: materials={} matches={} scoring=lexical-v2 top={}",
            latestBySourceKey.size(),
            bestChunks.size(),
            bestChunks.stream()
                .map(chunk -> "%s[p%s]=%d".formatted(
                    chunk.title(),
                    chunk.page() == null ? "-" : chunk.page(),
                    chunk.score()
                ))
                .toList()
        );

        List<RetrievedChunk> matches = bestChunks.stream()
            .map(chunk -> new RetrievedChunk(
                chunk.fullText(),
                new ChatSource(
                    chunk.materialId(),
                    chunk.title(),
                    clip(chunk.fullText(), 280),
                    chunk.score(),
                    chunk.page(),
                    chunk.extractor(),
                    chunk.ocrUsed()
                )
            ))
            .toList();

        return new RetrievalResult(allMaterials.size(), matches);
    }

    private MaterialSummary persistMaterial(
        String title,
        String sourceType,
        String originalFileName,
        String mediaType,
        ExtractedDocument document
    ) {
        List<ExtractedDocumentSegment> normalizedSegments = normalizeSegments(document.segments());
        String storedContent = joinSegments(normalizedSegments);
        if (!StringUtils.hasText(storedContent)) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "material.empty_content",
                "Material content is empty after normalization"
            );
        }

        if (storedContent.length() > properties.getMaxTextChars()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "material.content_too_large",
                "Material content exceeds the configured text limit"
            );
        }

        String normalizedContent = normalizeForHash(storedContent);
        String contentHash = sha256(normalizedContent);

        return loadAllMaterials().stream()
            .filter(record -> record.contentHash().equals(contentHash))
            .findFirst()
            .map(this::toSummary)
            .orElseGet(() -> saveNewMaterial(
                title,
                sourceType,
                originalFileName,
                mediaType,
                storedContent,
                normalizedContent,
                contentHash,
                document,
                normalizedSegments
            ));
    }

    private MaterialSummary saveNewMaterial(
        String title,
        String sourceType,
        String originalFileName,
        String mediaType,
        String storedContent,
        String normalizedContent,
        String contentHash,
        ExtractedDocument document,
        List<ExtractedDocumentSegment> normalizedSegments
    ) {
        Instant now = Instant.now();
        StoredMaterialRecord record = new StoredMaterialRecord(
            UUID.randomUUID().toString(),
            title,
            sourceType,
            originalFileName,
            mediaType,
            storedContent,
            normalizedContent,
            contentHash,
            buildSourceKey(title, originalFileName),
            normalizeExtractor(document.extractor()),
            document.ocrUsed(),
            document.pageCount(),
            buildChunks(normalizedSegments, normalizeExtractor(document.extractor()), document.ocrUsed()),
            now,
            now
        );

        repository.save(record);
        return toSummary(record);
    }

    private void logKnownMaterialFailure(
        String stage,
        String sourceType,
        String title,
        String originalFileName,
        String mediaType,
        ApiException exception
    ) {
        logger.warn(
            "Material processing failed at stage={} sourceType={} title={} originalFileName={} mediaType={} code={} status={} message={}",
            stage,
            sourceType,
            title,
            originalFileName,
            mediaType,
            exception.getCode(),
            exception.getStatus().value(),
            exception.getMessage()
        );
    }

    private void logUnexpectedMaterialFailure(
        String stage,
        String sourceType,
        String title,
        String originalFileName,
        String mediaType,
        RuntimeException exception
    ) {
        logger.error(
            "Material processing failed unexpectedly at stage={} sourceType={} title={} originalFileName={} mediaType={} cause={}",
            stage,
            sourceType,
            title,
            originalFileName,
            mediaType,
            exception.getMessage(),
            exception
        );
    }

    private List<StoredMaterialRecord> loadAllMaterials() {
        return repository.findAll().stream()
            .map(this::upgradeLegacyRecord)
            .toList();
    }

    private StoredMaterialRecord upgradeLegacyRecord(StoredMaterialRecord record) {
        String resolvedTitle = StringUtils.hasText(record.title())
            ? record.title().trim()
            : StringUtils.hasText(record.originalFileName()) ? record.originalFileName().trim() : "Untitled material";
        String resolvedSourceType = StringUtils.hasText(record.sourceType())
            ? record.sourceType().trim()
            : "text";
        String storedContent = normalizeStoredContent(record.content());
        String normalizedContent = StringUtils.hasText(record.normalizedContent())
            ? normalizeForHash(record.normalizedContent())
            : normalizeForHash(storedContent);
        String contentHash = StringUtils.hasText(record.contentHash())
            ? record.contentHash().trim()
            : sha256(normalizedContent);
        String sourceKey = StringUtils.hasText(record.sourceKey())
            ? record.sourceKey().trim()
            : buildSourceKey(resolvedTitle, record.originalFileName());
        String extractorName = normalizeExtractor(record.extractor());
        boolean ocrUsed = Boolean.TRUE.equals(record.ocrUsed());
        List<StoredMaterialChunk> chunks = normalizeChunks(record.chunks(), storedContent, extractorName, ocrUsed);
        Integer pageCount = record.pageCount();
        Instant createdAt = record.createdAt() != null ? record.createdAt() : Instant.now();
        Instant updatedAt = record.updatedAt() != null ? record.updatedAt() : createdAt;

        StoredMaterialRecord upgradedRecord = new StoredMaterialRecord(
            record.id(),
            resolvedTitle,
            resolvedSourceType,
            record.originalFileName(),
            record.mediaType(),
            storedContent,
            normalizedContent,
            contentHash,
            sourceKey,
            extractorName,
            ocrUsed,
            pageCount,
            chunks,
            createdAt,
            updatedAt
        );

        if (!upgradedRecord.equals(record)) {
            repository.save(upgradedRecord);
        }

        return upgradedRecord;
    }

    private StoredMaterialRecord pickNewerRecord(StoredMaterialRecord left, StoredMaterialRecord right) {
        return left.updatedAt().isAfter(right.updatedAt()) ? left : right;
    }

    private ScoredChunk scoreChunk(
        StoredMaterialRecord material,
        StoredMaterialChunk chunk,
        String normalizedPrompt,
        Set<String> queryTokens,
        List<String> queryPhrases
    ) {
        String normalizedChunkText = normalizeForSearch(chunk.text());
        String normalizedTitle = normalizeForSearch(material.title());
        int score = 0;
        int matchedTokens = 0;

        for (String token : queryTokens) {
            int textFrequency = countOccurrences(normalizedChunkText, token);
            int titleFrequency = countOccurrences(normalizedTitle, token);
            if (textFrequency > 0 || titleFrequency > 0) {
                matchedTokens++;
            }

            score += textFrequency * (token.length() > 5 ? 5 : 2);
            score += titleFrequency * 6;
        }

        score += matchedTokens * 3;

        if (normalizedChunkText.contains(normalizedPrompt)) {
            score += 18;
        }

        if (normalizedTitle.contains(normalizedPrompt)) {
            score += 12;
        }

        for (String phrase : queryPhrases) {
            if (phrase.length() >= 5 && normalizedChunkText.contains(phrase)) {
                score += 6;
            }
        }

        if (chunk.page() != null) {
            score += Math.max(0, 4 - Math.min(chunk.page() - 1, 3));
        }

        if (Boolean.TRUE.equals(chunk.ocrUsed())) {
            score -= 1;
        }

        return new ScoredChunk(
            material.id(),
            material.title(),
            chunk.text(),
            chunk.page(),
            normalizeExtractor(chunk.extractor()),
            Boolean.TRUE.equals(chunk.ocrUsed()),
            score
        );
    }

    private List<StoredMaterialChunk> buildChunks(
        List<ExtractedDocumentSegment> segments,
        String defaultExtractor,
        boolean defaultOcrUsed
    ) {
        List<StoredMaterialChunk> chunks = new ArrayList<>();
        int index = 0;

        for (ExtractedDocumentSegment segment : segments) {
            String text = normalizeStoredContent(segment.text());
            if (!StringUtils.hasText(text)) {
                continue;
            }

            int cursor = 0;
            while (cursor < text.length() && chunks.size() < properties.getMaxChunks()) {
                int end = Math.min(text.length(), cursor + properties.getChunkSize());
                String slice = text.substring(cursor, end).trim();
                if (!slice.isEmpty()) {
                    chunks.add(new StoredMaterialChunk(
                        index++,
                        slice,
                        List.copyOf(tokenize(slice)),
                        segment.page(),
                        normalizeExtractor(StringUtils.hasText(segment.extractor()) ? segment.extractor() : defaultExtractor),
                        segment.ocrUsed() || defaultOcrUsed
                    ));
                }

                if (end == text.length()) {
                    break;
                }

                cursor = Math.max(end - properties.getChunkOverlap(), cursor + 1);
            }

            if (chunks.size() >= properties.getMaxChunks()) {
                break;
            }
        }

        return chunks;
    }

    private List<StoredMaterialChunk> normalizeChunks(
        List<StoredMaterialChunk> chunks,
        String content,
        String defaultExtractor,
        boolean defaultOcrUsed
    ) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }

        if (chunks == null || chunks.isEmpty()) {
            return buildChunks(
                List.of(new ExtractedDocumentSegment(content, null, defaultExtractor, defaultOcrUsed)),
                defaultExtractor,
                defaultOcrUsed
            );
        }

        List<StoredMaterialChunk> normalizedChunks = new ArrayList<>();
        for (int index = 0; index < chunks.size(); index++) {
            StoredMaterialChunk chunk = chunks.get(index);
            if (chunk == null || !StringUtils.hasText(chunk.text())) {
                return buildChunks(
                    List.of(new ExtractedDocumentSegment(content, null, defaultExtractor, defaultOcrUsed)),
                    defaultExtractor,
                    defaultOcrUsed
                );
            }

            String text = chunk.text().trim();
            List<String> tokens = (chunk.tokens() == null || chunk.tokens().isEmpty())
                ? List.copyOf(tokenize(text))
                : chunk.tokens().stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .map(token -> token.toLowerCase(Locale.ROOT))
                    .distinct()
                    .toList();

            normalizedChunks.add(new StoredMaterialChunk(
                index,
                text,
                tokens,
                chunk.page(),
                normalizeExtractor(StringUtils.hasText(chunk.extractor()) ? chunk.extractor() : defaultExtractor),
                chunk.ocrUsed() != null ? chunk.ocrUsed() : defaultOcrUsed
            ));
        }

        return normalizedChunks;
    }

    private List<ExtractedDocumentSegment> normalizeSegments(List<ExtractedDocumentSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return List.of();
        }

        return segments.stream()
            .filter(segment -> segment != null && StringUtils.hasText(segment.text()))
            .map(segment -> new ExtractedDocumentSegment(
                normalizeStoredContent(segment.text()),
                segment.page(),
                normalizeExtractor(segment.extractor()),
                segment.ocrUsed()
            ))
            .filter(segment -> StringUtils.hasText(segment.text()))
            .toList();
    }

    private String joinSegments(List<ExtractedDocumentSegment> segments) {
        return segments.stream()
            .map(ExtractedDocumentSegment::text)
            .filter(StringUtils::hasText)
            .collect(Collectors.joining("\n\n"));
    }

    private Set<String> tokenize(String input) {
        String normalized = NON_ALPHANUMERIC.matcher(input.toLowerCase(Locale.ROOT)).replaceAll(" ");
        String[] rawTokens = normalized.split("\\s+");
        Set<String> tokens = new LinkedHashSet<>();
        for (String token : rawTokens) {
            if (token.length() >= 2) {
                tokens.add(token);
            }
        }

        return tokens;
    }

    private List<String> buildPhrases(Set<String> tokens) {
        List<String> orderedTokens = new ArrayList<>(tokens);
        List<String> phrases = new ArrayList<>();
        for (int index = 0; index < orderedTokens.size() - 1; index++) {
            phrases.add(orderedTokens.get(index) + " " + orderedTokens.get(index + 1));
        }
        return phrases;
    }

    private int countOccurrences(String text, String token) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(token)) {
            return 0;
        }

        int count = 0;
        int fromIndex = 0;
        while ((fromIndex = text.indexOf(token, fromIndex)) >= 0) {
            count++;
            fromIndex += token.length();
        }

        return count;
    }

    private MaterialSummary toSummary(StoredMaterialRecord record) {
        return new MaterialSummary(
            record.id(),
            record.title(),
            record.sourceType(),
            record.originalFileName(),
            true,
            record.createdAt(),
            record.content().length(),
            clip(record.content(), 180)
        );
    }

    private String normalizeStoredContent(String rawContent) {
        return rawContent == null ? "" : rawContent.replace("\r\n", "\n").trim();
    }

    private String normalizeForHash(String input) {
        return input.replaceAll("\\s+", " ").trim();
    }

    private String normalizeForSearch(String input) {
        return normalizeStoredContent(input).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private String normalizeExtractor(String extractor) {
        return StringUtils.hasText(extractor) ? extractor.trim() : "legacy";
    }

    private String buildSourceKey(String title, String originalFileName) {
        String source = StringUtils.hasText(originalFileName)
            ? originalFileName
            : StringUtils.hasText(title) ? title : "material";
        return normalizeForHash(source.toLowerCase(Locale.ROOT));
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "material.hash_unavailable",
                "Unable to create content fingerprint",
                exception
            );
        }
    }

    private String clip(String input, int limit) {
        String normalized = input.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= limit) {
            return normalized;
        }

        return normalized.substring(0, limit) + "...";
    }

    public record RetrievalResult(
        int materialCount,
        List<RetrievedChunk> matches
    ) {
        public List<ChatSource> sources() {
            return matches.stream().map(RetrievedChunk::source).toList();
        }
    }

    public record RetrievedChunk(
        String contextText,
        ChatSource source
    ) {
    }

    private record ScoredChunk(
        String materialId,
        String title,
        String fullText,
        Integer page,
        String extractor,
        boolean ocrUsed,
        int score
    ) {
    }
}
