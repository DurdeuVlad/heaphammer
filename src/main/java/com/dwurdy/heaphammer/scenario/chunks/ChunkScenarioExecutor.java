package com.dwurdy.heaphammer.scenario.chunks;

import com.dwurdy.heaphammer.application.ExecutionBudget;
import com.dwurdy.heaphammer.application.ExperimentStateMachine;
import com.dwurdy.heaphammer.domain.CheckpointPhase;
import com.dwurdy.heaphammer.domain.ExperimentPlan;
import com.dwurdy.heaphammer.domain.ExperimentSpec;
import com.dwurdy.heaphammer.domain.ExperimentState;
import com.dwurdy.heaphammer.domain.ResolvedChunkOperation;
import com.dwurdy.heaphammer.platform.ChunkTicketManager;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Tick-driven executor for Chunk Churn experiments (Section 18.2).
 */
public class ChunkScenarioExecutor {
    private final ExperimentPlan plan;
    private final ChunkTicketManager ticketManager;
    private final ExperimentStateMachine stateMachine;
    private final ExecutionBudget budget;
    private final BiConsumer<CheckpointPhase, Integer> checkpointTrigger;
    private final Consumer<ExperimentState> completionCallback;

    private int currentIteration = 0;
    private int currentOperationIndex = 0;
    private int holdTicksRemaining = 0;
    private int settleTicksRemaining = 0;
    private boolean waitingForHold = false;
    private boolean waitingForSettle = false;
    private boolean baselineRecorded = false;

    public ChunkScenarioExecutor(
            ExperimentPlan plan,
            ChunkTicketManager ticketManager,
            BiConsumer<CheckpointPhase, Integer> checkpointTrigger,
            Consumer<ExperimentState> completionCallback
    ) {
        this.plan = Objects.requireNonNull(plan, "plan must not be null");
        this.ticketManager = Objects.requireNonNull(ticketManager, "ticketManager must not be null");
        this.checkpointTrigger = Objects.requireNonNull(checkpointTrigger, "checkpointTrigger must not be null");
        this.completionCallback = Objects.requireNonNull(completionCallback, "completionCallback must not be null");

        this.stateMachine = new ExperimentStateMachine();
        ExperimentSpec spec = plan.spec();
        this.budget = new ExecutionBudget(spec.maxOperationsPerTick(), spec.maxMillisPerTick());
    }

    public ExperimentStateMachine getStateMachine() {
        return stateMachine;
    }

    public ExperimentPlan getPlan() {
        return plan;
    }

    public int getCurrentIteration() {
        return currentIteration;
    }

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
            stateMachine.transitionTo(isWarmup ? ExperimentState.WARMING_UP : ExperimentState.RUNNING, "Starting iteration " + currentIteration);
        }

        // 2. Handle hold delay countdown
        if (waitingForHold) {
            if (holdTicksRemaining > 0) {
                holdTicksRemaining--;
                return;
            } else {
                waitingForHold = false;
                stateMachine.transitionTo(ExperimentState.CLEANING_UP, "Releasing chunks for iteration " + currentIteration);
            }
        }

        // 3. Handle settle delay countdown
        if (waitingForSettle) {
            if (settleTicksRemaining > 0) {
                settleTicksRemaining--;
                return;
            } else {
                waitingForSettle = false;
                stateMachine.transitionTo(ExperimentState.MEASURING, "Measuring iteration " + currentIteration);

                boolean isWarmup = currentIteration < plan.spec().warmupIterations();
                checkpointTrigger.accept(isWarmup ? CheckpointPhase.WARMUP : CheckpointPhase.ITERATION_CLEANUP, currentIteration);

                currentIteration++;
                if (currentIteration >= plan.spec().iterations()) {
                    completeExperiment();
                    return;
                } else {
                    boolean nextIsWarmup = currentIteration < plan.spec().warmupIterations();
                    stateMachine.transitionTo(nextIsWarmup ? ExperimentState.WARMING_UP : ExperimentState.RUNNING, "Starting iteration " + currentIteration);
                }
            }
        }

        // 4. Process operations for the current iteration
        List<ResolvedChunkOperation> ops = plan.operations();
        while (currentOperationIndex < ops.size() && !budget.isExceeded()) {
            ResolvedChunkOperation op = ops.get(currentOperationIndex);

            // Only process operations for the current iteration
            if (op.iteration() != currentIteration) {
                break;
            }

            if (ResolvedChunkOperation.ACTION_ACQUIRE.equals(op.action())) {
                ticketManager.acquireTicket(op.dimension(), op.chunkX(), op.chunkZ());
            } else if (ResolvedChunkOperation.ACTION_RELEASE.equals(op.action())) {
                ticketManager.releaseTicket(op.dimension(), op.chunkX(), op.chunkZ());
            }

            budget.recordOperation();
            currentOperationIndex++;

            // If we just finished the ACQUIRE phase for this iteration, initiate HOLD
            boolean nextIsRelease = currentOperationIndex < ops.size() &&
                    ops.get(currentOperationIndex).iteration() == currentIteration &&
                    ResolvedChunkOperation.ACTION_RELEASE.equals(ops.get(currentOperationIndex).action());

            if (nextIsRelease && !waitingForHold) {
                waitingForHold = true;
                holdTicksRemaining = plan.spec().holdTicks();
                stateMachine.transitionTo(ExperimentState.HOLDING, "Holding chunks for " + holdTicksRemaining + " ticks");
                return;
            }
        }

        // Check if all operations for this iteration have been consumed (including releases)
        boolean iterationOpsFinished = currentOperationIndex >= ops.size() ||
                ops.get(currentOperationIndex).iteration() > currentIteration;

        if (iterationOpsFinished && !waitingForSettle && !waitingForHold) {
            waitingForSettle = true;
            settleTicksRemaining = plan.spec().settleTicks();
            stateMachine.transitionTo(ExperimentState.SETTLING, "Settling world for " + settleTicksRemaining + " ticks");
        }
    }

    public synchronized void stop(String reason) {
        stateMachine.transitionTo(ExperimentState.STOPPING, "Stopping: " + reason);
        ticketManager.releaseAllTickets();
        stateMachine.transitionTo(ExperimentState.ABORTED, "Aborted: " + reason);
        completionCallback.accept(ExperimentState.ABORTED);
    }

    private void completeExperiment() {
        ticketManager.releaseAllTickets();
        checkpointTrigger.accept(CheckpointPhase.FINAL_CLEANUP, currentIteration);
        stateMachine.transitionTo(ExperimentState.COMPLETED, "Experiment completed successfully");
        completionCallback.accept(ExperimentState.COMPLETED);
    }
}
