package com.dwurdy.heaphammer.scenario.blockentities;

import com.dwurdy.heaphammer.domain.ExperimentId;
import com.dwurdy.heaphammer.domain.ExperimentPlan;
import com.dwurdy.heaphammer.domain.ExperimentSpec;
import com.dwurdy.heaphammer.domain.ScenarioId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BlockEntityScenarioPlannerTest {

    private static final List<String> TEST_BLOCK_ENTITY_TYPES = List.of(
            "minecraft:chest",
            "minecraft:furnace",
            "minecraft:hopper",
            "minecraft:barrel",
            "minecraft:dispenser"
    );

    @Test
    @DisplayName("Identical spec and seed produce bit-identical block entity operations sequence")
    void testDeterminismAcrossRuns() {
        ExperimentSpec spec = ExperimentSpec.builder()
                .scenarioId(ScenarioId.BLOCK_ENTITIES)
                .seed(12345678L)
                .center(50, -50)
                .iterations(4)
                .batchSize(6)
                .build();

        BlockEntityScenarioPlanner planner = new BlockEntityScenarioPlanner();
        ExperimentPlan plan1 = planner.plan(ExperimentId.of("be-1"), spec, TEST_BLOCK_ENTITY_TYPES);
        ExperimentPlan plan2 = planner.plan(ExperimentId.of("be-2"), spec, TEST_BLOCK_ENTITY_TYPES);

        assertEquals(plan1.blockEntityOperations().size(), plan2.blockEntityOperations().size());
        for (int i = 0; i < plan1.blockEntityOperations().size(); i++) {
            ResolvedBlockEntityOperation op1 = plan1.blockEntityOperations().get(i);
            ResolvedBlockEntityOperation op2 = plan2.blockEntityOperations().get(i);
            assertEquals(op1.blockEntityTypeId(), op2.blockEntityTypeId());
            assertEquals(op1.action(), op2.action());
            assertEquals(op1.x(), op2.x());
            assertEquals(op1.y(), op2.y());
            assertEquals(op1.z(), op2.z());
            assertEquals(op1.iteration(), op2.iteration());
        }
    }

    @Test
    @DisplayName("Different seeds produce distinct block entity sequences")
    void testDifferentSeedsProduceDifferentPlans() {
        ExperimentSpec spec1 = ExperimentSpec.builder()
                .scenarioId(ScenarioId.BLOCK_ENTITIES)
                .seed(101010L)
                .iterations(3)
                .batchSize(5)
                .build();

        ExperimentSpec spec2 = ExperimentSpec.builder()
                .scenarioId(ScenarioId.BLOCK_ENTITIES)
                .seed(202020L)
                .iterations(3)
                .batchSize(5)
                .build();

        BlockEntityScenarioPlanner planner = new BlockEntityScenarioPlanner();
        ExperimentPlan plan1 = planner.plan(spec1, TEST_BLOCK_ENTITY_TYPES);
        ExperimentPlan plan2 = planner.plan(spec2, TEST_BLOCK_ENTITY_TYPES);

        boolean foundDifference = false;
        for (int i = 0; i < plan1.blockEntityOperations().size(); i++) {
            ResolvedBlockEntityOperation op1 = plan1.blockEntityOperations().get(i);
            ResolvedBlockEntityOperation op2 = plan2.blockEntityOperations().get(i);
            if (!op1.blockEntityTypeId().equals(op2.blockEntityTypeId())) {
                foundDifference = true;
                break;
            }
        }
        assertTrue(foundDifference, "Different seeds must produce different block entity types");
    }

    @Test
    @DisplayName("Block entity operations pair PLACE with matching REMOVE at same coordinate")
    void testPairingPlaceAndRemove() {
        int centerX = 10;
        int centerZ = 20;
        int iterations = 2;
        int batchSize = 4;

        ExperimentSpec spec = ExperimentSpec.builder()
                .scenarioId(ScenarioId.BLOCK_ENTITIES)
                .center(centerX, centerZ)
                .iterations(iterations)
                .batchSize(batchSize)
                .build();

        BlockEntityScenarioPlanner planner = new BlockEntityScenarioPlanner();
        ExperimentPlan plan = planner.plan(spec, TEST_BLOCK_ENTITY_TYPES);

        assertEquals(iterations * batchSize * 2, plan.blockEntityOperations().size());
        assertEquals(plan.blockEntityOperations().size(), plan.totalOperations());

        for (int iter = 0; iter < iterations; iter++) {
            final int currentIter = iter;
            List<ResolvedBlockEntityOperation> iterOps = plan.blockEntityOperations().stream()
                    .filter(op -> op.iteration() == currentIter)
                    .toList();

            assertEquals(batchSize * 2, iterOps.size());
            List<ResolvedBlockEntityOperation> placeOps = iterOps.subList(0, batchSize);
            List<ResolvedBlockEntityOperation> removeOps = iterOps.subList(batchSize, batchSize * 2);

            for (int b = 0; b < batchSize; b++) {
                ResolvedBlockEntityOperation place = placeOps.get(b);
                ResolvedBlockEntityOperation remove = removeOps.get(b);

                assertEquals(ResolvedBlockEntityOperation.ACTION_PLACE, place.action());
                assertEquals(ResolvedBlockEntityOperation.ACTION_REMOVE, remove.action());
                assertEquals(place.x(), remove.x());
                assertEquals(place.y(), remove.y());
                assertEquals(place.z(), remove.z());
            }
        }
    }
}
