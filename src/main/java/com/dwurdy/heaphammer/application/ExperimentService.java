package com.dwurdy.heaphammer.application;

import com.dwurdy.heaphammer.domain.ExperimentPlan;
import com.dwurdy.heaphammer.domain.ExperimentState;
import com.dwurdy.heaphammer.domain.ScenarioId;
import com.dwurdy.heaphammer.domain.EntityWorkloadProfile;
import com.dwurdy.heaphammer.domain.PlatformCapability;
import com.dwurdy.heaphammer.platform.PlatformAdapter;
import com.dwurdy.heaphammer.scenario.ScenarioExecutor;
import com.dwurdy.heaphammer.scenario.blockentities.BlockEntityScenarioExecutor;
import com.dwurdy.heaphammer.scenario.chunks.ChunkScenarioExecutor;
import com.dwurdy.heaphammer.scenario.entities.EntityScenarioExecutor;
import com.dwurdy.heaphammer.scenario.players.PlayerScenarioExecutor;
import com.dwurdy.heaphammer.scenario.soak.SoakScenarioExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Orchestrator service managing experiment execution across server ticks.
 */
public class ExperimentService {
    private static final Logger LOGGER = LoggerFactory.getLogger("heaphammer-service");

    private final PlatformAdapter platform;
    private final SafetyCircuitBreaker circuitBreaker;
    private final CrashRecoveryJournal recoveryJournal;
    private volatile ScenarioExecutor activeExecutor = null;
    private BiConsumer<com.dwurdy.heaphammer.domain.CheckpointPhase, Integer> checkpointListener = (p, i) -> {};
    private Consumer<ScenarioExecutor> completionListener = e -> {};

    public ExperimentService(PlatformAdapter platform) {
        this(platform, new SafetyCircuitBreaker(), new CrashRecoveryJournal());
    }

    public ExperimentService(PlatformAdapter platform, SafetyCircuitBreaker circuitBreaker, CrashRecoveryJournal recoveryJournal) {
        this.platform = Objects.requireNonNull(platform, "platform must not be null");
        this.circuitBreaker = Objects.requireNonNull(circuitBreaker, "circuitBreaker must not be null");
        this.recoveryJournal = Objects.requireNonNull(recoveryJournal, "recoveryJournal must not be null");
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

        Function<BiConsumer<com.dwurdy.heaphammer.domain.CheckpointPhase, Integer>, ScenarioExecutor> factory =
                checkpoint -> createExecutor(plan, checkpoint, state -> {});
        ScenarioExecutor executor;
        if (plan.spec().isSoak()) {
            executor = new SoakScenarioExecutor(plan, factory,
                    (phase, iteration) -> checkpointListener.accept(phase, iteration),
                    this::onExecutorFinished);
        } else {
            executor = createExecutor(plan,
                    (phase, iteration) -> checkpointListener.accept(phase, iteration),
                    this::onExecutorFinished);
        }

        boolean isWarmup = plan.spec().warmupIterations() > 0;
        executor.getStateMachine().transitionTo(
                isWarmup ? ExperimentState.WARMING_UP : ExperimentState.RUNNING,
                "Experiment started"
        );

        this.activeExecutor = executor;
        recoveryJournal.recordStart(plan);
        return executor;
    }

