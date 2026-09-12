package com.dwurdy.heaphammer.scenario.soak;

import com.dwurdy.heaphammer.application.ExperimentStateMachine;
import com.dwurdy.heaphammer.domain.CheckpointPhase;
import com.dwurdy.heaphammer.domain.ExperimentPlan;
import com.dwurdy.heaphammer.domain.ExperimentState;
import com.dwurdy.heaphammer.domain.SoakSchedule;
import com.dwurdy.heaphammer.scenario.ScenarioExecutor;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/** Repeats a bounded deterministic workload on an explicit duration/interval schedule. */
public final class SoakScenarioExecutor implements ScenarioExecutor {
    private final ExperimentPlan plan;
    private final Function<BiConsumer<CheckpointPhase, Integer>, ScenarioExecutor> cycleFactory;
    private final BiConsumer<CheckpointPhase, Integer> checkpointTrigger;
    private final Consumer<ExperimentState> completionCallback;
    private final ExperimentStateMachine stateMachine = new ExperimentStateMachine();
    private final long durationTicks;
    private final long intervalTicks;

    private ScenarioExecutor currentCycle;
    private long tick;
    private long nextCycleTick;
    private int checkpointIndex;
    private boolean baselineForwarded;
    private boolean waiting;
    private boolean completionSent;

    public SoakScenarioExecutor(
            ExperimentPlan plan,
            Function<BiConsumer<CheckpointPhase, Integer>, ScenarioExecutor> cycleFactory,
            BiConsumer<CheckpointPhase, Integer> checkpointTrigger,
            Consumer<ExperimentState> completionCallback
    ) {
        this.plan = Objects.requireNonNull(plan, "plan must not be null");
        this.cycleFactory = Objects.requireNonNull(cycleFactory, "cycleFactory must not be null");
        this.checkpointTrigger = Objects.requireNonNull(checkpointTrigger, "checkpointTrigger must not be null");
        this.completionCallback = Objects.requireNonNull(completionCallback, "completionCallback must not be null");
        SoakSchedule schedule = new SoakSchedule(plan.spec().durationSeconds(), plan.spec().intervalSeconds());
        this.durationTicks = safeTicks(schedule.durationSeconds());
        this.intervalTicks = Math.max(1L, safeTicks(schedule.intervalSeconds()));
        this.currentCycle = cycleFactory.apply(this::forwardCheckpoint);
    }

    @Override
    public ExperimentStateMachine getStateMachine() {
        return stateMachine;
    }

    @Override
    public ExperimentPlan getPlan() {
        return plan;
    }

    @Override
    public int getCurrentIteration() {
        return checkpointIndex;
    }

    @Override
    public synchronized void tick() {
        if (stateMachine.getState().isTerminal() || stateMachine.getState() == ExperimentState.PAUSED) return;
        tick++;
        if (waiting) {
            if (tick < nextCycleTick) return;
            waiting = false;
            stateMachine.transitionTo(ExperimentState.MEASURING, "Starting next soak interval");
            stateMachine.transitionTo(ExperimentState.RUNNING, "Running soak interval " + checkpointIndex);
            currentCycle = cycleFactory.apply(this::forwardCheckpoint);
        }

        ExperimentState innerState = currentCycle.getStateMachine().getState();
        if (!innerState.isActive()) {
            ExperimentState target = stateMachine.getState() == ExperimentState.WARMING_UP
                    ? ExperimentState.WARMING_UP : ExperimentState.RUNNING;
            currentCycle.getStateMachine().transitionTo(target, "Starting bounded soak cycle");
        }
        currentCycle.tick();

        if (currentCycle.getStateMachine().getState().isTerminal()) {
            if (tick >= durationTicks) {
                finish();
            } else {
                stateMachine.transitionTo(ExperimentState.RUNNING, "Completed bounded soak cycle");
                stateMachine.transitionTo(ExperimentState.CLEANING_UP, "Waiting for next soak interval");
                stateMachine.transitionTo(ExperimentState.SETTLING, "Waiting for next soak interval");
                nextCycleTick = Math.min(durationTicks, tick + intervalTicks);
                waiting = true;
            }
        }
    }

    private void forwardCheckpoint(CheckpointPhase phase, int ignoredIteration) {
        if (phase == CheckpointPhase.BASELINE) {
            if (!baselineForwarded) {
                baselineForwarded = true;
                checkpointTrigger.accept(CheckpointPhase.BASELINE, 0);
            }
        } else if (phase == CheckpointPhase.WARMUP || phase == CheckpointPhase.ITERATION_CLEANUP) {
            checkpointTrigger.accept(phase, checkpointIndex++);
        }
        // Bounded cycle FINAL_CLEANUP is intentionally suppressed; the soak wrapper
        // emits one final cleanup checkpoint when the duration expires.
    }

    private void finish() {
        if (completionSent) return;
        checkpointTrigger.accept(CheckpointPhase.FINAL_CLEANUP, checkpointIndex);
        stateMachine.transitionTo(ExperimentState.CLEANING_UP, "Final soak cleanup");
        stateMachine.transitionTo(ExperimentState.SETTLING, "Final soak settle");
        stateMachine.transitionTo(ExperimentState.MEASURING, "Final soak measurement");
        stateMachine.transitionTo(ExperimentState.COMPLETED, "Soak duration completed");
        completionSent = true;
        completionCallback.accept(ExperimentState.COMPLETED);
    }

    @Override
    public synchronized void stop(String reason) {
        if (completionSent) return;
        currentCycle.stop(reason);
        stateMachine.transitionTo(ExperimentState.STOPPING, "Stopping soak: " + reason);
        stateMachine.transitionTo(ExperimentState.ABORTED, "Soak aborted: " + reason);
        completionSent = true;
        completionCallback.accept(ExperimentState.ABORTED);
    }

    private static long safeTicks(long seconds) {
        return seconds > Long.MAX_VALUE / 20L ? Long.MAX_VALUE : seconds * 20L;
    }
}
