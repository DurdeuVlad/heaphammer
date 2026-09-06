package com.dwurdy.heaphammer.adapter;

import com.dwurdy.heaphammer.domain.CheckpointPhase;
import com.dwurdy.heaphammer.domain.ExperimentPlan;
import com.dwurdy.heaphammer.domain.ExperimentState;
import com.dwurdy.heaphammer.platform.PlatformAdapter;
import com.dwurdy.heaphammer.scenario.ScenarioExecutor;

import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Public SPI for third-party mods to supply custom workload generation, lifecycles, and verification hooks (Section 15.7, Issue #19).
 */
public interface WorkloadAdapter {

    /**
     * Unique identifier for this adapter (e.g. "appliedenergistics2", "create").
     */
    String adapterId();

    /**
     * Human-readable display name.
     */
    default String displayName() {
        return adapterId();
    }

    /**
     * List of custom scenario identifiers supported by this adapter.
     */
    List<String> supportedScenarios();

    /**
     * Called when the adapter is registered and platform is ready.
     */
    default void initialize(PlatformAdapter platform) {}

    /**
     * Factory method creating a ScenarioExecutor for a planned experiment.
     */
    Optional<ScenarioExecutor> createExecutor(
            ExperimentPlan plan,
            PlatformAdapter platform,
            BiConsumer<CheckpointPhase, Integer> checkpointTrigger,
            Consumer<ExperimentState> completionCallback
    );

    /**
     * Called during cleanup or when HeapHammer is unloaded.
     */
    default void teardown(PlatformAdapter platform) {}
}
