package com.dwurdy.heaphammer.scenario.entities;

import com.dwurdy.heaphammer.domain.ExperimentId;
import com.dwurdy.heaphammer.domain.ExperimentPlan;
import com.dwurdy.heaphammer.domain.ExperimentSpec;
import com.dwurdy.heaphammer.domain.ScenarioId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EntityScenarioPlannerTest {

    private static final List<String> TEST_ENTITY_TYPES = List.of(
            "minecraft:cow",
            "minecraft:pig",
            "minecraft:sheep",
            "minecraft:zombie",
            "minecraft:skeleton"
    );

    @Test
    @DisplayName("Identical spec and seed produce bit-identical entity operations sequence")
    void testDeterminismAcrossRuns() {
        ExperimentSpec spec = ExperimentSpec.builder()
                .scenarioId(ScenarioId.ENTITIES)
                .seed(42424242L)
                .center(10, -20)
                .radius(15)
                .iterations(5)
                .batchSize(4)
                .build();

        EntityScenarioPlanner planner = new EntityScenarioPlanner();
        ExperimentPlan plan1 = planner.plan(ExperimentId.of("ent-1"), spec, TEST_ENTITY_TYPES);
        ExperimentPlan plan2 = planner.plan(ExperimentId.of("ent-2"), spec, TEST_ENTITY_TYPES);

        assertEquals(plan1.entityOperations().size(), plan2.entityOperations().size());
        for (int i = 0; i < plan1.entityOperations().size(); i++) {
            ResolvedEntityOperation op1 = plan1.entityOperations().get(i);
            ResolvedEntityOperation op2 = plan2.entityOperations().get(i);
            assertEquals(op1.entityTypeId(), op2.entityTypeId());
            assertEquals(op1.action(), op2.action());
            assertEquals(op1.x(), op2.x(), 1e-6);
            assertEquals(op1.z(), op2.z(), 1e-6);
            assertEquals(op1.iteration(), op2.iteration());
            assertEquals(op1.removeMode(), op2.removeMode());
        }
    }

    @Test
    @DisplayName("Different seeds produce distinct entity operation sequences")
    void testDifferentSeedsProduceDifferentPlans() {
        ExperimentSpec spec1 = ExperimentSpec.builder()
                .scenarioId(ScenarioId.ENTITIES)
                .seed(11111L)
                .iterations(3)
                .batchSize(5)
                .build();

        ExperimentSpec spec2 = ExperimentSpec.builder()
                .scenarioId(ScenarioId.ENTITIES)
                .seed(99999L)
                .iterations(3)
                .batchSize(5)
                .build();

        EntityScenarioPlanner planner = new EntityScenarioPlanner();
        ExperimentPlan plan1 = planner.plan(spec1, TEST_ENTITY_TYPES);
        ExperimentPlan plan2 = planner.plan(spec2, TEST_ENTITY_TYPES);

        boolean foundDifference = false;
        for (int i = 0; i < plan1.entityOperations().size(); i++) {
            ResolvedEntityOperation op1 = plan1.entityOperations().get(i);
            ResolvedEntityOperation op2 = plan2.entityOperations().get(i);
            if (!op1.entityTypeId().equals(op2.entityTypeId()) || Math.abs(op1.x() - op2.x()) > 1e-4) {
                foundDifference = true;
                break;
            }
        }
        assertTrue(foundDifference, "Different seeds must produce different entity operations");
    }

    @Test
    @DisplayName("Operations have correct count, structure, and radius boundary")
    void testOperationCountAndStructure() {
        int centerX = 100;
        int centerZ = 200;
        int radius = 10;
        int iterations = 4;
        int batchSize = 6;

        ExperimentSpec spec = ExperimentSpec.builder()
                .scenarioId(ScenarioId.ENTITIES)
                .center(centerX, centerZ)
                .radius(radius)
                .iterations(iterations)
                .batchSize(batchSize)
                .strategy("KILL")
                .build();

        EntityScenarioPlanner planner = new EntityScenarioPlanner();
        ExperimentPlan plan = planner.plan(spec, TEST_ENTITY_TYPES);

        assertEquals(iterations * batchSize * 2, plan.entityOperations().size());
        assertEquals(plan.entityOperations().size(), plan.totalOperations());

        for (ResolvedEntityOperation op : plan.entityOperations()) {
            assertEquals(ResolvedEntityOperation.MODE_KILL, op.removeMode());
            if (ResolvedEntityOperation.ACTION_SPAWN.equals(op.action())) {
                double dist = Math.hypot(op.x() - centerX, op.z() - centerZ);
                assertTrue(dist <= radius + 0.001, "Spawn coord distance must be within radius");
            }
        }
    }
}
