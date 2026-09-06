package com.dwurdy.heaphammer.scenario.entities;

import com.dwurdy.heaphammer.application.ExecutionBudget;
import com.dwurdy.heaphammer.application.ExperimentStateMachine;
import com.dwurdy.heaphammer.domain.CheckpointPhase;
import com.dwurdy.heaphammer.domain.ExperimentPlan;
import com.dwurdy.heaphammer.domain.ExperimentSpec;
import com.dwurdy.heaphammer.domain.ExperimentState;
import com.dwurdy.heaphammer.platform.PlatformAdapter;
import com.dwurdy.heaphammer.scenario.ScenarioExecutor;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Tick-driven executor for Entity Churn experiments (Section 11.2, Issue #16).
 */
public class EntityScenarioExecutor implements ScenarioExecutor {
    private final ExperimentPlan plan;
    private final PlatformAdapter adapter;
    private final ExperimentStateMachine stateMachine;
    private final ExecutionBudget budget;
    private final BiConsumer<CheckpointPhase, Integer> checkpointTrigger;
    private final Consumer<ExperimentState> completionCallback;

    private final Queue<UUID> activeEntityUuids = new ArrayDeque<>();
    private int currentIteration = 0;
    private int currentOperationIndex = 0;
    private int holdTicksRemaining = 0;
    private int settleTicksRemaining = 0;
    private boolean waitingForHold = false;
    private boolean waitingForSettle = false;
    private boolean baselineRecorded = false;

    public EntityScenarioExecutor(
            ExperimentPlan plan,
            PlatformAdapter adapter,
            BiConsumer<CheckpointPhase, Integer> checkpointTrigger,
            Consumer<ExperimentState> completionCallback
    ) {
        this.plan = Objects.requireNonNull(plan, "plan must not be null");
        this.adapter = Objects.requireNonNull(adapter, "adapter must not be null");
        this.checkpointTrigger = Objects.requireNonNull(checkpointTrigger, "checkpointTrigger must not be null");
        this.completionCallback = Objects.requireNonNull(completionCallback, "completionCallback must not be null");

        this.stateMachine = new ExperimentStateMachine();
        ExperimentSpec spec = plan.spec();
        this.budget = new ExecutionBudget(spec.maxOperationsPerTick(), spec.maxMillisPerTick());
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
        return currentIteration;
    }

    @Override
    public synchronized void tick() {
        ExperimentState state = stateMachine.getState();
        if (state.isTerminal() || state == ExperimentState.PAUSED) {
            return;
        }

        budget.startTick();

        // 1. Record pre-workload baseline on very first tick
        if (!baselineRecorded) {
            checkpointTrigger.accept(CheckpointPhase.BASELINE, 0);
            baselineRecorded = true;
            boolean isWarmup = currentIteration < plan.spec().warmupIterations();
            stateMachine.transitionTo(isWarmup ? ExperimentState.WARMING_UP : ExperimentState.RUNNING, "Starting entity iteration " + currentIteration);
        }

        // 2. Handle hold delay countdown
        if (waitingForHold) {
            if (holdTicksRemaining > 0) {
                holdTicksRemaining--;
                return;
            } else {
                waitingForHold = false;
                stateMachine.transitionTo(ExperimentState.CLEANING_UP, "Removing entities for iteration " + currentIteration);
            }
        }

        // 3. Handle settle delay countdown
        if (waitingForSettle) {
            if (settleTicksRemaining > 0) {
                settleTicksRemaining--;
                return;
            } else {
                waitingForSettle = false;
                stateMachine.transitionTo(ExperimentState.MEASURING, "Measuring entity iteration " + currentIteration);

                boolean isWarmup = currentIteration < plan.spec().warmupIterations();
                checkpointTrigger.accept(isWarmup ? CheckpointPhase.WARMUP : CheckpointPhase.ITERATION_CLEANUP, currentIteration);

                currentIteration++;
                if (currentIteration >= plan.spec().iterations()) {
                    completeExperiment();
                    return;
                } else {
                    boolean nextIsWarmup = currentIteration < plan.spec().warmupIterations();
                    stateMachine.transitionTo(nextIsWarmup ? ExperimentState.WARMING_UP : ExperimentState.RUNNING, "Starting entity iteration " + currentIteration);
                }
            }
        }

        // 4. Process operations for the current iteration
        List<ResolvedEntityOperation> ops = plan.entityOperations();
        while (currentOperationIndex < ops.size() && !budget.isExceeded()) {
            ResolvedEntityOperation op = ops.get(currentOperationIndex);

            // Only process operations for the current iteration
            if (op.iteration() != currentIteration) {
                break;
            }

            if (ResolvedEntityOperation.ACTION_SPAWN.equals(op.action())) {
                UUID uuid = adapter.spawnEntity(op.dimension(), op.entityTypeId(), op.x(), op.y(), op.z());
                if (uuid != null) {
                    activeEntityUuids.add(uuid);
                }
            } else if (ResolvedEntityOperation.ACTION_REMOVE.equals(op.action())) {
                UUID uuid = activeEntityUuids.poll();
                if (uuid != null) {
                    adapter.removeEntity(op.dimension(), uuid, op.removeMode());
                }
            }

            budget.recordOperation();
            currentOperationIndex++;

            // If we just finished the SPAWN phase for this iteration, initiate HOLD
            boolean nextIsRemove = currentOperationIndex < ops.size() &&
                    ops.get(currentOperationIndex).iteration() == currentIteration &&
                    ResolvedEntityOperation.ACTION_REMOVE.equals(ops.get(currentOperationIndex).action());

            if (nextIsRemove && !waitingForHold) {
                waitingForHold = true;
                holdTicksRemaining = plan.spec().holdTicks();
                stateMachine.transitionTo(ExperimentState.HOLDING, "Holding entities for " + holdTicksRemaining + " ticks");
                return;
            }
        }

        // Check if all operations for this iteration have been consumed
        boolean iterationOpsFinished = currentOperationIndex >= ops.size() ||
                ops.get(currentOperationIndex).iteration() > currentIteration;

        if (iterationOpsFinished && !waitingForSettle && !waitingForHold) {
            waitingForSettle = true;
            settleTicksRemaining = plan.spec().settleTicks();
            stateMachine.transitionTo(ExperimentState.SETTLING, "Settling world after entity cleanup for " + settleTicksRemaining + " ticks");
        }
    }

    @Override
    public synchronized void stop(String reason) {
        stateMachine.transitionTo(ExperimentState.STOPPING, "Stopping: " + reason);
        activeEntityUuids.clear();
        adapter.removeAllTestEntities(plan.spec().dimension());
        stateMachine.transitionTo(ExperimentState.ABORTED, "Aborted: " + reason);
        completionCallback.accept(ExperimentState.ABORTED);
    }

    private void completeExperiment() {
        activeEntityUuids.clear();
        adapter.removeAllTestEntities(plan.spec().dimension());
        checkpointTrigger.accept(CheckpointPhase.FINAL_CLEANUP, currentIteration);
        stateMachine.transitionTo(ExperimentState.COMPLETED, "Entity experiment completed successfully");
        completionCallback.accept(ExperimentState.COMPLETED);
    }
}
