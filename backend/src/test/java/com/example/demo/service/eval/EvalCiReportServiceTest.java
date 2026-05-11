package com.example.demo.service.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.model.eval.EvalCiReport;
import com.example.demo.model.eval.EvalRunItemStatus;
import java.time.Clock;
import org.junit.jupiter.api.Test;

class EvalCiReportServiceTest {

    private final EvalCiReportFixtures fixtures = new EvalCiReportFixtures(
        Clock.fixed(EvalCiReportFixtures.GENERATED_AT, EvalCiReportFixtures.UTC)
    );

    @Test
    void emitsStableReportContractWithNamedMetricsAndSlices() {
        EvalCiReport report = fixtures.report("smoke");

        assertEquals(EvalCiReportService.SCHEMA_VERSION, report.schemaVersion());
        assertEquals("smoke", report.mode());
        assertEquals("PASS", report.gateStatus());
        assertEquals("COMPLETED", report.runStatus());
        assertEquals("PASS", report.overallVerdict());
        assertEquals("SMOKE", report.datasetKind().name());
        assertEquals(1.0d, report.metrics().get("answer_correctness").value());
        assertEquals(1.0d, report.metrics().get("groundedness").value());
        assertEquals(1.0d, report.metrics().get("citation_precision").value());
        assertEquals(1.0d, report.metrics().get("instruction_adherence").value());
        assertEquals(1.0d, report.metrics().get("abstention_recall").value());
        assertEquals(1.0d, report.metrics().get("clarification_recall").value());
        assertTrue(report.slices().stream().anyMatch(slice -> "NO_ANSWER".equals(slice.name())));
        assertTrue(report.slices().stream().anyMatch(slice -> "AMBIGUOUS_QUERY".equals(slice.name())));
        assertEquals(4L, report.itemCounts().get("total"));
        assertEquals(4L, report.itemCounts().get("passed"));
    }

    @Test
    void compareReportCarriesStrictReleaseGateFields() {
        EvalCiReport report = fixtures.report("compare");

        assertEquals("PASS", report.overallVerdict());
        assertEquals("mapping-ci", report.mappingHash());
        assertTrue(report.metricSummary().containsKey("answer_correctness"));
    }

    @Test
    void failedItemsBecomeConcreteGateFailures() {
        EvalCiReport report = fixtures.report("smoke", EvalRunItemStatus.FAILED);

        assertEquals("FAIL", report.gateStatus());
        assertEquals("FAIL", report.overallVerdict());
        assertEquals(1, report.failures().size());
        assertEquals("OUTPUT_FORMAT_ERROR", report.failures().getFirst().code());
        assertEquals("BLOCKER", report.failures().getFirst().severity());
    }
}
