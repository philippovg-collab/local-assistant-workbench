package com.example.demo.service;

import com.example.demo.api.ApiException;
import com.example.demo.config.MaterialProperties;
import com.example.demo.infrastructure.material.ExtractedDocumentSegment;
import com.example.demo.infrastructure.material.StoredMaterialChunk;
import com.example.demo.infrastructure.material.StoredMaterialRecord;
import com.example.demo.model.MaterialIndexingStatus;
import com.example.demo.model.MaterialLineageVersion;
import com.example.demo.model.MaterialSummary;
import com.example.demo.model.MaterialVersionState;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class MaterialContentSupport {

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final int CONTENT_ANCHOR_TOKEN_LIMIT = 12;
    private static final String DEFAULT_TEXT_TITLE = "text-material";

    private final MaterialProperties properties;

    public MaterialContentSupport(MaterialProperties properties) {
        this.properties = properties;
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

    public String joinSegments(List<ExtractedDocumentSegment> segments) {
        return segments.stream()
            .map(ExtractedDocumentSegment::text)
            .filter(StringUtils::hasText)
            .collect(Collectors.joining("\n\n"));
    }

    public List<StoredMaterialChunk> buildChunks(
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
        return new MaterialLineageIdentity(
            normalizedSourceType,
            normalizeLineageLabel(lineageTitle),
            normalizeLineageLabel(originalFileName),
            normalizeFileStem(originalFileName),
            buildContentAnchor(content)
        );
    }

    public String buildSourceKey(MaterialLineageIdentity identity) {
        String serializedIdentity = String.join(
            "|",
            "lineage-v2",
            identity.sourceType(),
            identity.explicitTitle() == null ? "" : identity.explicitTitle(),
            identity.originalFileName() == null ? "" : identity.originalFileName(),
            identity.fileStem() == null ? "" : identity.fileStem(),
            identity.contentAnchor()
        );
        return identity.sourceType() + ":" + sha256(serializedIdentity).substring(0, 24);
    }

    public boolean matchesLineage(StoredMaterialRecord record, MaterialLineageIdentity candidateIdentity) {
        if (record == null || candidateIdentity == null) {
            return false;
        }

        MaterialLineageIdentity existingIdentity = buildLineageIdentity(
            record.sourceType(),
            resolveExplicitLineageTitle(record),
            record.originalFileName(),
            record.normalizedContent()
        );
        if (!existingIdentity.sourceType().equals(candidateIdentity.sourceType())) {
            return false;
        }

        return switch (candidateIdentity.sourceType()) {
            case "file" -> fileLineageMatches(existingIdentity, candidateIdentity);
            case "text" -> textLineageMatches(existingIdentity, candidateIdentity);
            default -> sameNonBlank(existingIdentity.contentAnchor(), candidateIdentity.contentAnchor());
        };
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
            clip(record.content(), 180)
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
            clip(record.content(), 180)
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
            : buildSourceKey(buildLineageIdentity(
                resolvedSourceType,
                resolveExplicitLineageTitle(resolvedSourceType, resolvedTitle, record.originalFileName()),
                record.originalFileName(),
                normalizedContent
            ));
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
            null
        );
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

    private boolean textLineageMatches(MaterialLineageIdentity existingIdentity, MaterialLineageIdentity candidateIdentity) {
        if (sameNonBlank(existingIdentity.explicitTitle(), candidateIdentity.explicitTitle())) {
            return true;
        }

        return !StringUtils.hasText(existingIdentity.explicitTitle())
            && !StringUtils.hasText(candidateIdentity.explicitTitle())
            && sameNonBlank(existingIdentity.contentAnchor(), candidateIdentity.contentAnchor());
    }

    private boolean fileLineageMatches(MaterialLineageIdentity existingIdentity, MaterialLineageIdentity candidateIdentity) {
        boolean hasExistingExplicitTitle = StringUtils.hasText(existingIdentity.explicitTitle());
        boolean hasCandidateExplicitTitle = StringUtils.hasText(candidateIdentity.explicitTitle());
        boolean sameExplicitTitle = sameNonBlank(existingIdentity.explicitTitle(), candidateIdentity.explicitTitle());
        boolean sameOriginalFileName = sameNonBlank(existingIdentity.originalFileName(), candidateIdentity.originalFileName());
        boolean sameFileStem = sameNonBlank(existingIdentity.fileStem(), candidateIdentity.fileStem());
        boolean sameContentAnchor = sameNonBlank(existingIdentity.contentAnchor(), candidateIdentity.contentAnchor());

        if (hasExistingExplicitTitle && hasCandidateExplicitTitle && !sameExplicitTitle) {
            return false;
        }

        return (sameExplicitTitle && (sameOriginalFileName || sameFileStem || sameContentAnchor))
            || ((sameOriginalFileName || sameFileStem) && sameContentAnchor);
    }

    private boolean sameNonBlank(String left, String right) {
        return StringUtils.hasText(left) && left.equals(right);
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

    public record MaterialLineageIdentity(
        String sourceType,
        String explicitTitle,
        String originalFileName,
        String fileStem,
        String contentAnchor
    ) {
    }
}
