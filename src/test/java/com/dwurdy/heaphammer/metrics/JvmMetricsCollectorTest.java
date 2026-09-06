package com.dwurdy.heaphammer.metrics;

import com.dwurdy.heaphammer.detection.CleanupValidator;
import com.dwurdy.heaphammer.domain.Checkpoint;
import com.dwurdy.heaphammer.domain.CheckpointPhase;
import com.dwurdy.heaphammer.platform.MockPlatformAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JvmMetricsCollectorTest {

    @Test
    @DisplayName("JvmMetricsCollector returns valid positive heap metrics")
    void testJvmMetrics() {
        JvmMetricsCollector collector = new JvmMetricsCollector();
        assertTrue(collector.getHeapUsedBytes() > 0, "Heap used must be > 0");
        assertTrue(collector.getHeapCommittedBytes() > 0, "Heap committed must be > 0");
        assertTrue(collector.getNonHeapUsedBytes() > 0, "Non-heap used must be > 0");
        assertTrue(collector.getTotalGcCount() >= 0, "GC count must be >= 0");
    }

    @Test
    @DisplayName("CheckpointService captures baseline and cleanup checkpoints in order")
    void testCheckpointRecording() {
        MockPlatformAdapter mockPlatform = new MockPlatformAdapter();
        JvmMetricsCollector jvmMetrics = new JvmMetricsCollector();
        MinecraftMetricsCollector mcMetrics = new MinecraftMetricsCollector(mockPlatform);
        CleanupValidator validator = new CleanupValidator(mockPlatform);

        CheckpointService service = new CheckpointService(jvmMetrics, mcMetrics, validator);
        assertEquals(0, service.getCheckpoints().size());

        Checkpoint baseline = service.recordCheckpoint(CheckpointPhase.BASELINE, 0, "minecraft:overworld", false);
        assertEquals(CheckpointPhase.BASELINE, baseline.phase());
        assertEquals(0, baseline.iteration());
        assertTrue(baseline.cleanupValid());

        Checkpoint iter1 = service.recordCheckpoint(CheckpointPhase.ITERATION_CLEANUP, 1, "minecraft:overworld", false);
        assertEquals(CheckpointPhase.ITERATION_CLEANUP, iter1.phase());
        assertEquals(1, iter1.iteration());

        assertEquals(2, service.getCheckpoints().size());
    }
}
