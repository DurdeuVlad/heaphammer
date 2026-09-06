package com.dwurdy.heaphammer.scenario.blockentities;

import com.dwurdy.heaphammer.domain.*;
import com.dwurdy.heaphammer.platform.MockPlatformAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class BlockEntityScenarioExecutorTest {

    @Test
    @DisplayName("BlockEntityScenarioExecutor runs tick-by-tick lifecycle to completion and clears all test block entities")
    void testBlockEntityExecutorLifecycle() {
        MockPlatformAdapter mockPlatform = new MockPlatformAdapter();
        ExperimentSpec spec = ExperimentSpec.builder()
                .scenarioId(ScenarioId.BLOCK_ENTITIES)
                .iterations(2)
                .batchSize(3)
                .holdTicks(2)
                .settleTicks(2)
                .maxOperationsPerTick(5)
                .build();

        BlockEntityScenarioPlanner planner = new BlockEntityScenarioPlanner();
        ExperimentPlan plan = planner.plan(spec, mockPlatform.getAvailableBlockEntityTypes());

        List<CheckpointPhase> capturedPhases = new ArrayList<>();
        AtomicReference<ExperimentState> finalState = new AtomicReference<>();

        BlockEntityScenarioExecutor executor = new BlockEntityScenarioExecutor(
                plan,
                mockPlatform,
                (phase, iter) -> capturedPhases.add(phase),
                finalState::set
        );

        int maxTicks = 200;
        int ticks = 0;
        while (!executor.getStateMachine().getState().isTerminal() && ticks++ < maxTicks) {
            executor.tick();
        }

        assertEquals(ExperimentState.COMPLETED, executor.getStateMachine().getState());
        assertEquals(ExperimentState.COMPLETED, finalState.get());
        assertEquals(0, mockPlatform.removeAllTestBlockEntities(spec.dimension()), "All test block entities must be purged upon completion");
        assertTrue(capturedPhases.contains(CheckpointPhase.BASELINE));
        assertTrue(capturedPhases.contains(CheckpointPhase.FINAL_CLEANUP));
    }
}
