package com.dwurdy.heaphammer.scenario.chunks;

import com.dwurdy.heaphammer.domain.ExperimentId;
import com.dwurdy.heaphammer.domain.ExperimentPlan;
import com.dwurdy.heaphammer.domain.ExperimentSpec;
import com.dwurdy.heaphammer.domain.ResolvedChunkOperation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChunkWorkloadPlannerTest {

    @Test
    @DisplayName("Identical spec and seed produce bit-identical operations sequence")
    void testDeterminismAcrossRuns() {
        ExperimentSpec spec = ExperimentSpec.builder()
                .seed(987654321L)
                .strategy("RANDOM_WALK")
                .center(10, -20)
                .radius(5)
                .iterations(10)
                .batchSize(4)
                .build();

        ChunkWorkloadPlanner planner = new ChunkWorkloadPlanner();
        ExperimentPlan plan1 = planner.plan(ExperimentId.of("run-1"), spec);
        ExperimentPlan plan2 = planner.plan(ExperimentId.of("run-2"), spec);

        assertEquals(plan1.operations().size(), plan2.operations().size());
        for (int i = 0; i < plan1.operations().size(); i++) {
            ResolvedChunkOperation op1 = plan1.operations().get(i);
            ResolvedChunkOperation op2 = plan2.operations().get(i);
            assertEquals(op1.chunkX(), op2.chunkX());
            assertEquals(op1.chunkZ(), op2.chunkZ());
            assertEquals(op1.action(), op2.action());
            assertEquals(op1.iteration(), op2.iteration());
        }
    }

    @Test
    @DisplayName("All generated coordinates strictly fall within radius boundary")
    void testCoordinateBoundary() {
        int centerX = 50;
        int centerZ = -100;
        int radius = 8;

        for (String strategy : List.of("SPIRAL", "RING", "RANDOM_WALK", "HOTSPOT_CHURN", "GRID_SWEEP")) {
            ExperimentSpec spec = ExperimentSpec.builder()
                    .strategy(strategy)
                    .center(centerX, centerZ)
                    .radius(radius)
                    .iterations(5)
                    .batchSize(6)
                    .build();

            ChunkWorkloadPlanner planner = new ChunkWorkloadPlanner();
            ExperimentPlan plan = planner.plan(spec);

            assertFalse(plan.operations().isEmpty(), "Operations for " + strategy + " must not be empty");
            for (ResolvedChunkOperation op : plan.operations()) {
                int dx = Math.abs(op.chunkX() - centerX);
                int dz = Math.abs(op.chunkZ() - centerZ);
                assertTrue(dx <= radius, strategy + ": chunkX " + op.chunkX() + " out of radius " + radius);
                assertTrue(dz <= radius, strategy + ": chunkZ " + op.chunkZ() + " out of radius " + radius);
            }
        }
    }

    @Test
    @DisplayName("Different seeds produce distinct operation sequences in random walk")
    void testSeedVariation() {
        ExperimentSpec spec1 = ExperimentSpec.builder().seed(111L).strategy("RANDOM_WALK").build();
        ExperimentSpec spec2 = ExperimentSpec.builder().seed(999L).strategy("RANDOM_WALK").build();

        ChunkWorkloadPlanner planner = new ChunkWorkloadPlanner();
        ExperimentPlan plan1 = planner.plan(spec1);
        ExperimentPlan plan2 = planner.plan(spec2);

        boolean foundDifference = false;
        for (int i = 0; i < Math.min(plan1.operations().size(), plan2.operations().size()); i++) {
            if (plan1.operations().get(i).chunkX() != plan2.operations().get(i).chunkX() ||
                plan1.operations().get(i).chunkZ() != plan2.operations().get(i).chunkZ()) {
                foundDifference = true;
                break;
            }
        }
        assertTrue(foundDifference, "Different seeds should produce different coordinates");
    }
}
