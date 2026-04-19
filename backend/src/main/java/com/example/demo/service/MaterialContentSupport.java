package com.example.demo.service;

import com.example.demo.api.ApiException;
import com.example.demo.config.MaterialProperties;
import com.example.demo.infrastructure.material.ChunkProfile;
import com.example.demo.infrastructure.material.DocumentBlock;
import com.example.demo.infrastructure.material.DocumentBlockBuilder;
import com.example.demo.infrastructure.material.DocumentParseResult;
import com.example.demo.infrastructure.material.ExtractedDocumentSegment;
import com.example.demo.infrastructure.material.MaterialLineageIdentity;
import com.example.demo.infrastructure.material.MaterialLineageIdentityKind;
import com.example.demo.infrastructure.material.StoredMaterialChunk;
import com.example.demo.infrastructure.material.StoredMaterialRecord;
import com.example.demo.infrastructure.material.StoredMaterialSegment;
import com.example.demo.model.MaterialIndexingStatus;
import com.example.demo.model.MaterialLineageVersion;
import com.example.demo.model.MaterialMetadataSnapshot;
import com.example.demo.model.MaterialSummary;
import com.example.demo.model.MaterialVersionState;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class MaterialContentSupport {

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern SECTION_OUTLINE_PREFIX = Pattern.compile(
        "^(?:\\d+(?:\\.\\d+)+\\.?|\\d+[.)]|[A-Za-z][.)]|[IVXLCDMivxlcdm]+[.)])\\s+"
    );
    private static final int CONTENT_ANCHOR_TOKEN_LIMIT = 12;
    private static final String DEFAULT_TEXT_TITLE = "text-material";

    private final MaterialProperties properties;
    private final Map<ChunkProfile, ChunkingStrategy> chunkingStrategies;

    public MaterialContentSupport(MaterialProperties properties) {
        this.properties = properties;
        this.chunkingStrategies = new EnumMap<>(ChunkProfile.class);
        registerChunkingStrategy(new FixedChunkingStrategy());
        registerChunkingStrategy(new SentenceChunkingStrategy());
        registerChunkingStrategy(new StructuredChunkingStrategy());
    }

    public List<DocumentBlock> normalizeBlocks(List<DocumentBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return List.of();
        }

        List<DocumentBlock> normalizedBlocks = new ArrayList<>();
        int index = 0;
        for (DocumentBlock block : blocks) {
            if (block == null || !StringUtils.hasText(block.text())) {
                continue;
            }

            String text = normalizeStoredContent(block.text());
            if (!StringUtils.hasText(text)) {
                continue;
            }

            normalizedBlocks.add(new DocumentBlock(
                index++,
                block.type(),
                text,
                block.page(),
                normalizeExtractor(block.extractor()),
                block.ocrUsed(),
                block.confidence(),
                block.level()
            ));
        }
        return List.copyOf(normalizedBlocks);
    }

    public List<StoredMaterialSegment> toStoredSegments(DocumentParseResult parseResult) {
        if (parseResult == null) {
            return List.of();
        }
        return toStoredSegments(parseResult.blocks());
    }

    public List<StoredMaterialSegment> toStoredSegments(List<DocumentBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return List.of();
        }

        List<DocumentBlock> normalizedBlocks = normalizeBlocks(blocks);
        if (normalizedBlocks.isEmpty()) {
            return List.of();
        }

        List<StoredMaterialSegment> segments = new ArrayList<>();
        int index = 0;
        for (DocumentBlock block : normalizedBlocks) {
            segments.add(new StoredMaterialSegment(
                index++,
                block.text(),
                block.page(),
                normalizeExtractor(block.extractor()),
                block.ocrUsed()
            ));
        }
        return List.copyOf(segments);
    }

    public String joinBlocks(DocumentParseResult parseResult) {
        return parseResult == null ? "" : joinBlocks(parseResult.blocks());
    }

    public String joinBlocks(List<DocumentBlock> blocks) {
        return normalizeBlocks(blocks).stream()
            .map(DocumentBlock::text)
            .filter(StringUtils::hasText)
            .collect(Collectors.joining("\n\n"));
    }

    public String headerTextForHints(DocumentParseResult parseResult) {
        if (parseResult == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (DocumentBlock block : normalizeBlocks(parseResult.blocks())) {
            if (!StringUtils.hasText(block.text())) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(block.text());
            if (builder.length() >= 1_200) {
                break;
            }
        }
        return builder.length() <= 1_200 ? builder.toString() : builder.substring(0, 1_200);
    }

    public List<ExtractedDocumentSegment> normalizeSegments(List<ExtractedDocumentSegment> segments) {
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

    public List<StoredMaterialSegment> normalizeStoredSegments(List<ExtractedDocumentSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return List.of();
        }

        List<StoredMaterialSegment> normalizedSegments = new ArrayList<>();
        int index = 0;
        for (ExtractedDocumentSegment segment : segments) {
            if (segment == null || !StringUtils.hasText(segment.text())) {
                continue;
            }

            String text = normalizeStoredContent(segment.text());
            if (!StringUtils.hasText(text)) {
                continue;
            }

            normalizedSegments.add(new StoredMaterialSegment(
                index++,
                text,
                segment.page(),
                normalizeExtractor(segment.extractor()),
                segment.ocrUsed()
            ));
        }
        return List.copyOf(normalizedSegments);
    }

    public String joinSegments(List<ExtractedDocumentSegment> segments) {
        return segments.stream()
            .map(ExtractedDocumentSegment::text)
            .filter(StringUtils::hasText)
            .collect(Collectors.joining("\n\n"));
    }

    public String joinStoredSegments(List<StoredMaterialSegment> segments) {
        return segments.stream()
            .map(StoredMaterialSegment::text)
            .filter(StringUtils::hasText)
            .collect(Collectors.joining("\n\n"));
    }

    public ChunkProfile configuredChunkProfile() {
        return resolveChunkProfile(properties.getChunkProfile());
    }

    public ChunkProfile configuredChunkProfile(boolean structuredV1Enabled) {
        ChunkProfile configuredProfile = configuredChunkProfile();
        if (!structuredV1Enabled && configuredProfile == ChunkProfile.STRUCTURED_V1) {
            return ChunkProfile.FIXED_V1;
        }
        return configuredProfile;
    }

    public ChunkProfile resolveChunkProfile(String rawValue) {
        return ChunkProfile.fromProperty(StringUtils.hasText(rawValue)
            ? rawValue
            : ChunkProfile.STRUCTURED_V1.propertyValue());
    }

    public List<StoredMaterialChunk> buildChunks(DocumentParseResult parseResult, ChunkProfile chunkProfile) {
        if (parseResult == null) {
            return List.of();
        }

        ChunkProfile resolvedProfile = chunkProfile == null ? ChunkProfile.FIXED_V1 : chunkProfile;
        ChunkingStrategy strategy = chunkingStrategies.get(resolvedProfile);
        if (strategy == null) {
            throw new IllegalArgumentException("No chunking strategy registered for profile '" + resolvedProfile.propertyValue() + "'.");
        }
        return strategy.buildChunksFromBlocks(normalizeBlocks(parseResult.blocks()), properties, this);
    }

    public List<StoredMaterialChunk> buildChunks(
        List<ExtractedDocumentSegment> segments,
        String defaultExtractor,
        boolean defaultOcrUsed
    ) {
        List<StoredMaterialSegment> normalizedSegments = normalizeStoredSegments(segments).stream()
            .map(segment -> new StoredMaterialSegment(
                segment.index(),
                segment.text(),
                segment.page(),
                StringUtils.hasText(segment.extractor()) ? segment.extractor() : normalizeExtractor(defaultExtractor),
                Boolean.TRUE.equals(segment.ocrUsed()) || defaultOcrUsed
            ))
            .toList();
        return buildChunks(normalizedSegments, ChunkProfile.FIXED_V1);
    }

    public List<StoredMaterialChunk> buildChunks(List<StoredMaterialSegment> segments, ChunkProfile chunkProfile) {
        ChunkProfile resolvedProfile = chunkProfile == null ? ChunkProfile.FIXED_V1 : chunkProfile;
        ChunkingStrategy strategy = chunkingStrategies.get(resolvedProfile);
        if (strategy == null) {
            throw new IllegalArgumentException("No chunking strategy registered for profile '" + resolvedProfile.propertyValue() + "'.");
        }
        return strategy.buildChunks(segments == null ? List.of() : segments, properties, this);
    }

    public List<StoredMaterialChunk> normalizeChunks(
        List<StoredMaterialChunk> chunks,
        String content,
        String defaultExtractor,
        boolean defaultOcrUsed
    ) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }

        if (chunks == null || chunks.isEmpty()) {
            return buildChunks(singleSegment(content, defaultExtractor, defaultOcrUsed), ChunkProfile.FIXED_V1);
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
                chunk.ocrUsed() != null ? chunk.ocrUsed() : defaultOcrUsed,
                chunk.chunkType(),
                chunk.sectionPath(),
                chunk.headingTrail(),
                chunk.tableId(),
                chunk.slideId(),
                chunk.parserConfidence()
            ));
        }

        return normalizedChunks;
    }

    public List<StoredMaterialSegment> singleSegment(String content, String extractor, boolean ocrUsed) {
        String normalized = normalizeStoredContent(content);
        if (!StringUtils.hasText(normalized)) {
            return List.of();
        }

        return List.of(new StoredMaterialSegment(
            0,
            normalized,
            null,
            normalizeExtractor(extractor),
            ocrUsed
        ));
    }

    public List<StoredMaterialSegment> pseudoSegmentsFromChunks(
        List<StoredMaterialChunk> chunks,
        String defaultExtractor,
        boolean defaultOcrUsed
    ) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }

        List<StoredMaterialSegment> segments = new ArrayList<>();
        int nextIndex = 0;
        for (StoredMaterialChunk chunk : chunks) {
            if (chunk == null || !StringUtils.hasText(chunk.text())) {
                continue;
            }

            segments.add(new StoredMaterialSegment(
                nextIndex++,
                normalizeStoredContent(chunk.text()),
                chunk.page(),
                normalizeExtractor(StringUtils.hasText(chunk.extractor()) ? chunk.extractor() : defaultExtractor),
                chunk.ocrUsed() != null ? chunk.ocrUsed() : defaultOcrUsed
            ));
        }
        return List.copyOf(segments);
    }

    public List<DocumentBlock> reconstructBlocks(List<StoredMaterialSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return List.of();
        }

        List<DocumentBlock> blocks = new ArrayList<>();
        int nextIndex = 0;
        for (StoredMaterialSegment segment : segments) {
            if (segment == null || !StringUtils.hasText(segment.text())) {
                continue;
            }
            List<DocumentBlock> segmentBlocks = DocumentBlockBuilder.fromText(
                segment.text(),
                segment.page(),
                normalizeExtractor(segment.extractor()),
                Boolean.TRUE.equals(segment.ocrUsed()),
                false,
                com.example.demo.infrastructure.material.DocumentBlockType.NARRATIVE,
                nextIndex
            );
            if (segmentBlocks.isEmpty()) {
                continue;
            }
            blocks.addAll(segmentBlocks);
            nextIndex = blocks.getLast().index() + 1;
        }
        return List.copyOf(blocks);
    }

    public String normalizeSectionKey(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String normalized = value.trim();
        String previous;
        do {
            previous = normalized;
            normalized = SECTION_OUTLINE_PREFIX.matcher(normalized).replaceFirst("").trim();
        } while (!normalized.equals(previous));

        return normalizeLineageLabel(normalized);
    }

    public Set<String> tokenize(String input) {
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

    public String clip(String input, int limit) {
        String normalized = normalizeStoredContent(input).replaceAll("\\s+", " ").trim();
        if (normalized.length() <= limit) {
            return normalized;
        }

        return normalized.substring(0, limit) + "...";
    }

    public String normalizeStoredContent(String rawContent) {
        return rawContent == null ? "" : rawContent.replace("\r\n", "\n").trim();
    }

    public String normalizeForHash(String input) {
        return input.replaceAll("\\s+", " ").trim();
    }

    public String normalizeExtractor(String extractor) {
        return StringUtils.hasText(extractor) ? extractor.trim() : "legacy";
    }

    public MaterialLineageIdentity buildLineageIdentity(
        String sourceType,
        String lineageTitle,
        String originalFileName,
        String content
    ) {
        String normalizedSourceType = StringUtils.hasText(sourceType)
            ? sourceType.trim().toLowerCase(Locale.ROOT)
            : "text";
        String explicitTitleNorm = normalizeLineageLabel(lineageTitle);
        String originalFileNameNorm = normalizeLineageLabel(originalFileName);
        String fileStemNorm = normalizeFileStem(originalFileName);
        String contentAnchor = buildContentAnchor(content);
        MaterialLineageIdentityKind identityKind;
        String identityKey;

        switch (normalizedSourceType) {
            case "text" -> {
                identityKind = StringUtils.hasText(explicitTitleNorm)
                    ? MaterialLineageIdentityKind.EXPLICIT_TITLE
                    : MaterialLineageIdentityKind.CONTENT_ANCHOR;
                identityKey = identityKind == MaterialLineageIdentityKind.EXPLICIT_TITLE
                    ? explicitTitleNorm
                    : contentAnchor;
            }
            case "file" -> {
                if (StringUtils.hasText(explicitTitleNorm)) {
                    identityKind = MaterialLineageIdentityKind.EXPLICIT_TITLE;
                    identityKey = explicitTitleNorm;
                } else if (StringUtils.hasText(fileStemNorm)) {
                    identityKind = MaterialLineageIdentityKind.FILE_STEM_AND_CONTENT_ANCHOR;
                    identityKey = fileStemNorm + "|" + contentAnchor;
                } else {
                    throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "material.lineage_identity_unresolvable",
                        "File materials require an explicit title or a filename-derived lineage identity"
                    );
                }
            }
            default -> {
                identityKind = MaterialLineageIdentityKind.CONTENT_ANCHOR;
                identityKey = contentAnchor;
            }
        }

        MaterialLineageIdentity identity = new MaterialLineageIdentity(
            normalizedSourceType,
            identityKind,
            identityKey,
            explicitTitleNorm,
            originalFileNameNorm,
            fileStemNorm,
            contentAnchor,
            null
        );
        return new MaterialLineageIdentity(
            identity.sourceType(),
            identity.identityKind(),
            identity.identityKey(),
            identity.explicitTitleNorm(),
            identity.originalFileNameNorm(),
            identity.fileStemNorm(),
            identity.contentAnchor(),
            buildSourceKey(identity)
        );
    }

    public String buildSourceKey(MaterialLineageIdentity identity) {
        String serializedIdentity = String.join(
            "|",
            "lineage-v3",
            identity.sourceType(),
            identity.identityKind().name(),
            identity.identityKey()
        );
        return identity.sourceType() + ":" + sha256(serializedIdentity).substring(0, 24);
    }

    public MaterialLineageIdentity buildLineageIdentity(StoredMaterialRecord record) {
        if (record == null) {
            return null;
        }

        return buildLineageIdentity(
            record.sourceType(),
            resolveExplicitLineageTitle(record),
            record.originalFileName(),
            record.normalizedContent()
        );
    }

    public String sha256(String input) {
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

    public MaterialSummary toSummary(StoredMaterialRecord record) {
        return new MaterialSummary(
            record.id(),
            record.title(),
            record.sourceType(),
            record.originalFileName(),
            record.status(),
            record.versionState(),
            record.statusReasonCode(),
            record.statusReasonMessage(),
            record.createdAt(),
            record.updatedAt(),
            record.indexingAttempts(),
            record.nextRetryAt(),
            record.content().length(),
            clip(record.content(), 180),
            record.metadata()
        );
    }

    public MaterialLineageVersion toLineageVersion(StoredMaterialRecord record, String fallbackSupersedeReason) {
        return new MaterialLineageVersion(
            record.id(),
            record.title(),
            record.sourceType(),
            record.originalFileName(),
            record.status(),
            record.versionState(),
            record.statusReasonCode(),
            record.statusReasonMessage(),
            record.createdAt(),
            record.updatedAt(),
            record.indexingAttempts(),
            record.nextRetryAt(),
            record.supersededByMaterialId(),
            record.versionState() == MaterialVersionState.SUPERSEDED
                ? (StringUtils.hasText(record.supersedeReason()) ? record.supersedeReason() : fallbackSupersedeReason)
                : record.supersedeReason(),
            record.content().length(),
            clip(record.content(), 180),
            record.metadata()
        );
    }

    public StoredMaterialRecord normalizeLegacyRecord(StoredMaterialRecord record) {
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
            : buildLineageIdentity(
                resolvedSourceType,
                resolveExplicitLineageTitle(resolvedSourceType, resolvedTitle, record.originalFileName()),
                record.originalFileName(),
                normalizedContent
            ).sourceKey();
        String extractorName = normalizeExtractor(record.extractor());
        boolean ocrUsed = Boolean.TRUE.equals(record.ocrUsed());
        List<StoredMaterialChunk> chunks = normalizeChunks(record.chunks(), storedContent, extractorName, ocrUsed);
        Integer pageCount = record.pageCount();
        Instant createdAt = record.createdAt() != null ? record.createdAt() : Instant.now();
        Instant updatedAt = record.updatedAt() != null ? record.updatedAt() : createdAt;

        return new StoredMaterialRecord(
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
            MaterialIndexingStatus.PENDING,
            MaterialVersionState.ACTIVE,
            null,
            null,
            createdAt,
            updatedAt,
            0,
            updatedAt,
            null,
            null,
            record.metadata() == null ? MaterialMetadataSnapshot.empty() : record.metadata()
        );
    }

    private void registerChunkingStrategy(ChunkingStrategy strategy) {
        chunkingStrategies.put(strategy.profile(), strategy);
    }

    public String resolveExplicitLineageTitle(String sourceType, String title, String originalFileName) {
        String normalizedTitle = normalizeLineageLabel(title);
        if (!StringUtils.hasText(normalizedTitle)) {
            return null;
        }

        if ("file".equalsIgnoreCase(sourceType)) {
            String normalizedFileName = normalizeLineageLabel(originalFileName);
            return normalizedTitle.equals(normalizedFileName) ? null : normalizedTitle;
        }

        if ("text".equalsIgnoreCase(sourceType) && DEFAULT_TEXT_TITLE.equals(normalizedTitle)) {
            return null;
        }

        return normalizedTitle;
    }

    public String resolveExplicitLineageTitle(StoredMaterialRecord record) {
        return resolveExplicitLineageTitle(record.sourceType(), record.title(), record.originalFileName());
    }

    private String normalizeLineageLabel(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String normalized = NON_ALPHANUMERIC.matcher(value.toLowerCase(Locale.ROOT).trim()).replaceAll("-");
        normalized = normalized.replaceAll("(^-+|-+$)", "");
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeFileStem(String originalFileName) {
        if (!StringUtils.hasText(originalFileName)) {
            return null;
        }

        String trimmed = originalFileName.trim();
        int extensionSeparator = trimmed.lastIndexOf('.');
        String stem = extensionSeparator > 0 ? trimmed.substring(0, extensionSeparator) : trimmed;
        return normalizeLineageLabel(stem);
    }

    private String buildContentAnchor(String content) {
        String normalizedContent = normalizeForHash(normalizeStoredContent(content)).toLowerCase(Locale.ROOT);
        List<String> tokens = orderedTokens(normalizedContent);
        if (tokens.isEmpty()) {
            return "empty";
        }

        return String.join("-", tokens.subList(0, Math.min(tokens.size(), CONTENT_ANCHOR_TOKEN_LIMIT)));
    }

    private List<String> orderedTokens(String input) {
        String normalized = NON_ALPHANUMERIC.matcher(input).replaceAll(" ");
        List<String> tokens = new ArrayList<>();
        for (String token : normalized.split("\\s+")) {
            if (token.length() >= 2) {
                tokens.add(token);
            }
        }
        return tokens;
    }

}
