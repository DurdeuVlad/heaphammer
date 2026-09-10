package com.dwurdy.heaphammer.adapter;

import com.github.bsideup.jabel.Desugar;

import com.dwurdy.heaphammer.domain.CheckpointPhase;
import com.dwurdy.heaphammer.domain.ExperimentPlan;
import com.dwurdy.heaphammer.domain.ExperimentState;
import com.dwurdy.heaphammer.platform.PlatformAdapter;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Execution context provided to workload adapters during scenario runs (Section 15.7).
 */
@Desugar
public record AdapterExecutionContext(
        ExperimentPlan plan,
        PlatformAdapter platform,
        BiConsumer<CheckpointPhase, Integer> checkpointTrigger,
        Consumer<ExperimentState> completionCallback
) {
    public AdapterExecutionContext {
        Objects.requireNonNull(plan, "plan must not be null");
        Objects.requireNonNull(platform, "platform must not be null");
        Objects.requireNonNull(checkpointTrigger, "checkpointTrigger must not be null");
        Objects.requireNonNull(completionCallback, "completionCallback must not be null");
    }
}
