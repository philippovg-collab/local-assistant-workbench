package com.example.demo.service.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.demo.model.EvidenceLocator;
import com.example.demo.model.eval.EvalCase;
import com.example.demo.model.eval.EvalCaseOrigin;
import com.example.demo.model.eval.EvalCaseType;
import com.example.demo.model.eval.EvalExpectedMode;
import com.example.demo.model.eval.EvalSeverity;
import com.example.demo.model.eval.RetrievalEvalCandidate;
import com.example.demo.model.eval.RetrievalEvalMetric;
import com.example.demo.model.eval.RetrievalEvalMetricStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RetrievalScoringServiceTest {

    private final RetrievalScoringService service = new RetrievalScoringService();

    @Test
    void scoresDocumentAndEvidenceMatchesAtK() {
        EvalCase evalCase = evalCase(Map.of(
            "documents",
            List.of("DOC-1", "DOC-2"),
            "forbiddenDocuments",
            List.of("DOC-X"),
            "locators",
            List.of(
                Map.of("documentNumber", "DOC-1", "chunkIndex", 2),
                Map.of("documentNumber", "DOC-2", "tableId", "T-1")
            )
        ));

        List<RetrievalEvalMetric> metrics = service.score(
            evalCase,
            List.of(
                candidate("material-a", "DOC-1", 2, null),
                candidate("material-b", "DOC-2", 4, "T-1")
            ),
            null,
            2
        );

        assertEquals(1.0d, metric(metrics, "doc_hit_rate@k").value());
        assertEquals(1.0d, metric(metrics, "doc_recall@k").value());
        assertEquals(1.0d, metric(metrics, "evidence_hit_rate@k").value());
        assertEquals(1.0d, metric(metrics, "evidence_recall@k").value());
        assertEquals(1.0d, metric(metrics, "table_locator_hit_rate@k").value());
        assertEquals(0.0d, metric(metrics, "forbidden_doc_rate").value());
        assertEquals(0.0d, metric(metrics, "retrieval_no_results_rate").value());
        assertEquals(1.0d, metric(metrics, "mrr@k").value());
        assertEquals(1.0d, metric(metrics, "ndcg@k").value());
        assertEquals(1.0d, metric(metrics, "retrieval_sufficiency").value());
    }

    @Test
    void marksGoldDependentMetricsNotScorableWhenGoldIsMissing() {
        List<RetrievalEvalMetric> metrics = service.score(evalCase(Map.of()), List.of(), null, 5);

        assertEquals(RetrievalEvalMetricStatus.NOT_SCORABLE, metric(metrics, "doc_hit_rate@k").status());
        assertEquals(RetrievalEvalMetricStatus.NOT_SCORABLE, metric(metrics, "evidence_hit_rate@k").status());
        assertEquals(RetrievalEvalMetricStatus.NOT_SCORABLE, metric(metrics, "retrieval_sufficiency").status());
        assertEquals(1.0d, metric(metrics, "filter_adherence_retrieval").value());
        assertEquals(1.0d, metric(metrics, "retrieval_no_results_rate").value());
    }

    @Test
    void noResultsMakesRankingMetricsNotScorableButRecordsFailureRate() {
        EvalCase evalCase = evalCase(Map.of("documents", List.of("DOC-1")));

        List<RetrievalEvalMetric> metrics = service.score(evalCase, List.of(), null, 5);

        assertEquals(RetrievalEvalMetricStatus.NOT_SCORABLE, metric(metrics, "mrr@k").status());
        assertEquals(RetrievalEvalMetricStatus.NOT_SCORABLE, metric(metrics, "ndcg@k").status());
        assertEquals(1.0d, metric(metrics, "retrieval_no_results_rate").value());
    }

    private EvalCase evalCase(Map<String, Object> goldEvidence) {
        return new EvalCase(
            "case-id",
            "dataset-id",
            "case-1",
            1,
            EvalCaseType.EXACT_FACT,
            EvalExpectedMode.ANSWER,
            EvalSeverity.BLOCKER,
            "What is the limit?",
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            goldEvidence,
            new EvalCaseOrigin("manual", "test", null, Map.of()),
            Instant.parse("2026-05-10T00:00:00Z"),
            Instant.parse("2026-05-10T00:00:00Z")
        );
    }

    private RetrievalEvalCandidate candidate(
        String materialId,
        String documentNumber,
        int chunkIndex,
        String tableId
    ) {
        return new RetrievalEvalCandidate(
            "FINAL",
            1,
            materialId,
            chunkIndex,
            "Material",
            "excerpt",
            null,
            null,
            null,
            null,
            null,
            100,
            null,
            new EvidenceLocator(
                "source-" + documentNumber,
                materialId,
                documentNumber,
                "v1",
                null,
                1,
                chunkIndex,
                null,
                List.of(),
                List.of(),
                tableId,
                null,
                null,
                null,
                null,
                null
            )
        );
    }

    private RetrievalEvalMetric metric(List<RetrievalEvalMetric> metrics, String name) {
        return metrics.stream()
            .filter(metric -> name.equals(metric.name()))
            .findFirst()
            .orElseThrow();
    }
}
