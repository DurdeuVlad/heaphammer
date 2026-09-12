package com.dwurdy.heaphammer.scenario.players;

import com.dwurdy.heaphammer.application.ExecutionBudget;
import com.dwurdy.heaphammer.application.CrashRecoveryJournal;
import com.dwurdy.heaphammer.application.ExperimentStateMachine;
import com.dwurdy.heaphammer.domain.CheckpointPhase;
import com.dwurdy.heaphammer.domain.ExperimentPlan;
import com.dwurdy.heaphammer.domain.ExperimentState;
import com.dwurdy.heaphammer.domain.PlayerAction;
import com.dwurdy.heaphammer.platform.PlayerLifecyclePort;
import com.dwurdy.heaphammer.scenario.ScenarioExecutor;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Tick-driven executor for authentic player login/logout lifecycle workloads. */
public final class PlayerScenarioExecutor implements ScenarioExecutor {
    private final ExperimentPlan plan;
    private final PlayerLifecyclePort port;
    private final ExperimentStateMachine stateMachine;
    private final ExecutionBudget budget;
    private final BiConsumer<CheckpointPhase, Integer> checkpointTrigger;
    private final Consumer<ExperimentState> completionCallback;
    private final Set<UUID> activePlayerIds = new HashSet<>();
    private final CrashRecoveryJournal recoveryJournal;

    private int currentIteration;
    private int currentOperationIndex;
    private int settleTicksRemaining;
    private boolean waitingForSettle;
    private boolean baselineRecorded;

    public PlayerScenarioExecutor(
            ExperimentPlan plan,
            PlayerLifecyclePort port,
            BiConsumer<CheckpointPhase, Integer> checkpointTrigger,
            Consumer<ExperimentState> completionCallback
    ) {
        this(plan, port, checkpointTrigger, completionCallback, null);
    }

    public PlayerScenarioExecutor(
            ExperimentPlan plan,
            PlayerLifecyclePort port,
            BiConsumer<CheckpointPhase, Integer> checkpointTrigger,
            Consumer<ExperimentState> completionCallback,
            CrashRecoveryJournal recoveryJournal
    ) {
        this.plan = Objects.requireNonNull(plan, "plan must not be null");
        this.port = Objects.requireNonNull(port, "port must not be null");
        this.checkpointTrigger = Objects.requireNonNull(checkpointTrigger, "checkpointTrigger must not be null");
        this.completionCallback = Objects.requireNonNull(completionCallback, "completionCallback must not be null");
        this.recoveryJournal = recoveryJournal;
        this.stateMachine = new ExperimentStateMachine();
        this.budget = new ExecutionBudget(plan.spec().maxOperationsPerTick(), plan.spec().maxMillisPerTick());
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
        if (state.isTerminal() || state == ExperimentState.PAUSED) return;

        budget.startTick();
        if (!baselineRecorded) {
            checkpointTrigger.accept(CheckpointPhase.BASELINE, 0);
            baselineRecorded = true;
            stateMachine.transitionTo(ExperimentState.RUNNING, "Starting player iteration " + currentIteration);
        }

        if (waitingForSettle) {
            if (settleTicksRemaining > 0) {
                settleTicksRemaining--;
                return;
            }
            waitingForSettle = false;
            stateMachine.transitionTo(ExperimentState.MEASURING, "Measuring player iteration " + currentIteration);
            boolean warmup = currentIteration < plan.spec().warmupIterations();
            checkpointTrigger.accept(warmup ? CheckpointPhase.WARMUP : CheckpointPhase.ITERATION_CLEANUP, currentIteration);
            currentIteration++;
            if (currentIteration >= plan.spec().iterations()) {
                completeExperiment();
            } else {
                stateMachine.transitionTo(ExperimentState.RUNNING, "Starting player iteration " + currentIteration);
            }
            return;
        }

        List<ResolvedPlayerOperation> operations = plan.playerOperations();
        while (currentOperationIndex < operations.size() && !budget.isExceeded()) {
            ResolvedPlayerOperation operation = operations.get(currentOperationIndex);
            if (operation.iteration() != currentIteration) break;
            execute(operation);
            currentOperationIndex++;
            budget.recordOperation();
        }

        boolean iterationFinished = currentOperationIndex >= operations.size()
                || operations.get(currentOperationIndex).iteration() > currentIteration;
        if (iterationFinished && !waitingForSettle) {
            waitingForSettle = true;
            settleTicksRemaining = plan.spec().settleTicks();
            stateMachine.transitionTo(ExperimentState.CLEANING_UP,
                    "Cleaning up player lifecycle after iteration " + currentIteration);
            stateMachine.transitionTo(ExperimentState.SETTLING,
                    "Settling player lifecycle after iteration " + currentIteration);
        }
    }

    private void execute(ResolvedPlayerOperation operation) {
        if (operation.action() == PlayerAction.JOIN) {
            UUID joined = port.join(operation.dimension(), operation.profileName(), operation.playerId(),
                    operation.x(), operation.y(), operation.z());
            if (joined != null) activePlayerIds.add(joined);
            if (joined != null && recoveryJournal != null) recoveryJournal.recordPlayerJoined(joined);
        } else if (operation.action() == PlayerAction.QUIT) {
            if (port.quit(operation.dimension(), operation.playerId())) {
                activePlayerIds.remove(operation.playerId());
                if (recoveryJournal != null) recoveryJournal.recordPlayerRemoved(operation.playerId());
            }
        } else {
            port.perform(operation.dimension(), operation.playerId(), operation.action(), operation.dimension(),
                    operation.x(), operation.y(), operation.z());
        }
    }

    @Override
    public synchronized void stop(String reason) {
        stateMachine.transitionTo(ExperimentState.STOPPING, "Stopping: " + reason);
        activePlayerIds.clear();
        port.cleanupTestPlayers();
        stateMachine.transitionTo(ExperimentState.ABORTED, "Aborted: " + reason);
        completionCallback.accept(ExperimentState.ABORTED);
    }

    private void completeExperiment() {
        activePlayerIds.clear();
        port.cleanupTestPlayers();
        checkpointTrigger.accept(CheckpointPhase.FINAL_CLEANUP, currentIteration);
        stateMachine.transitionTo(ExperimentState.COMPLETED, "Player experiment completed successfully");
        completionCallback.accept(ExperimentState.COMPLETED);
    }
}
