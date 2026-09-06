package com.dwurdy.heaphammer.fixture;

import com.dwurdy.heaphammer.detection.CleanupValidator;
import com.dwurdy.heaphammer.detection.TrendAnalyzer;
import com.dwurdy.heaphammer.domain.*;
import com.dwurdy.heaphammer.metrics.CheckpointService;
import com.dwurdy.heaphammer.metrics.JvmMetricsCollector;
import com.dwurdy.heaphammer.metrics.MinecraftMetricsCollector;
import com.dwurdy.heaphammer.platform.MockPlatformAdapter;
import com.dwurdy.heaphammer.scenario.chunks.ChunkScenarioExecutor;
import com.dwurdy.heaphammer.scenario.chunks.ChunkWorkloadPlanner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FixtureDetectionIntegrationTest {

    private static final long BASELINE_HEAP = 100L * 1024 * 1024; // 100 MB baseline
    private MockPlatformAdapter mockPlatform;
    private CheckpointService checkpointService;
    private TrendAnalyzer trendAnalyzer;
    private ChunkWorkloadPlanner planner;

    @BeforeEach
    void setUp() {
        SyntheticLeakFixture.reset();
        mockPlatform = new MockPlatformAdapter();
        // Measure baseline heap plus the exact memory retained by the fixture
        JvmMetricsCollector jvmMetrics = new JvmMetricsCollector(() -> BASELINE_HEAP + SyntheticLeakFixture.getRetainedBytes());
        MinecraftMetricsCollector mcMetrics = new MinecraftMetricsCollector(mockPlatform);
        CleanupValidator validator = new CleanupValidator(mockPlatform);

        checkpointService = new CheckpointService(jvmMetrics, mcMetrics, validator);
        trendAnalyzer = new TrendAnalyzer(2.0 * 1024 * 1024, 0.70); // 2 MB/cycle threshold
        planner = new ChunkWorkloadPlanner();
    }

    @AfterEach
    void tearDown() {
        SyntheticLeakFixture.reset();
    }

    @Test
    @DisplayName("Hypothesis Validation 1: Synthetic leak fixture consistently produces SUSPICIOUS verdict")
    void testSyntheticLeakDetection() {
        SyntheticLeakFixture.setMode(SyntheticLeakFixture.Mode.LEAK);

        ExperimentSpec spec = ExperimentSpec.builder()
                .warmupIterations(1)
                .iterations(5)
                .batchSize(2)
                .holdTicks(1)
                .settleTicks(1)
                .maxOperationsPerTick(10)
                .build();

        ExperimentPlan plan = planner.plan(spec);

        ChunkScenarioExecutor executor = new ChunkScenarioExecutor(
                plan,
                mockPlatform.getChunkTicketManager(),
                (phase, iter) -> {
                    if (phase == CheckpointPhase.ITERATION_CLEANUP || phase == CheckpointPhase.WARMUP) {
                        SyntheticLeakFixture.onCycle(iter, 5); // 5 MB leak per cycle
                    }
                    checkpointService.recordCheckpoint(phase, iter, "minecraft:overworld", false);
                },
                state -> {}
        );

        int maxTicks = 100;
        int ticks = 0;
        while (!executor.getStateMachine().getState().isTerminal() && ticks++ < maxTicks) {
            executor.tick();
        }

        assertEquals(ExperimentState.COMPLETED, executor.getStateMachine().getState());
        List<Checkpoint> checkpoints = checkpointService.getCheckpoints();
        assertTrue(checkpoints.size() >= 5);

        DetectionResult detection = trendAnalyzer.analyze(spec, checkpoints);
        assertEquals(DetectionClassification.SUSPICIOUS, detection.classification());
        assertTrue(detection.confidence() >= 0.70);
        assertTrue(detection.slopeBytesPerCycle() > 0);
        assertTrue(SyntheticLeakFixture.getRetainedCount() > 0);
    }

    @Test
    @DisplayName("Hypothesis Validation 2: Clean execution produces PASS verdict")
    void testCleanDetection() {
        SyntheticLeakFixture.setMode(SyntheticLeakFixture.Mode.CLEAN);

        ExperimentSpec spec = ExperimentSpec.builder()
                .warmupIterations(1)
                .iterations(5)
                .batchSize(2)
                .holdTicks(1)
                .settleTicks(1)
                .build();

        ExperimentPlan plan = planner.plan(spec);

        ChunkScenarioExecutor executor = new ChunkScenarioExecutor(
                plan,
                mockPlatform.getChunkTicketManager(),
                (phase, iter) -> {
                    if (phase == CheckpointPhase.ITERATION_CLEANUP || phase == CheckpointPhase.WARMUP) {
                        SyntheticLeakFixture.onCycle(iter, 5);
                    }
                    checkpointService.recordCheckpoint(phase, iter, "minecraft:overworld", false);
                },
                state -> {}
        );

        int maxTicks = 100;
        int ticks = 0;
        while (!executor.getStateMachine().getState().isTerminal() && ticks++ < maxTicks) {
            executor.tick();
        }

        assertEquals(ExperimentState.COMPLETED, executor.getStateMachine().getState());
        DetectionResult detection = trendAnalyzer.analyze(spec, checkpointService.getCheckpoints());
        assertEquals(DetectionClassification.PASS, detection.classification());
        assertEquals(0, SyntheticLeakFixture.getRetainedCount());
    }

    @Test
    @DisplayName("Hypothesis Validation 3: Reset clears all synthetic leak retention")
    void testFixtureReset() {
        SyntheticLeakFixture.setMode(SyntheticLeakFixture.Mode.LEAK);
        SyntheticLeakFixture.onCycle(1, 10);
        SyntheticLeakFixture.onCycle(2, 10);
        assertEquals(2, SyntheticLeakFixture.getRetainedCount());

        SyntheticLeakFixture.reset();
        assertEquals(0, SyntheticLeakFixture.getRetainedCount());
        assertEquals(0, SyntheticLeakFixture.getRetainedBytes());
        assertEquals(SyntheticLeakFixture.Mode.OFF, SyntheticLeakFixture.getMode());
    }
}
