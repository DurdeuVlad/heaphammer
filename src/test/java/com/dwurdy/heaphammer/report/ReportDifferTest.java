package com.dwurdy.heaphammer.report;

import com.dwurdy.heaphammer.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReportDifferTest {

    @Test
    @DisplayName("ReportDiffer calculates differential slopes and heap deltas")
    void testIdenticalEnvironmentsDifferentSlopes() {
        EnvironmentFingerprint env = new EnvironmentFingerprint("0.1.0", "1.21.1", "0.16.0", "21.0.3", 1234L, "fp", Map.of("mod1", "1.0"));
        ExperimentSpec spec = ExperimentSpec.builder().scenarioId(ScenarioId.of("chunk-churn")).iterations(10).build();

        // Report A: Leaking (+10MB/cycle)
        MetricSample baseA = new MetricSample(1000L, 0L, 100L * 1024 * 1024, 200L * 1024 * 1024, 400L * 1024 * 1024, 10L, 100, 10, 1L, 10L);
        MetricSample finalA = new MetricSample(2000L, 0L, 200L * 1024 * 1024, 200L * 1024 * 1024, 400L * 1024 * 1024, 10L, 100, 10, 1L, 10L);
        List<Checkpoint> cpsA = List.of(
                new Checkpoint(CheckpointPhase.BASELINE, 0, 1000L, baseA, true),
                new Checkpoint(CheckpointPhase.FINAL_CLEANUP, 10, 2000L, finalA, true)
        );
        DetectionResult detA = new DetectionResult(DetectionClassification.SUSPICIOUS, 0.95, 10.0 * 1024 * 1024, 0.98, 100L * 1024 * 1024, false, "Leak");
        ExperimentReport reportA = new ExperimentReport(ExperimentId.generate(), 1000L, 2000L, "COMPLETED", spec, env, cpsA, detA, "/hh run", List.of());

        // Report B: Stable / Fixed (+0MB/cycle)
        MetricSample baseB = new MetricSample(3000L, 0L, 100L * 1024 * 1024, 200L * 1024 * 1024, 400L * 1024 * 1024, 10L, 100, 10, 1L, 10L);
        MetricSample finalB = new MetricSample(4000L, 0L, 102L * 1024 * 1024, 200L * 1024 * 1024, 400L * 1024 * 1024, 10L, 100, 10, 1L, 10L);
        List<Checkpoint> cpsB = List.of(
                new Checkpoint(CheckpointPhase.BASELINE, 0, 3000L, baseB, true),
                new Checkpoint(CheckpointPhase.FINAL_CLEANUP, 10, 4000L, finalB, true)
        );
        DetectionResult detB = new DetectionResult(DetectionClassification.PASS, 0.90, 0.2 * 1024 * 1024, 0.10, 2L * 1024 * 1024, false, "Pass");
        ExperimentReport reportB = new ExperimentReport(ExperimentId.generate(), 3000L, 4000L, "COMPLETED", spec, env, cpsB, detB, "/hh run", List.of());

        ReportDiff diff = ReportDiffer.diff(reportA, reportB);

        assertNotNull(diff);
        assertTrue(diff.environmentMatched());
        assertEquals(DetectionClassification.SUSPICIOUS, diff.classificationA());
        assertEquals(DetectionClassification.PASS, diff.classificationB());
        assertTrue(diff.classificationChanged());

        // Net delta in A: +100MB, in B: +2MB -> difference is -98MB
        assertEquals(-98L * 1024 * 1024, diff.netDeltaDifferenceBytes());
        // Slope in A: 10MB, in B: 0.2MB -> slope diff is -9.8MB/cycle
        assertEquals(-9.8 * 1024 * 1024, diff.slopeDifferenceBytes(), 1e-4);
        assertTrue(diff.summary().contains("PASS"));
    }

    @Test
    @DisplayName("ReportDiffer flags environment mismatches and issues warnings")
    void testEnvironmentMismatch() {
        EnvironmentFingerprint envA = new EnvironmentFingerprint("0.1.0", "1.21.1", "0.16.0", "21.0.3", 1234L, "fpA", Map.of("mod1", "1.0", "mod2", "1.0"));
        EnvironmentFingerprint envB = new EnvironmentFingerprint("0.1.0", "1.21.2", "0.16.0", "21.0.3", 1234L, "fpB", Map.of("mod1", "1.0"));

        ExperimentSpec specA = ExperimentSpec.builder().scenarioId(ScenarioId.of("chunk-churn")).iterations(5).build();
        ExperimentSpec specB = ExperimentSpec.builder().scenarioId(ScenarioId.of("chunk-churn")).iterations(10).build();

        DetectionResult det = new DetectionResult(DetectionClassification.PASS, 0.9, 0.0, 0.0, 0L, "Pass");

        ExperimentReport reportA = new ExperimentReport(ExperimentId.generate(), 1000L, 2000L, "COMPLETED", specA, envA, List.of(), det, "/hh run", List.of());
        ExperimentReport reportB = new ExperimentReport(ExperimentId.generate(), 3000L, 4000L, "COMPLETED", specB, envB, List.of(), det, "/hh run", List.of());

        ReportDiff diff = ReportDiffer.diff(reportA, reportB);

        assertFalse(diff.environmentMatched());
        assertTrue(diff.warnings().stream().anyMatch(w -> w.contains("Environment mismatch")));
        assertTrue(diff.warnings().stream().anyMatch(w -> w.contains("Iteration counts differ")));
    }
}
