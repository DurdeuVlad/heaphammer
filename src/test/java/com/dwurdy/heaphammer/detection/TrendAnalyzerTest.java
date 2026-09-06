package com.dwurdy.heaphammer.detection;

import com.dwurdy.heaphammer.domain.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TrendAnalyzerTest {

    @Test
    @DisplayName("LinearRegression accurately computes slope, r-squared, standard error, and prediction")
    void testLinearRegression() {
        double[] x = {1, 2, 3, 4, 5};
        double[] y = {10, 20, 30, 40, 50}; // Perfect line slope 10, r^2 = 1.0

        LinearRegression reg = LinearRegression.compute(x, y);
        assertEquals(10.0, reg.slope(), 1e-6);
        assertEquals(0.0, reg.intercept(), 1e-6);
        assertEquals(1.0, reg.rSquared(), 1e-6);
        assertEquals(0.0, reg.standardError(), 1e-6);
        assertEquals(60.0, reg.predict(6), 1e-6);
    }

    @Test
    @DisplayName("TrendAnalyzer detects monotonic synthetic leak as SUSPICIOUS with high confidence")
    void testLeakingClassification() {
        ExperimentSpec spec = ExperimentSpec.builder().warmupIterations(1).iterations(5).build();
        List<Checkpoint> checkpoints = new ArrayList<>();

        long baseHeap = 100L * 1024 * 1024; // 100 MB
        checkpoints.add(createCheckpoint(CheckpointPhase.BASELINE, 0, baseHeap, true));

        // Leaking: +10 MB retained per cycle
        for (int i = 0; i <= 5; i++) {
            long heap = baseHeap + (i * 10L * 1024 * 1024);
            checkpoints.add(createCheckpoint(i == 0 ? CheckpointPhase.WARMUP : CheckpointPhase.ITERATION_CLEANUP, i, heap, true));
        }

        TrendAnalyzer analyzer = new TrendAnalyzer();
        DetectionResult result = analyzer.analyze(spec, checkpoints);

        assertEquals(DetectionClassification.SUSPICIOUS, result.classification());
        assertTrue(result.confidence() >= 0.90);
        assertTrue(result.rSquared() > 0.95);
        assertTrue(result.slopeBytesPerCycle() > 8 * 1024 * 1024);
        assertFalse(result.plateauDetected());
    }

    @Test
    @DisplayName("TrendAnalyzer classifies stable flat heap as PASS")
    void testStableClassification() {
        ExperimentSpec spec = ExperimentSpec.builder().warmupIterations(1).iterations(5).build();
        List<Checkpoint> checkpoints = new ArrayList<>();

        long baseHeap = 100L * 1024 * 1024;
        checkpoints.add(createCheckpoint(CheckpointPhase.BASELINE, 0, baseHeap, true));

        // Stable flat heap across cycles
        for (int i = 0; i <= 5; i++) {
            checkpoints.add(createCheckpoint(i == 0 ? CheckpointPhase.WARMUP : CheckpointPhase.ITERATION_CLEANUP, i, baseHeap, true));
        }

        TrendAnalyzer analyzer = new TrendAnalyzer();
        DetectionResult result = analyzer.analyze(spec, checkpoints);

        assertEquals(DetectionClassification.PASS, result.classification());
        assertFalse(result.plateauDetected());
    }

    @Test
    @DisplayName("TrendAnalyzer excludes warmup cycles and passes bounded cache")
    void testWarmupExclusion() {
        ExperimentSpec spec = ExperimentSpec.builder().warmupIterations(2).iterations(6).build();
        List<Checkpoint> checkpoints = new ArrayList<>();

        long baseHeap = 100L * 1024 * 1024;
        checkpoints.add(createCheckpoint(CheckpointPhase.BASELINE, 0, baseHeap, true));

        // Cycles 0 and 1 warm cache by +20 MB each
        checkpoints.add(createCheckpoint(CheckpointPhase.WARMUP, 0, baseHeap + 20 * 1024 * 1024, true));
        checkpoints.add(createCheckpoint(CheckpointPhase.WARMUP, 1, baseHeap + 40 * 1024 * 1024, true));

        // Cycles 2 through 6 are completely flat
        long warmedHeap = baseHeap + 40 * 1024 * 1024;
        for (int i = 2; i <= 6; i++) {
            checkpoints.add(createCheckpoint(CheckpointPhase.ITERATION_CLEANUP, i, warmedHeap, true));
        }

        TrendAnalyzer analyzer = new TrendAnalyzer();
        DetectionResult result = analyzer.analyze(spec, checkpoints);

        assertEquals(DetectionClassification.PASS, result.classification(), "Warm cache should not be classified as suspicious when warmup is excluded");
    }

    @Test
    @DisplayName("TrendAnalyzer detects bounded warming plateau even without warmup exclusion")
    void testPlateauDetection() {
        ExperimentSpec spec = ExperimentSpec.builder().warmupIterations(0).iterations(6).build();
        List<Checkpoint> checkpoints = new ArrayList<>();

        long baseHeap = 100L * 1024 * 1024;
        checkpoints.add(createCheckpoint(CheckpointPhase.BASELINE, 0, baseHeap, true));

        // Cycles 0, 1, 2 warm up steeply (+15 MB each)
        checkpoints.add(createCheckpoint(CheckpointPhase.ITERATION_CLEANUP, 0, baseHeap + 15 * 1024 * 1024, true));
        checkpoints.add(createCheckpoint(CheckpointPhase.ITERATION_CLEANUP, 1, baseHeap + 30 * 1024 * 1024, true));
        checkpoints.add(createCheckpoint(CheckpointPhase.ITERATION_CLEANUP, 2, baseHeap + 45 * 1024 * 1024, true));

        // Cycles 3, 4, 5 plateau and remain completely flat
        long plateauHeap = baseHeap + 45 * 1024 * 1024;
        checkpoints.add(createCheckpoint(CheckpointPhase.ITERATION_CLEANUP, 3, plateauHeap, true));
        checkpoints.add(createCheckpoint(CheckpointPhase.ITERATION_CLEANUP, 4, plateauHeap, true));
        checkpoints.add(createCheckpoint(CheckpointPhase.ITERATION_CLEANUP, 5, plateauHeap, true));

        TrendAnalyzer analyzer = new TrendAnalyzer();
        DetectionResult result = analyzer.analyze(spec, checkpoints);

        assertEquals(DetectionClassification.PASS, result.classification());
        assertTrue(result.plateauDetected(), "Plateau should be detected when early growth levels off");
        assertTrue(result.rationale().contains("PLATEAU"));
    }

    @Test
    @DisplayName("TrendAnalyzer flags CLEANUP_FAILED if cleanup was invalid")
    void testCleanupFailed() {
        ExperimentSpec spec = ExperimentSpec.builder().warmupIterations(0).iterations(3).build();
        List<Checkpoint> checkpoints = List.of(
                createCheckpoint(CheckpointPhase.BASELINE, 0, 100L * 1024 * 1024, true),
                createCheckpoint(CheckpointPhase.ITERATION_CLEANUP, 0, 100L * 1024 * 1024, true),
                createCheckpoint(CheckpointPhase.ITERATION_CLEANUP, 1, 100L * 1024 * 1024, false) // Failed cleanup!
        );

        TrendAnalyzer analyzer = new TrendAnalyzer();
        DetectionResult result = analyzer.analyze(spec, checkpoints);

        assertEquals(DetectionClassification.CLEANUP_FAILED, result.classification());
    }

    private Checkpoint createCheckpoint(CheckpointPhase phase, int iteration, long heapUsed, boolean cleanupValid) {
        MetricSample sample = new MetricSample(
                System.currentTimeMillis(), 0L, heapUsed, heapUsed * 2, heapUsed * 4,
                10L * 1024 * 1024, 100, 10, 1L, 10L
        );
        return new Checkpoint(phase, iteration, System.currentTimeMillis(), sample, cleanupValid);
    }
}
