package com.example.demo.service.eval;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.demo.model.eval.EvalCiReport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import org.junit.jupiter.api.Test;

class EvalCiReportGenerationTest {

    @Test
    void writesCiReportForRequestedMode() throws Exception {
        String mode = System.getProperty("eval.ci.mode", "smoke");
        Path reportDirectory = Path.of(System.getProperty("eval.ci.report.dir", "target/eval-reports"));
        EvalCiReportFixtures fixtures = new EvalCiReportFixtures(
            Clock.fixed(EvalCiReportFixtures.GENERATED_AT, EvalCiReportFixtures.UTC)
        );
        EvalCiReport report = fixtures.report(mode);

        fixtures.reportService().writeReport(report, reportDirectory);

        assertTrue(Files.isRegularFile(reportDirectory.resolve(mode + "-report.json")));
        assertTrue(Files.isRegularFile(reportDirectory.resolve(mode + "-report.md")));
    }
}
