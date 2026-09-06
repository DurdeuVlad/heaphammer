package com.dwurdy.heaphammer.testmod;

import com.dwurdy.heaphammer.domain.DetectionClassification;
import com.dwurdy.heaphammer.domain.ExperimentReport;
import com.dwurdy.heaphammer.report.ReportDiff;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModMatrixVerificationTest {
    private final MatrixTestFixtureManager manager = new MatrixTestFixtureManager();

    @Test
    @DisplayName("Verify existing dedicated server reports in run/heaphammer/reports if available")
    void testExistingServerReports() throws IOException {
        Path reportsDir = Path.of("run/heaphammer/reports");
        List<Path> reports = manager.findReportsInDirectory(reportsDir);

        for (Path reportPath : reports) {
            ExperimentReport report = manager.loadReport(reportPath);
            assertNotNull(report.runId());
            assertNotNull(report.detection());
            assertNotNull(report.detection().classification());
        }
    }

    @Test
    @DisplayName("Empirically verify generated matrix reports: Baseline vs Collision")
    void testRealMatrixDifferentialEvaluation() throws IOException {
        Path baselinePath = Path.of("build/matrix-reports/04_CrossMod_ModA_Alone.json");
        Path collisionPath = Path.of("build/matrix-reports/06_CrossMod_Collision.json");

        if (!Files.exists(baselinePath) || !Files.exists(collisionPath)) {
            return;
        }

        ExperimentReport reportA = manager.loadReport(baselinePath);
        ExperimentReport reportB = manager.loadReport(collisionPath);

        assertEquals(DetectionClassification.PASS, reportA.detection().classification(), "Mod A alone must PASS");
        assertEquals(DetectionClassification.SUSPICIOUS, reportB.detection().classification(), "CrossMod Collision must be SUSPICIOUS");

        ReportDiff diff = manager.compareReports(reportA, reportB);
        assertTrue(diff.classificationChanged(), "Classification must change from PASS to SUSPICIOUS");
        assertEquals(DetectionClassification.PASS, diff.classificationA());
        assertEquals(DetectionClassification.SUSPICIOUS, diff.classificationB());
        assertTrue(diff.slopeDiffMb() > 8.0, "Slope difference must exceed 8 MB/cycle");

        Path summaryPath = Path.of("build/matrix-reports/differential_summary.txt");
        Files.writeString(summaryPath, diff.summary());
        System.out.println("Real Matrix Differential Summary:\n" + diff.summary());
    }
}
