package com.dwurdy.heaphammer.report;

import com.dwurdy.heaphammer.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ReportServiceTest {

    @Test
    @DisplayName("PlanStorage and ReportService persist and restore plans and reports atomically")
    void testAtomicSaveAndLoad(@TempDir Path tempDir) throws IOException {
        PlanStorage planStorage = new PlanStorage(tempDir);
        ReportService reportService = new ReportService(tempDir);

        ExperimentSpec spec = ExperimentSpec.builder().seed(42L).radius(4).iterations(3).build();
        ExperimentPlan plan = new ExperimentPlan(
                ExperimentId.of("plan-101"), System.currentTimeMillis(), spec,
                List.of(new ResolvedChunkOperation(0, 0, "minecraft:overworld", 1, 2, "ACQUIRE")),
                50, 1
        );

        planStorage.savePlan(plan);
        Optional<ExperimentPlan> loadedPlan = planStorage.loadPlan(ExperimentId.of("plan-101"));
        assertTrue(loadedPlan.isPresent());
        assertEquals(plan.id(), loadedPlan.get().id());
        assertEquals(1, loadedPlan.get().operations().size());

        EnvironmentFingerprint env = new EnvironmentFingerprint("1.0", "1.21.1", "0.19.5", "21", 123L, "hash", Map.of());
        DetectionResult det = new DetectionResult(DetectionClassification.PASS, 0.9, 0.0, 0.0, 0L, "Pass");
        ExperimentReport report = new ExperimentReport(
                ExperimentId.of("run-202"), 1000L, 2000L, "COMPLETED", spec, env, List.of(), det, "/hh run", List.of()
        );

        reportService.saveReport(report);
        Optional<ExperimentReport> loadedReport = reportService.loadReport(ExperimentId.of("run-202"));
        assertTrue(loadedReport.isPresent());
        assertEquals(report.runId(), loadedReport.get().runId());
        assertEquals("COMPLETED", loadedReport.get().status());

        List<String> reportIds = reportService.listReportIds();
        assertTrue(reportIds.contains("run-202"));

        String summary = reportService.formatSummary(loadedReport.get());
        assertTrue(summary.contains("run-202"));
        assertTrue(summary.contains("PASS"));
    }

    @Test
    @DisplayName("Canonical command builder constructs reproducible command string")
    void testCanonicalCommand() {
        ExperimentSpec spec = ExperimentSpec.builder()
                .seed(123456L)
                .center(5, -10)
                .radius(8)
                .iterations(10)
                .batchSize(6)
                .strategy("SPIRAL")
                .warmupIterations(2)
                .holdTicks(25)
                .settleTicks(45)
                .explicitGc(true)
                .build();

        String cmd = ReportService.buildCanonicalCommand(spec);
        assertTrue(cmd.contains("--seed=123456"));
        assertTrue(cmd.contains("--center=5,-10"));
        assertTrue(cmd.contains("--radius=8"));
        assertTrue(cmd.contains("--iterations=10"));
        assertTrue(cmd.contains("--batch=6"));
        assertTrue(cmd.contains("--strategy=spiral"));
        assertTrue(cmd.contains("--warmup=2"));
        assertTrue(cmd.contains("--explicit-gc=true"));
    }
}
