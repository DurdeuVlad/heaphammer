package com.dwurdy.testmod.omnitrack;

import com.dwurdy.heaphammer.adapter.WorkloadAdapter;
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
 * Sample third-party workload adapter providing custom stress profiling hooks for OmniTrack.
 */
public class OmniTrackWorkloadAdapter implements WorkloadAdapter {
    @Override
    public String adapterId() {
        return "omnitrack";
    }

    @Override
    public String displayName() {
        return "OmniTrack Analytics Adapter";
    }

    @Override
    public List<String> supportedScenarios() {
        return List.of("omnitrack-analytics");
    }

    @Override
    public Optional<ScenarioExecutor> createExecutor(
            ExperimentPlan plan,
            PlatformAdapter platform,
            BiConsumer<CheckpointPhase, Integer> checkpointTrigger,
            Consumer<ExperimentState> completionCallback
    ) {
        return Optional.empty();
    }
}