    private ScenarioExecutor createExecutor(
            ExperimentPlan plan,
            BiConsumer<com.dwurdy.heaphammer.domain.CheckpointPhase, Integer> checkpoint,
            Consumer<ExperimentState> completion
    ) {
        ScenarioExecutor executor;
        if (plan.spec().scenarioId().equals(ScenarioId.PLAYERS)) {
            com.dwurdy.heaphammer.platform.PlayerLifecyclePort playerPort = platform.getPlayerLifecyclePort()
                    .orElseThrow(() -> new IllegalStateException(
                            "Player lifecycle scenario is unsupported by this platform: "
                                    + platform.getCapabilities().status(com.dwurdy.heaphammer.domain.PlatformCapability.PLAYER_LIFECYCLE).reason()));
            executor = new PlayerScenarioExecutor(
                    plan,
                    playerPort,
                    checkpoint,
                    completion,
                    recoveryJournal
            );
        } else if (plan.spec().scenarioId().equals(ScenarioId.ENTITIES)
                && plan.spec().entityProfile() != EntityWorkloadProfile.TRANSIENT) {
            PlatformCapability capability = plan.spec().entityProfile() == EntityWorkloadProfile.PERSISTENT
                    ? PlatformCapability.PERSISTENT_ENTITIES : PlatformCapability.UNTICKED_CHUNKS;
            if (!platform.getCapabilities().supports(capability)) {
                throw new IllegalStateException("Entity profile " + plan.spec().entityProfile()
                        + " is unsupported by this platform: " + platform.getCapabilities().status(capability).reason());
            }
            platform.getEntityLifecyclePort().orElseThrow(() ->
                    new IllegalStateException("Entity lifecycle extension is unavailable on this platform"));
            executor = new EntityScenarioExecutor(
                    plan,
                    platform,
                    checkpoint,
                    completion,
                    recoveryJournal
            );
        } else {
        Optional<com.dwurdy.heaphammer.adapter.WorkloadAdapter> customAdapter =
                com.dwurdy.heaphammer.adapter.WorkloadAdapterRegistry.getInstance()
                        .findAdapterForScenario(plan.spec().scenarioId().value());

        if (customAdapter.isPresent()) {
            Optional<ScenarioExecutor> customExecutor = customAdapter.get().createExecutor(
                    plan,
                    platform,
                    checkpoint,
                    completion
            );
            if (customExecutor.isPresent()) {
                executor = customExecutor.get();
            } else if (plan.spec().scenarioId() == ScenarioId.ENTITIES) {
                executor = new EntityScenarioExecutor(
                        plan,
                        platform,
                        checkpoint,
                        completion,
                        recoveryJournal
                );
            } else if (plan.spec().scenarioId() == ScenarioId.BLOCK_ENTITIES) {
                executor = new BlockEntityScenarioExecutor(
                        plan,
                        platform,
                        checkpoint,
                        completion,
                        recoveryJournal
                );
            } else {
                executor = new ChunkScenarioExecutor(
                        plan,
                        platform.getChunkTicketManager(),
                        checkpoint,
                        completion
                );
            }
        } else if (plan.spec().scenarioId() == ScenarioId.ENTITIES) {
            executor = new EntityScenarioExecutor(
                    plan,
                    platform,
                    checkpoint,
                    completion,
                    recoveryJournal
            );
        } else if (plan.spec().scenarioId() == ScenarioId.BLOCK_ENTITIES) {
            executor = new BlockEntityScenarioExecutor(
                    plan,
                    platform,
                    checkpoint,
                    completion,
                    recoveryJournal
            );
        } else {
            executor = new ChunkScenarioExecutor(
                    plan,
                    platform.getChunkTicketManager(),
                    checkpoint,
                    completion
            );
        }
        }
        return executor;
    }

    public synchronized boolean stop(String reason) {
        if (activeExecutor != null && !activeExecutor.getStateMachine().getState().isTerminal()) {
            LOGGER.warn("Stopping experiment {}: {}", activeExecutor.getPlan().id(), reason);
            activeExecutor.stop(reason);
            recoveryJournal.recordFinish();
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

    public CrashRecoveryJournal getRecoveryJournal() {
        return recoveryJournal;
    }

    public SafetyCircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    private void onServerTick(long tick) {
        ScenarioExecutor executor = this.activeExecutor;
        if (executor != null && !executor.getStateMachine().getState().isTerminal()) {
            SafetyCircuitBreaker.TripResult trip = circuitBreaker.evaluate();
            if (trip.tripped()) {
                LOGGER.error("SAFETY CIRCUIT BREAKER TRIPPED: {}", trip.reason());
                executor.stop(trip.reason());
                return;
            }

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
        recoveryJournal.recordFinish();
        ScenarioExecutor finished = this.activeExecutor;
        this.activeExecutor = null;
        if (finished != null) {
            completionListener.accept(finished);
        }
    }
}
