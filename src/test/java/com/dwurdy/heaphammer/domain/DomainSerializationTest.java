package com.dwurdy.heaphammer.domain;

import com.dwurdy.heaphammer.infrastructure.json.GsonCodec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DomainSerializationTest {

    @Test
    @DisplayName("ExperimentSpec serializes to JSON and deserializes identically")
    void testSpecSerialization() {
        ExperimentSpec spec = ExperimentSpec.builder()
                .seed(123456789L)
                .radius(10)
                .iterations(8)
                .batchSize(12)
                .strategy("SPIRAL")
                .warmupIterations(2)
                .holdTicks(30)
                .settleTicks(50)
                .explicitGc(true)
                .build();

        String json = GsonCodec.toJson(spec);
        assertNotNull(json);

        ExperimentSpec restored = GsonCodec.fromJson(json, ExperimentSpec.class);
        assertEquals(spec.seed(), restored.seed());
        assertEquals(spec.radius(), restored.radius());
        assertEquals(spec.iterations(), restored.iterations());
        assertEquals(spec.batchSize(), restored.batchSize());
        assertEquals(spec.strategy(), restored.strategy());
        assertEquals(spec.warmupIterations(), restored.warmupIterations());
        assertEquals(spec.holdTicks(), restored.holdTicks());
        assertEquals(spec.settleTicks(), restored.settleTicks());
        assertTrue(restored.explicitGc());
    }

    @Test
    @DisplayName("ExperimentPlan serializes with resolved operations and roundtrips")
    void testPlanSerialization() {
        ExperimentSpec spec = ExperimentSpec.builder().build();
        List<ResolvedChunkOperation> ops = List.of(
                new ResolvedChunkOperation(0, 0, "minecraft:overworld", 0, 0, ResolvedChunkOperation.ACTION_ACQUIRE),
                new ResolvedChunkOperation(0, 1, "minecraft:overworld", 0, 1, ResolvedChunkOperation.ACTION_ACQUIRE),
                new ResolvedChunkOperation(0, 2, "minecraft:overworld", 0, 0, ResolvedChunkOperation.ACTION_RELEASE),
                new ResolvedChunkOperation(0, 3, "minecraft:overworld", 0, 1, ResolvedChunkOperation.ACTION_RELEASE)
        );

        ExperimentPlan plan = new ExperimentPlan(
                ExperimentId.of("hh-test-1234"),
                System.currentTimeMillis(),
                spec,
                ops,
                100,
                2
        );

        String json = GsonCodec.toJson(plan);
        ExperimentPlan restored = GsonCodec.fromJson(json, ExperimentPlan.class);

        assertEquals(plan.id(), restored.id());
        assertEquals(4, restored.totalOperations());
        assertEquals(0, restored.operations().get(0).chunkX());
        assertEquals(1, restored.operations().get(1).chunkZ());
    }

    @Test
    @DisplayName("ExperimentReport serializes complete run with checkpoints and detection")
    void testReportSerialization() {
        ExperimentSpec spec = ExperimentSpec.builder().build();
        EnvironmentFingerprint env = new EnvironmentFingerprint(
                "1.0.0", "1.21.1", "0.19.5", "21.0.8", 99999L, "hash123",
                Map.of("fabricloader", "0.19.5", "heaphammer", "1.0.0")
        );

        MetricSample sample = new MetricSample(
                System.currentTimeMillis(), 100L, 500_000_000L, 1_000_000_000L, 2_000_000_000L,
                50_000_000L, 150, 25, 3L, 45L
        );

        Checkpoint cp = new Checkpoint(CheckpointPhase.BASELINE, 0, System.currentTimeMillis(), sample, true);

        DetectionResult detection = new DetectionResult(
                DetectionClassification.PASS, 0.95, 1024.0, 0.12, 5000L, "Normal baseline"
        );

        ExperimentReport report = new ExperimentReport(
                ExperimentId.of("hh-report-1"),
                1000L, 2000L, "COMPLETED", spec, env, List.of(cp), detection,
                "/hh run chunks --seed=42", List.of("Warning: test world recommended")
        );

        String json = GsonCodec.toJson(report);
        ExperimentReport restored = GsonCodec.fromJson(json, ExperimentReport.class);

        assertEquals("hh-report-1", restored.runId().value());
        assertEquals("COMPLETED", restored.status());
        assertEquals(DetectionClassification.PASS, restored.detection().classification());
        assertEquals(1, restored.checkpoints().size());
        assertEquals(1, restored.warnings().size());
    }
}
