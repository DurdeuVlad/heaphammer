package com.dwurdy.heaphammer.scenario.blockentities;

import com.dwurdy.heaphammer.application.ExecutionBudget;
import com.dwurdy.heaphammer.application.CrashRecoveryJournal;
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
 * Tick-driven executor for Block Entity Lifecycle experiments (Section 11.3, Issue #17).
 */
public class BlockEntityScenarioExecutor implements ScenarioExecutor {
    private final ExperimentPlan plan;
    private final PlatformAdapter adapter;
    private final ExperimentStateMachine stateMachine;
    private final ExecutionBudget budget;
    private final BiConsumer<CheckpointPhase, Integer> checkpointTrigger;
    private final Consumer<ExperimentState> completionCallback;
    private final CrashRecoveryJournal recoveryJournal;

    private int currentIteration = 0;
    private int currentOperationIndex = 0;
    private int holdTicksRemaining = 0;
    private int settleTicksRemaining = 0;
    private boolean waitingForHold = false;
    private boolean waitingForSettle = false;
    private boolean baselineRecorded = false;

    public BlockEntityScenarioExecutor(
            ExperimentPlan plan,
            PlatformAdapter adapter,
            BiConsumer<CheckpointPhase, Integer> checkpointTrigger,
            Consumer<ExperimentState> completionCallback
    ) {
        this(plan, adapter, checkpointTrigger, completionCallback, null);
    }

    public BlockEntityScenarioExecutor(
            ExperimentPlan plan,
            PlatformAdapter adapter,
            BiConsumer<CheckpointPhase, Integer> checkpointTrigger,
            Consumer<ExperimentState> completionCallback,
            CrashRecoveryJournal recoveryJournal
    ) {
        this.plan = Objects.requireNonNull(plan, "plan must not be null");
        this.adapter = Objects.requireNonNull(adapter, "adapter must not be null");
        this.checkpointTrigger = Objects.requireNonNull(checkpointTrigger, "checkpointTrigger must not be null");
        this.completionCallback = Objects.requireNonNull(completionCallback, "completionCallback must not be null");
        this.recoveryJournal = recoveryJournal;

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
            stateMachine.transitionTo(isWarmup ? ExperimentState.WARMING_UP : ExperimentState.RUNNING, "Starting block entity iteration " + currentIteration);
        }

        // 2. Handle hold delay countdown
        if (waitingForHold) {
            if (holdTicksRemaining > 0) {
                holdTicksRemaining--;
                return;
            } else {
                waitingForHold = false;
                stateMachine.transitionTo(ExperimentState.CLEANING_UP, "Removing block entities for iteration " + currentIteration);
            }
        }

        // 3. Handle settle delay countdown
        if (waitingForSettle) {
            if (settleTicksRemaining > 0) {
                settleTicksRemaining--;
                return;
            } else {
                waitingForSettle = false;
                stateMachine.transitionTo(ExperimentState.MEASURING, "Measuring block entity iteration " + currentIteration);

                boolean isWarmup = currentIteration < plan.spec().warmupIterations();
                checkpointTrigger.accept(isWarmup ? CheckpointPhase.WARMUP : CheckpointPhase.ITERATION_CLEANUP, currentIteration);

                currentIteration++;
                if (currentIteration >= plan.spec().iterations()) {
                    completeExperiment();
                    return;
                } else {
                    boolean nextIsWarmup = currentIteration < plan.spec().warmupIterations();
                    stateMachine.transitionTo(nextIsWarmup ? ExperimentState.WARMING_UP : ExperimentState.RUNNING, "Starting block entity iteration " + currentIteration);
                }
            }
        }

        // 4. Process operations for the current iteration
        List<ResolvedBlockEntityOperation> ops = plan.blockEntityOperations();
        while (currentOperationIndex < ops.size() && !budget.isExceeded()) {
            ResolvedBlockEntityOperation op = ops.get(currentOperationIndex);

            // Only process operations for the current iteration
            if (op.iteration() != currentIteration) {
                break;
            }

            if (ResolvedBlockEntityOperation.ACTION_PLACE.equals(op.action())) {
                if (adapter.placeBlockEntity(op.dimension(), op.blockEntityTypeId(), op.x(), op.y(), op.z())
                        && recoveryJournal != null) {
                    recoveryJournal.recordBlockPlaced(op.x(), op.y(), op.z());
                }
            } else if (ResolvedBlockEntityOperation.ACTION_REMOVE.equals(op.action())) {
                if (adapter.removeBlockEntity(op.dimension(), op.x(), op.y(), op.z())
                        && recoveryJournal != null) {
                    recoveryJournal.recordBlockRemoved(op.x(), op.y(), op.z());
                }
            }

            budget.recordOperation();
            currentOperationIndex++;

            // If we just finished the PLACE phase for this iteration, initiate HOLD
            boolean nextIsRemove = currentOperationIndex < ops.size() &&
                    ops.get(currentOperationIndex).iteration() == currentIteration &&
                    ResolvedBlockEntityOperation.ACTION_REMOVE.equals(ops.get(currentOperationIndex).action());

            if (nextIsRemove && !waitingForHold) {
                waitingForHold = true;
                holdTicksRemaining = plan.spec().holdTicks();
                stateMachine.transitionTo(ExperimentState.HOLDING, "Holding block entities for " + holdTicksRemaining + " ticks");
                return;
            }
        }

        // Check if all operations for this iteration have been consumed
        boolean iterationOpsFinished = currentOperationIndex >= ops.size() ||
                ops.get(currentOperationIndex).iteration() > currentIteration;

        if (iterationOpsFinished && !waitingForSettle && !waitingForHold) {
            waitingForSettle = true;
            settleTicksRemaining = plan.spec().settleTicks();
            stateMachine.transitionTo(ExperimentState.SETTLING, "Settling world after block entity cleanup for " + settleTicksRemaining + " ticks");
        }
    }

    @Override
    public synchronized void stop(String reason) {
        stateMachine.transitionTo(ExperimentState.STOPPING, "Stopping: " + reason);
        adapter.removeAllTestBlockEntities(plan.spec().dimension());
        stateMachine.transitionTo(ExperimentState.ABORTED, "Aborted: " + reason);
        completionCallback.accept(ExperimentState.ABORTED);
    }

    private void completeExperiment() {
        adapter.removeAllTestBlockEntities(plan.spec().dimension());
        checkpointTrigger.accept(CheckpointPhase.FINAL_CLEANUP, currentIteration);
        stateMachine.transitionTo(ExperimentState.COMPLETED, "Block entity experiment completed successfully");
        completionCallback.accept(ExperimentState.COMPLETED);
    }
}
