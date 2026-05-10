package com.example.demo.service.context;

import com.example.demo.config.ContextProperties;
import com.example.demo.model.ChatExecutionRequest;
import com.example.demo.model.ChatExecutionResponse;
import com.example.demo.model.ChatMode;
import com.example.demo.model.ChatSource;
import com.example.demo.model.ContextAssemblyHistoryItem;
import com.example.demo.model.MaterialMetadataSnapshot;
import com.example.demo.model.RetrievalQueryReferencedSource;
import com.example.demo.model.RetrievalQueryResolution;
import com.example.demo.model.RetrievalQueryResolutionDecision;
import com.example.demo.service.audit.port.ChatRunTraceRepository;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RetrievalQueryResolutionService {

    private static final int MAX_RESOLVED_QUERY_LENGTH = 1200;
    private static final Pattern SOURCE_INDEX_PATTERN = Pattern.compile(
        "(?iu)(?:^|[\\s,.;:!?])(?:source|источник)\\s*(\\d+)(?=$|[\\s,.;:!?])"
    );
    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}\\b");
    private static final Pattern DOTTED_DATE_PATTERN = Pattern.compile("\\b\\d{2}[./]\\d{2}[./]\\d{4}\\b");
    private static final Pattern DOCUMENT_NUMBER_PATTERN = Pattern.compile(
        "(?iu)(?:№\\s*[\\p{L}\\p{N}][\\p{L}\\p{N}/._-]{2,}|\\b[A-Z]{2,}(?:-[A-Z0-9]{2,}){1,}\\b)"
    );
    private static final Pattern PROJECT_PATTERN = Pattern.compile("(?iu)(?:по\\s+проекту|project)\\s+[\\p{L}\\p{N}]");
    private static final Pattern COUNTERPARTY_PATTERN = Pattern.compile("(?iu)(?:контрагент|counterparty)\\s*[: ]\\s*[\\p{L}\\p{N}]");

    private final ContextProperties contextProperties;
    private final ChatRunTraceRepository traceRepository;

    public RetrievalQueryResolutionService(
        ContextProperties contextProperties,
        ChatRunTraceRepository traceRepository
    ) {
        this.contextProperties = contextProperties;
        this.traceRepository = traceRepository;
    }

    public RetrievalQueryResolution resolve(
        ChatExecutionRequest request,
        ChatMode mode,
        PreparedContextAssembly contextAssembly
    ) {
        String originalQuery = normalizeQuery(request == null ? null : request.prompt());
        if (!isEnabled(request, mode, contextAssembly)) {
            return RetrievalQueryResolution.disabled(originalQuery);
        }

        List<PriorRagTurn> priorRagTurns = priorRagTurns(contextAssembly.selectedHistory());
        if (priorRagTurns.isEmpty()) {
            return originalDecision(
                originalQuery,
                RetrievalQueryResolutionDecision.NO_HISTORY,
                0.0d,
                List.of(),
                false,
                null
            );
        }

        MarkerSet markers = markers(originalQuery);
        if (markers.markers().isEmpty()) {
            return originalDecision(
                originalQuery,
                RetrievalQueryResolutionDecision.NOT_FOLLOW_UP,
                0.0d,
                List.of(),
                false,
                null
            );
        }
        if (!markers.hasOrdinalMarker() && hasStandaloneAnchor(originalQuery)) {
            return originalDecision(
                originalQuery,
                RetrievalQueryResolutionDecision.NOT_FOLLOW_UP,
                0.0d,
                markers.markers(),
                false,
                null
            );
        }

        PriorRagTurn latestTurn = priorRagTurns.getFirst();
        Integer sourceIndex = sourceIndex(originalQuery);
        if (sourceIndex != null) {
            return resolveRawSource(originalQuery, markers.markers(), latestTurn, sourceIndex);
        }

        Integer documentIndex = documentIndex(originalQuery);
        if (documentIndex != null) {
            return resolveDistinctDocument(originalQuery, markers.markers(), latestTurn, documentIndex);
        }

        if (markers.hasDeicticMarker() && latestTurn.sources().size() == 1) {
            IndexedSource source = new IndexedSource(latestTurn.sources().getFirst(), 1, 1);
            return resolved(originalQuery, markers.markers(), latestTurn, List.of(source), 0.80d);
        }

        if (markers.hasVagueDeicticMarker() && latestTurn.sources().size() > 1) {
            return lowConfidence(originalQuery, markers.markers(), latestTurn, "ambiguous_follow_up_reference");
        }

        if (markers.hasContinuationMarker() && priorRagTurns.size() == 1 && !latestTurn.sources().isEmpty()) {
            return resolved(originalQuery, markers.markers(), latestTurn, indexedSources(latestTurn.sources()), 0.70d);
        }

        return lowConfidence(originalQuery, markers.markers(), latestTurn, "low_confidence_follow_up");
    }

    private boolean isEnabled(
        ChatExecutionRequest request,
        ChatMode mode,
        PreparedContextAssembly contextAssembly
    ) {
        if (mode != ChatMode.RAG || request == null) {
            return false;
        }
        if (!contextProperties.isRetrievalQueryResolutionEnabled()) {
            return false;
        }
        if (request.contextOptions() != null && Boolean.FALSE.equals(request.contextOptions().resolveRetrievalQuery())) {
            return false;
        }
        return contextAssembly != null && contextAssembly.active();
    }

    private List<PriorRagTurn> priorRagTurns(List<ContextAssemblyHistoryItem> selectedHistory) {
        Map<String, HistoryRun> historyRuns = new LinkedHashMap<>();
        if (selectedHistory == null) {
            return List.of();
        }
        for (ContextAssemblyHistoryItem item : selectedHistory) {
            if (item == null || !StringUtils.hasText(item.runId())) {
                continue;
            }
            HistoryRun current = historyRuns.get(item.runId());
            int turnNo = item.turnNo() == null ? 0 : item.turnNo();
            String prompt = current == null ? null : current.userPrompt();
            if ("user".equals(item.role()) && StringUtils.hasText(item.content())) {
                prompt = item.content();
            }
            historyRuns.put(item.runId(), new HistoryRun(item.runId(), turnNo, prompt));
        }

        List<PriorRagTurn> turns = new ArrayList<>();
        for (HistoryRun run : historyRuns.values()) {
            ChatExecutionResponse response = traceRepository.findResult(run.runId()).orElse(null);
            if (response == null || response.mode() != ChatMode.RAG || response.sources().isEmpty()) {
                continue;
            }
            turns.add(new PriorRagTurn(
                run.runId(),
                run.turnNo(),
                firstNonBlank(run.userPrompt(), response.prompt()),
                response.sources()
            ));
        }
        turns.sort(Comparator.comparingInt(PriorRagTurn::turnNo).reversed());
        return turns;
    }

    private RetrievalQueryResolution resolveRawSource(
        String originalQuery,
        List<String> markers,
        PriorRagTurn turn,
        int sourceIndex
    ) {
        if (sourceIndex < 1 || sourceIndex > turn.sources().size()) {
            return lowConfidence(originalQuery, markers, turn, "source_index_out_of_range");
        }
        IndexedSource source = new IndexedSource(turn.sources().get(sourceIndex - 1), sourceIndex, null);
        return resolved(originalQuery, markers, turn, List.of(source), 0.95d);
    }

    private RetrievalQueryResolution resolveDistinctDocument(
        String originalQuery,
        List<String> markers,
        PriorRagTurn turn,
        int documentIndex
    ) {
        List<IndexedSource> distinctDocuments = distinctDocuments(turn.sources());
        if (documentIndex < 1 || documentIndex > distinctDocuments.size()) {
            return lowConfidence(originalQuery, markers, turn, "document_index_out_of_range");
        }
        return resolved(originalQuery, markers, turn, List.of(distinctDocuments.get(documentIndex - 1)), 0.95d);
    }

    private RetrievalQueryResolution resolved(
        String originalQuery,
        List<String> markers,
        PriorRagTurn turn,
        List<IndexedSource> sources,
        double confidence
    ) {
        List<RetrievalQueryReferencedSource> referencedSources = sources.stream()
            .map(this::referencedSource)
            .toList();
        String resolvedQuery = buildResolvedQuery(originalQuery, turn.userPrompt(), referencedSources);
        return new RetrievalQueryResolution(
            originalQuery,
            resolvedQuery,
            resolvedQuery,
            RetrievalQueryResolutionDecision.RESOLVED,
            confidence,
            markers,
            List.of(turn.runId()),
            referencedSources,
            false,
            null
        );
    }

    private RetrievalQueryResolution lowConfidence(
        String originalQuery,
        List<String> markers,
        PriorRagTurn turn,
        String reason
    ) {
        return new RetrievalQueryResolution(
            originalQuery,
            originalQuery,
            null,
            RetrievalQueryResolutionDecision.LOW_CONFIDENCE,
            0.50d,
            markers,
            turn == null ? List.of() : List.of(turn.runId()),
            List.of(),
            true,
            reason
        );
    }

    private RetrievalQueryResolution originalDecision(
        String originalQuery,
        RetrievalQueryResolutionDecision decision,
        double confidence,
        List<String> markers,
        boolean degraded,
        String degradedReason
    ) {
        return new RetrievalQueryResolution(
            originalQuery,
            originalQuery,
            null,
            decision,
            confidence,
            markers,
            List.of(),
            List.of(),
            degraded,
            degradedReason
        );
    }

    private MarkerSet markers(String query) {
        LinkedHashSet<String> markers = new LinkedHashSet<>();
        boolean continuation = false;
        boolean deictic = false;
        boolean vagueDeictic = false;
        boolean ordinal = false;

        if (startsWithWord(query, "а")) {
            markers.add("а");
            continuation = true;
        }
        if (startsWithWord(query, "и")) {
            markers.add("и");
            continuation = true;
        }
        if (containsWord(query, "ещё") || containsWord(query, "еще")) {
            markers.add("ещё");
            continuation = true;
        }
        if (containsWord(query, "также")) {
            markers.add("также");
            continuation = true;
        }
        if (containsWord(query, "also")) {
            markers.add("also");
            continuation = true;
        }
        if (startsWithWord(query, "and")) {
            markers.add("and");
            continuation = true;
        }
        if (containsPhrase(query, "what about")) {
            markers.add("what about");
            continuation = true;
        }
        if (containsPhrase(query, "по нему") || containsPhrase(query, "по ней") || containsPhrase(query, "по этому")) {
            markers.add("deictic");
            deictic = true;
        }
        if (containsWord(query, "там") || containsWord(query, "it") || containsWord(query, "that")
            || containsWord(query, "this") || containsPrefix(query, "предыдущ") || containsWord(query, "previous")
            || containsWord(query, "above") || containsWord(query, "выше")) {
            markers.add("vague_deictic");
            deictic = true;
            vagueDeictic = true;
        }
        if (sourceIndex(query) != null) {
            markers.add("source_index");
            ordinal = true;
        }
        if (documentIndex(query) != null) {
            markers.add("document_index");
            ordinal = true;
        }

        return new MarkerSet(List.copyOf(markers), continuation, deictic, vagueDeictic, ordinal);
    }

    private Integer sourceIndex(String query) {
        Matcher matcher = SOURCE_INDEX_PATTERN.matcher(query == null ? "" : query);
        if (!matcher.find()) {
            return null;
        }
        return parsePositiveInt(matcher.group(1));
    }

    private Integer documentIndex(String query) {
        if (containsWord(query, "first") || containsPrefix(query, "перв") || containsPhrase(query, "document 1")
            || containsPhrase(query, "документ 1")) {
            return 1;
        }
        if (containsWord(query, "second") || containsPrefix(query, "втор") || containsPhrase(query, "document 2")
            || containsPhrase(query, "документ 2")) {
            return 2;
        }
        if (containsWord(query, "third") || containsPrefix(query, "трет") || containsPhrase(query, "document 3")
            || containsPhrase(query, "документ 3")) {
            return 3;
        }
        return null;
    }

    private List<IndexedSource> indexedSources(List<ChatSource> sources) {
        List<IndexedSource> indexed = new ArrayList<>();
        for (int index = 0; index < Math.min(4, sources.size()); index++) {
            indexed.add(new IndexedSource(sources.get(index), index + 1, null));
        }
        return indexed;
    }

    private List<IndexedSource> distinctDocuments(List<ChatSource> sources) {
        Map<String, IndexedSource> byDocumentKey = new LinkedHashMap<>();
        for (int index = 0; index < sources.size(); index++) {
            ChatSource source = sources.get(index);
            String documentKey = documentKey(source);
            byDocumentKey.putIfAbsent(
                documentKey,
                new IndexedSource(source, index + 1, byDocumentKey.size() + 1)
            );
        }
        return List.copyOf(byDocumentKey.values());
    }

    private String documentKey(ChatSource source) {
        MaterialMetadataSnapshot metadata = source == null ? null : source.metadata();
        return firstNonBlank(
            metadata == null ? null : metadata.documentNumber(),
            source == null ? null : source.materialId(),
            normalizeTitle(source == null ? null : source.title())
        );
    }

    private RetrievalQueryReferencedSource referencedSource(IndexedSource indexedSource) {
        ChatSource source = indexedSource.source();
        MaterialMetadataSnapshot metadata = source.metadata();
        return new RetrievalQueryReferencedSource(
            indexedSource.sourceIndex(),
            indexedSource.documentIndex(),
            source.materialId(),
            source.title(),
            metadata.documentNumber(),
            metadata.project(),
            metadata.counterparty(),
            source.page()
        );
    }

    private String buildResolvedQuery(
        String originalQuery,
        String previousUserPrompt,
        List<RetrievalQueryReferencedSource> referencedSources
    ) {
        StringBuilder builder = new StringBuilder();
        appendField(builder, "Follow-up request", originalQuery);
        appendField(builder, "Previous user request", previousUserPrompt);
        for (RetrievalQueryReferencedSource source : referencedSources) {
            builder.append("\nSource metadata:");
            appendInline(builder, "sourceIndex", source.sourceIndex());
            appendInline(builder, "documentIndex", source.documentIndex());
            appendInline(builder, "title", source.title());
            appendInline(builder, "documentNumber", source.documentNumber());
            appendInline(builder, "project", source.project());
            appendInline(builder, "counterparty", source.counterparty());
            appendInline(builder, "page", source.page());
            appendInline(builder, "materialId", source.materialId());
        }
        return limit(normalizeQuery(builder.toString()), MAX_RESOLVED_QUERY_LENGTH);
    }

    private void appendField(StringBuilder builder, String label, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append('\n');
        }
        builder.append(label).append(": ").append(value.trim());
    }

    private void appendInline(StringBuilder builder, String label, Object value) {
        if (value == null || !StringUtils.hasText(value.toString())) {
            return;
        }
        builder.append(' ').append(label).append('=').append(value);
    }

    private boolean hasStandaloneAnchor(String query) {
        return DOCUMENT_NUMBER_PATTERN.matcher(query).find()
            || ISO_DATE_PATTERN.matcher(query).find()
            || DOTTED_DATE_PATTERN.matcher(query).find()
            || PROJECT_PATTERN.matcher(query).find()
            || COUNTERPARTY_PATTERN.matcher(query).find();
    }

    private boolean containsWord(String query, String word) {
        return Pattern.compile(
            "(?iu)(?<![\\p{L}\\p{N}_])" + Pattern.quote(word) + "(?![\\p{L}\\p{N}_])"
        ).matcher(query == null ? "" : query).find();
    }

    private boolean startsWithWord(String query, String word) {
        return Pattern.compile(
            "(?iu)^\\s*" + Pattern.quote(word) + "(?![\\p{L}\\p{N}_])"
        ).matcher(query == null ? "" : query).find();
    }

    private boolean containsPrefix(String query, String prefix) {
        return Pattern.compile(
            "(?iu)(?<![\\p{L}\\p{N}_])" + Pattern.quote(prefix) + "[\\p{L}]*"
        ).matcher(query == null ? "" : query).find();
    }

    private boolean containsPhrase(String query, String phrase) {
        return normalizeQuery(query).toLowerCase(Locale.ROOT)
            .contains(normalizeQuery(phrase).toLowerCase(Locale.ROOT));
    }

    private Integer parsePositiveInt(String rawValue) {
        try {
            int value = Integer.parseInt(rawValue);
            return value > 0 ? value : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String normalizeQuery(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value
            .replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private String normalizeTitle(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)
            .toLowerCase(Locale.ROOT)
            .replaceAll("\\s+", " ");
        return normalized.isBlank() ? "" : normalized;
    }

    private String limit(String value, int limit) {
        if (value == null || value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit).trim();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private record HistoryRun(String runId, int turnNo, String userPrompt) {
    }

    private record PriorRagTurn(
        String runId,
        int turnNo,
        String userPrompt,
        List<ChatSource> sources
    ) {
        private PriorRagTurn {
            sources = sources == null ? List.of() : List.copyOf(sources);
        }
    }

    private record IndexedSource(ChatSource source, int sourceIndex, Integer documentIndex) {
    }

    private record MarkerSet(
        List<String> markers,
        boolean hasContinuationMarker,
        boolean hasDeicticMarker,
        boolean hasVagueDeicticMarker,
        boolean hasOrdinalMarker
    ) {
        private MarkerSet {
            Set<String> uniqueMarkers = new LinkedHashSet<>(markers == null ? List.of() : markers);
            markers = List.copyOf(uniqueMarkers);
        }
    }
}
