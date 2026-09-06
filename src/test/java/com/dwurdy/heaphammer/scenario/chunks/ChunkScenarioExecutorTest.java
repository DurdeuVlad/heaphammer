package com.dwurdy.heaphammer.scenario.chunks;

import com.dwurdy.heaphammer.domain.*;
import com.dwurdy.heaphammer.platform.MockPlatformAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ChunkScenarioExecutorTest {

    @Test
    @DisplayName("ChunkScenarioExecutor executes complete plan tick-by-tick to completion")
    void testExecutorLifecycle() {
        MockPlatformAdapter mockPlatform = new MockPlatformAdapter();
        ExperimentSpec spec = ExperimentSpec.builder()
                .iterations(2)
                .batchSize(2)
                .holdTicks(2)
                .settleTicks(2)
                .maxOperationsPerTick(5)
                .build();

        ChunkWorkloadPlanner planner = new ChunkWorkloadPlanner();
        ExperimentPlan plan = planner.plan(spec);

        List<CheckpointPhase> capturedPhases = new ArrayList<>();
        AtomicReference<ExperimentState> finalState = new AtomicReference<>();

        ChunkScenarioExecutor executor = new ChunkScenarioExecutor(
                plan,
                mockPlatform.getChunkTicketManager(),
                (phase, iter) -> capturedPhases.add(phase),
                finalState::set
        );

        // Run ticks until completed
        int maxTicks = 200;
        int ticks = 0;
        while (!executor.getStateMachine().getState().isTerminal() && ticks++ < maxTicks) {
            executor.tick();
        }

        assertEquals(ExperimentState.COMPLETED, executor.getStateMachine().getState());
        assertEquals(ExperimentState.COMPLETED, finalState.get());
        assertEquals(0, mockPlatform.getChunkTicketManager().getActiveTicketCount(), "All tickets must be released");

        // Baseline + 2 iterations + final cleanup = 4 checkpoints
        assertTrue(capturedPhases.contains(CheckpointPhase.BASELINE));
        assertTrue(capturedPhases.contains(CheckpointPhase.FINAL_CLEANUP));
    }
}
