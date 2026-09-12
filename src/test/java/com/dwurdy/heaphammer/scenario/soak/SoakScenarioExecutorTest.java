package com.dwurdy.heaphammer.scenario.soak;

import com.dwurdy.heaphammer.application.ExperimentStateMachine;
import com.dwurdy.heaphammer.domain.CheckpointPhase;
import com.dwurdy.heaphammer.domain.ExperimentId;
import com.dwurdy.heaphammer.domain.ExperimentPlan;
import com.dwurdy.heaphammer.domain.ExperimentSpec;
import com.dwurdy.heaphammer.domain.ExperimentState;
import com.dwurdy.heaphammer.domain.ScenarioId;
import com.dwurdy.heaphammer.scenario.ScenarioExecutor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoakScenarioExecutorTest {
    @Test
    void repeatsBoundedCyclesUntilDurationAndEmitsOneFinalCheckpoint() {
        ExperimentSpec spec = ExperimentSpec.builder().scenarioId(ScenarioId.CHUNKS)
                .iterations(1).durationSeconds(1).intervalSeconds(1).build();
        ExperimentPlan plan = new ExperimentPlan(ExperimentId.generate(), System.currentTimeMillis(), spec,
                List.of(), 1, 0);
        List<CheckpointPhase> phases = new ArrayList<>();
        SoakScenarioExecutor executor = new SoakScenarioExecutor(plan,
                checkpoint -> new OneTickExecutor(plan, checkpoint),
                (phase, iteration) -> phases.add(phase),
                ignored -> {});
        executor.getStateMachine().transitionTo(ExperimentState.RUNNING, "test");
        for (int i = 0; i < 30 && !executor.getStateMachine().getState().isTerminal(); i++) executor.tick();

        assertEquals(ExperimentState.COMPLETED, executor.getStateMachine().getState());
        assertEquals(1, phases.stream().filter(phase -> phase == CheckpointPhase.BASELINE).count());
        assertEquals(1, phases.stream().filter(phase -> phase == CheckpointPhase.FINAL_CLEANUP).count());
        assertTrue(phases.stream().anyMatch(phase -> phase == CheckpointPhase.ITERATION_CLEANUP));
    }

    private static final class OneTickExecutor implements ScenarioExecutor {
        private final ExperimentPlan plan;
        private final ExperimentStateMachine state = new ExperimentStateMachine();
        private final BiConsumer<CheckpointPhase, Integer> checkpoint;
        private boolean ticked;

        private OneTickExecutor(ExperimentPlan plan, BiConsumer<CheckpointPhase, Integer> checkpoint) {
            this.plan = plan;
            this.checkpoint = checkpoint;
        }

        @Override public ExperimentStateMachine getStateMachine() { return state; }
        @Override public ExperimentPlan getPlan() { return plan; }
        @Override public int getCurrentIteration() { return ticked ? 1 : 0; }
        @Override public void tick() {
            if (ticked) return;
            checkpoint.accept(CheckpointPhase.BASELINE, 0);
            checkpoint.accept(CheckpointPhase.ITERATION_CLEANUP, 0);
            state.transitionTo(ExperimentState.CLEANING_UP, "cleanup");
            state.transitionTo(ExperimentState.SETTLING, "settle");
            state.transitionTo(ExperimentState.MEASURING, "measure");
            state.transitionTo(ExperimentState.COMPLETED, "done");
            ticked = true;
        }
        @Override public void stop(String reason) { state.transitionTo(ExperimentState.ABORTED, reason); }
    }
}
