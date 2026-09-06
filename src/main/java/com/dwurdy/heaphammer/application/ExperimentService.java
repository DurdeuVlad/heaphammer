package com.dwurdy.heaphammer.application;

import com.dwurdy.heaphammer.domain.ExperimentPlan;
import com.dwurdy.heaphammer.domain.ExperimentState;
import com.dwurdy.heaphammer.domain.ScenarioId;
import com.dwurdy.heaphammer.platform.PlatformAdapter;
import com.dwurdy.heaphammer.scenario.ScenarioExecutor;
import com.dwurdy.heaphammer.scenario.blockentities.BlockEntityScenarioExecutor;
import com.dwurdy.heaphammer.scenario.chunks.ChunkScenarioExecutor;
import com.dwurdy.heaphammer.scenario.entities.EntityScenarioExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Orchestrator service managing experiment execution across server ticks.
 */
public class ExperimentService {
    private static final Logger LOGGER = LoggerFactory.getLogger("heaphammer-service");

    private final PlatformAdapter platform;
    private volatile ScenarioExecutor activeExecutor = null;
    private BiConsumer<com.dwurdy.heaphammer.domain.CheckpointPhase, Integer> checkpointListener = (p, i) -> {};
    private Consumer<ScenarioExecutor> completionListener = e -> {};

    public ExperimentService(PlatformAdapter platform) {
        this.platform = Objects.requireNonNull(platform, "platform must not be null");
        platform.registerServerTickHook(this::onServerTick);
    }

    public void setCheckpointListener(BiConsumer<com.dwurdy.heaphammer.domain.CheckpointPhase, Integer> listener) {
        this.checkpointListener = Objects.requireNonNull(listener, "listener must not be null");
    }

    public void setCompletionListener(Consumer<ScenarioExecutor> listener) {
        this.completionListener = Objects.requireNonNull(listener, "listener must not be null");
    }

    public synchronized ScenarioExecutor start(ExperimentPlan plan) {
        Objects.requireNonNull(plan, "plan must not be null");

        if (isExperimentActive()) {
            throw new IllegalStateException("An experiment is already active (" +
                    activeExecutor.getPlan().id() + ": " + activeExecutor.getStateMachine().getState() + ")");
        }

        LOGGER.info("Starting experiment {} ({}, iterations: {}, batch: {}, radius: {})",
                plan.id(), plan.spec().scenarioId(), plan.spec().iterations(), plan.spec().batchSize(), plan.spec().radius());

        ScenarioExecutor executor;
        Optional<com.dwurdy.heaphammer.adapter.WorkloadAdapter> customAdapter =
                com.dwurdy.heaphammer.adapter.WorkloadAdapterRegistry.getInstance()
                        .findAdapterForScenario(plan.spec().scenarioId().value());

        if (customAdapter.isPresent()) {
            Optional<ScenarioExecutor> customExecutor = customAdapter.get().createExecutor(
                    plan,
                    platform,
                    (phase, iter) -> checkpointListener.accept(phase, iter),
                    state -> onExecutorFinished(state)
            );
            if (customExecutor.isPresent()) {
                executor = customExecutor.get();
            } else if (plan.spec().scenarioId() == ScenarioId.ENTITIES) {
                executor = new EntityScenarioExecutor(
                        plan,
                        platform,
                        (phase, iter) -> checkpointListener.accept(phase, iter),
                        state -> onExecutorFinished(state)
                );
            } else if (plan.spec().scenarioId() == ScenarioId.BLOCK_ENTITIES) {
                executor = new BlockEntityScenarioExecutor(
                        plan,
                        platform,
                        (phase, iter) -> checkpointListener.accept(phase, iter),
                        state -> onExecutorFinished(state)
                );
            } else {
                executor = new ChunkScenarioExecutor(
                        plan,
                        platform.getChunkTicketManager(),
                        (phase, iter) -> checkpointListener.accept(phase, iter),
                        state -> onExecutorFinished(state)
                );
            }
        } else if (plan.spec().scenarioId() == ScenarioId.ENTITIES) {
            executor = new EntityScenarioExecutor(
                    plan,
                    platform,
                    (phase, iter) -> checkpointListener.accept(phase, iter),
                    state -> onExecutorFinished(state)
            );
        } else if (plan.spec().scenarioId() == ScenarioId.BLOCK_ENTITIES) {
            executor = new BlockEntityScenarioExecutor(
                    plan,
                    platform,
                    (phase, iter) -> checkpointListener.accept(phase, iter),
                    state -> onExecutorFinished(state)
            );
        } else {
            executor = new ChunkScenarioExecutor(
                    plan,
                    platform.getChunkTicketManager(),
                    (phase, iter) -> checkpointListener.accept(phase, iter),
                    state -> onExecutorFinished(state)
            );
        }

        boolean isWarmup = plan.spec().warmupIterations() > 0;
        executor.getStateMachine().transitionTo(
                isWarmup ? ExperimentState.WARMING_UP : ExperimentState.RUNNING,
                "Experiment started"
        );

        this.activeExecutor = executor;
        return executor;
    }

    public synchronized boolean stop(String reason) {
        if (activeExecutor != null && !activeExecutor.getStateMachine().getState().isTerminal()) {
            LOGGER.warn("Stopping experiment {}: {}", activeExecutor.getPlan().id(), reason);
            activeExecutor.stop(reason);
            return true;
        }
        return false;
    }

    public boolean isExperimentActive() {
        return activeExecutor != null && !activeExecutor.getStateMachine().getState().isTerminal();
    }

    public Optional<ScenarioExecutor> getActiveExecutor() {
        return Optional.ofNullable(activeExecutor);
    }

    private void onServerTick(long tick) {
        ScenarioExecutor executor = this.activeExecutor;
        if (executor != null && !executor.getStateMachine().getState().isTerminal()) {
            try {
                executor.tick();
            } catch (Exception e) {
                LOGGER.error("Uncaught exception during experiment tick execution", e);
                executor.stop("Internal execution error: " + e.getMessage());
            }
        }
    }

    private void onExecutorFinished(ExperimentState finalState) {
        LOGGER.info("Experiment finished with state: {}", finalState);
        ScenarioExecutor finished = this.activeExecutor;
        this.activeExecutor = null;
        if (finished != null) {
            completionListener.accept(finished);
        }
    }
}
