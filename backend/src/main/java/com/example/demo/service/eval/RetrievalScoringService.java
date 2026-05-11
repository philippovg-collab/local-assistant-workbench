package com.example.demo.service.eval;

import com.example.demo.model.EvidenceLocator;
import com.example.demo.model.RetrievalFilters;
import com.example.demo.model.eval.EvalCase;
import com.example.demo.model.eval.RetrievalEvalCandidate;
import com.example.demo.model.eval.RetrievalEvalMetric;
import com.example.demo.service.RetrievalSearchExecution;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class RetrievalScoringService {

    public List<RetrievalEvalMetric> score(
        EvalCase evalCase,
        List<RetrievalEvalCandidate> finalCandidates,
        RetrievalSearchExecution execution,
        int requestedK
    ) {
        int k = Math.max(1, requestedK);
        List<RetrievalEvalCandidate> top = safeCandidates(finalCandidates).stream().limit(k).toList();
        GoldSpec gold = GoldSpec.from(evalCase);
        List<RetrievalEvalMetric> metrics = new ArrayList<>();
        metrics.add(docHitRate(top, gold, k));
        metrics.add(docRecall(top, gold, k));
        metrics.add(evidenceHitRate(top, gold, k));
        metrics.add(evidenceRecall(top, gold, k));
        metrics.add(tableLocatorHitRate(top, gold, k));
        metrics.add(filterAdherence(execution, k));
        metrics.add(forbiddenDocRate(top, gold, k));
        metrics.add(noResultsRate(top, k));
        metrics.add(mrr(top, gold, k));
        metrics.add(ndcg(top, gold, k));
        metrics.add(retrievalSufficiency(top, gold, k));
        return List.copyOf(metrics);
    }

    private RetrievalEvalMetric docHitRate(List<RetrievalEvalCandidate> top, GoldSpec gold, int k) {
        if (gold.requiredDocuments().isEmpty()) {
            return RetrievalEvalMetric.notScorable("doc_hit_rate@k", k, "No required documents are defined");
        }
        return RetrievalEvalMetric.scored("doc_hit_rate@k", k, matchedDocuments(top, gold).isEmpty() ? 0.0d : 1.0d);
    }

    private RetrievalEvalMetric docRecall(List<RetrievalEvalCandidate> top, GoldSpec gold, int k) {
        if (gold.requiredDocuments().isEmpty()) {
            return RetrievalEvalMetric.notScorable("doc_recall@k", k, "No required documents are defined");
        }
        return RetrievalEvalMetric.scored(
            "doc_recall@k",
            k,
            matchedDocuments(top, gold).size() / (double) gold.requiredDocuments().size()
        );
    }

    private RetrievalEvalMetric evidenceHitRate(List<RetrievalEvalCandidate> top, GoldSpec gold, int k) {
        if (gold.locators().isEmpty()) {
            return RetrievalEvalMetric.notScorable("evidence_hit_rate@k", k, "No gold evidence locators are defined");
        }
        return RetrievalEvalMetric.scored("evidence_hit_rate@k", k, matchedLocators(top, gold.locators()).isEmpty() ? 0.0d : 1.0d);
    }

    private RetrievalEvalMetric evidenceRecall(List<RetrievalEvalCandidate> top, GoldSpec gold, int k) {
        if (gold.locators().isEmpty()) {
            return RetrievalEvalMetric.notScorable("evidence_recall@k", k, "No gold evidence locators are defined");
        }
        return RetrievalEvalMetric.scored(
            "evidence_recall@k",
            k,
            matchedLocators(top, gold.locators()).size() / (double) gold.locators().size()
        );
    }

    private RetrievalEvalMetric tableLocatorHitRate(List<RetrievalEvalCandidate> top, GoldSpec gold, int k) {
        List<GoldLocator> tableLocators = gold.locators().stream().filter(GoldLocator::isTableLocator).toList();
        if (tableLocators.isEmpty()) {
            return RetrievalEvalMetric.notScorable("table_locator_hit_rate@k", k, "No table/cell gold locators are defined");
        }
        return RetrievalEvalMetric.scored(
            "table_locator_hit_rate@k",
            k,
            matchedLocators(top, tableLocators).isEmpty() ? 0.0d : 1.0d
        );
    }

    private RetrievalEvalMetric filterAdherence(RetrievalSearchExecution execution, int k) {
        if (execution == null) {
            return RetrievalEvalMetric.scored("filter_adherence_retrieval", k, 1.0d);
        }
        RetrievalFilters filters = execution.effectiveFilters();
        boolean adheres = execution.matches().stream()
            .limit(k)
            .allMatch(match -> filters.matches(match.source().metadata()));
        return RetrievalEvalMetric.scored("filter_adherence_retrieval", k, adheres ? 1.0d : 0.0d);
    }

    private RetrievalEvalMetric forbiddenDocRate(List<RetrievalEvalCandidate> top, GoldSpec gold, int k) {
        if (gold.forbiddenDocuments().isEmpty()) {
            return RetrievalEvalMetric.notScorable("forbidden_doc_rate", k, "No forbidden documents are defined");
        }
        if (top.isEmpty()) {
            return RetrievalEvalMetric.scored("forbidden_doc_rate", k, 0.0d);
        }
        long forbidden = top.stream().filter(candidate -> matchesAnyDocument(candidate, gold.forbiddenDocuments())).count();
        return RetrievalEvalMetric.scored("forbidden_doc_rate", k, forbidden / (double) top.size());
    }

    private RetrievalEvalMetric mrr(List<RetrievalEvalCandidate> top, GoldSpec gold, int k) {
        if (!gold.isRelevantScorable()) {
            return RetrievalEvalMetric.notScorable("mrr@k", k, "No required documents or evidence locators are defined");
        }
        if (top.isEmpty()) {
            return RetrievalEvalMetric.notScorable("mrr@k", k, "No retrieval candidates were returned");
        }
        for (int index = 0; index < top.size(); index++) {
            if (gold.isRelevant(top.get(index))) {
                return RetrievalEvalMetric.scored("mrr@k", k, 1.0d / (index + 1));
            }
        }
        return RetrievalEvalMetric.scored("mrr@k", k, 0.0d);
    }

    private RetrievalEvalMetric ndcg(List<RetrievalEvalCandidate> top, GoldSpec gold, int k) {
        if (!gold.isRelevantScorable()) {
            return RetrievalEvalMetric.notScorable("ndcg@k", k, "No required documents or evidence locators are defined");
        }
        if (top.isEmpty()) {
            return RetrievalEvalMetric.notScorable("ndcg@k", k, "No retrieval candidates were returned");
        }
        double dcg = 0.0d;
        int relevantCount = 0;
        for (int index = 0; index < top.size(); index++) {
            if (gold.isRelevant(top.get(index))) {
                relevantCount++;
                dcg += 1.0d / log2(index + 2.0d);
            }
        }
        int idealRelevant = Math.max(1, Math.min(k, Math.max(relevantCount, gold.relevantTargetCount())));
        double idcg = 0.0d;
        for (int index = 0; index < idealRelevant; index++) {
            idcg += 1.0d / log2(index + 2.0d);
        }
        return RetrievalEvalMetric.scored("ndcg@k", k, idcg == 0.0d ? 0.0d : dcg / idcg);
    }

    private RetrievalEvalMetric noResultsRate(List<RetrievalEvalCandidate> top, int k) {
        return RetrievalEvalMetric.scored("retrieval_no_results_rate", k, top.isEmpty() ? 1.0d : 0.0d);
    }

    private RetrievalEvalMetric retrievalSufficiency(List<RetrievalEvalCandidate> top, GoldSpec gold, int k) {
        if (!gold.locators().isEmpty()) {
            return RetrievalEvalMetric.scored(
                "retrieval_sufficiency",
                k,
                matchedLocators(top, gold.locators()).size() == gold.locators().size() ? 1.0d : 0.0d
            );
        }
        if (!gold.requiredDocuments().isEmpty()) {
            return RetrievalEvalMetric.scored(
                "retrieval_sufficiency",
                k,
                matchedDocuments(top, gold).size() == gold.requiredDocuments().size() ? 1.0d : 0.0d
            );
        }
        return RetrievalEvalMetric.notScorable(
            "retrieval_sufficiency",
            k,
            "No required documents or evidence locators are defined"
        );
    }

    private Set<String> matchedDocuments(List<RetrievalEvalCandidate> top, GoldSpec gold) {
        Set<String> matched = new LinkedHashSet<>();
        for (String document : gold.requiredDocuments()) {
            if (top.stream().anyMatch(candidate -> matchesDocument(candidate, document))) {
                matched.add(document);
            }
        }
        return matched;
    }

    private Set<GoldLocator> matchedLocators(List<RetrievalEvalCandidate> top, List<GoldLocator> locators) {
        Set<GoldLocator> matched = new LinkedHashSet<>();
        for (GoldLocator locator : locators) {
            if (top.stream().anyMatch(candidate -> locator.matches(candidate.evidenceLocator()))) {
                matched.add(locator);
            }
        }
        return matched;
    }

    private boolean matchesAnyDocument(RetrievalEvalCandidate candidate, Set<String> documents) {
        return documents.stream().anyMatch(document -> matchesDocument(candidate, document));
    }

    private static boolean matchesDocument(RetrievalEvalCandidate candidate, String document) {
        String expected = normalize(document);
        if (expected == null || candidate == null) {
            return false;
        }
        EvidenceLocator locator = candidate.evidenceLocator();
        return expected.equals(normalize(candidate.materialId()))
            || (locator != null
                && (expected.equals(normalize(locator.materialId()))
                    || expected.equals(normalize(locator.sourceKey()))
                    || expected.equals(normalize(locator.documentNumber()))));
    }

    private List<RetrievalEvalCandidate> safeCandidates(List<RetrievalEvalCandidate> candidates) {
        return candidates == null ? List.of() : candidates;
    }

    private double log2(double value) {
        return Math.log(value) / Math.log(2.0d);
    }

    private static String normalize(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text.toLowerCase(Locale.ROOT);
    }

    private record GoldSpec(
        Set<String> requiredDocuments,
        Set<String> forbiddenDocuments,
        List<GoldLocator> locators
    ) {
        private static GoldSpec from(EvalCase evalCase) {
            if (evalCase == null) {
                return new GoldSpec(Set.of(), Set.of(), List.of());
            }
            List<GoldLocator> locators = new ArrayList<>(GoldLocator.fromEvidenceLocators(evalCase.goldEvidenceLocators()));
            for (List<EvidenceLocator> group : evalCase.requiredDocGroups()) {
                locators.addAll(GoldLocator.fromEvidenceLocators(group));
            }
            Set<String> requiredDocuments = documentRefs(evalCase.goldEvidenceLocators(), evalCase.requiredDocGroups());
            return new GoldSpec(
                requiredDocuments,
                normalizedSet(evalCase.forbiddenDocumentRefs()),
                List.copyOf(locators)
            );
        }

        private boolean isRelevantScorable() {
            return !requiredDocuments.isEmpty() || !locators.isEmpty();
        }

        private boolean isRelevant(RetrievalEvalCandidate candidate) {
            return requiredDocuments.stream().anyMatch(document -> RetrievalScoringService.matchesDocument(candidate, document))
                || locators.stream().anyMatch(locator -> locator.matches(candidate.evidenceLocator()));
        }

        private int relevantTargetCount() {
            return Math.max(requiredDocuments.size(), locators.size());
        }

        private static Set<String> documentRefs(
            List<EvidenceLocator> locators,
            List<List<EvidenceLocator>> groups
        ) {
            LinkedHashSet<String> documents = new LinkedHashSet<>();
            collectDocumentRefs(documents, locators);
            if (groups != null) {
                for (List<EvidenceLocator> group : groups) {
                    collectDocumentRefs(documents, group);
                }
            }
            return Set.copyOf(documents);
        }

        private static void collectDocumentRefs(LinkedHashSet<String> documents, List<EvidenceLocator> locators) {
            if (locators == null) {
                return;
            }
            for (EvidenceLocator locator : locators) {
                addDocument(documents, locator == null ? null : locator.documentNumber());
                addDocument(documents, locator == null ? null : locator.materialId());
                addDocument(documents, locator == null ? null : locator.sourceKey());
            }
        }

        private static void addDocument(LinkedHashSet<String> documents, String value) {
            String normalized = RetrievalScoringService.normalize(value);
            if (normalized != null) {
                documents.add(normalized);
            }
        }

        private static Set<String> normalizedSet(List<String> values) {
            if (values == null || values.isEmpty()) {
                return Set.of();
            }
            LinkedHashSet<String> normalized = new LinkedHashSet<>();
            values.stream().map(RetrievalScoringService::normalize).filter(item -> item != null).forEach(normalized::add);
            return Set.copyOf(normalized);
        }
    }

    private record GoldLocator(
        String sourceKey,
        String materialId,
        String documentNumber,
        String versionLabel,
        Integer chunkIndex,
        Integer page,
        String tableId,
        String rowKey,
        String columnKey
    ) {
        private static List<GoldLocator> fromEvidenceLocators(List<EvidenceLocator> rawLocators) {
            if (rawLocators == null || rawLocators.isEmpty()) {
                return List.of();
            }
            List<GoldLocator> locators = new ArrayList<>();
            for (EvidenceLocator locator : rawLocators) {
                locators.add(new GoldLocator(
                    normalize(locator.sourceKey()),
                    normalize(locator.materialId()),
                    normalize(locator.documentNumber()),
                    normalize(locator.versionLabel()),
                    locator.chunkIndex(),
                    locator.page(),
                    normalize(locator.tableId()),
                    normalize(locator.rowKey()),
                    normalize(locator.columnKey())
                ));
            }
            return List.copyOf(locators);
        }

        private boolean isTableLocator() {
            return tableId != null || rowKey != null || columnKey != null;
        }

        private boolean matches(EvidenceLocator locator) {
            if (locator == null) {
                return false;
            }
            return matchesText(sourceKey, locator.sourceKey())
                && matchesText(materialId, locator.materialId())
                && matchesText(documentNumber, locator.documentNumber())
                && matchesText(versionLabel, locator.versionLabel())
                && matchesNumber(chunkIndex, locator.chunkIndex())
                && matchesNumber(page, locator.page())
                && matchesText(tableId, locator.tableId())
                && matchesText(rowKey, locator.rowKey())
                && matchesText(columnKey, locator.columnKey());
        }

        private boolean matchesText(String expected, String actual) {
            return expected == null || expected.equals(normalize(actual));
        }

        private boolean matchesNumber(Integer expected, Integer actual) {
            return expected == null || expected.equals(actual);
        }

    }
}
