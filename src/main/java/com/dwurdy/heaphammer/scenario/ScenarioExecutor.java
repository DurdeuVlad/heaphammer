package com.dwurdy.heaphammer.scenario;

import com.dwurdy.heaphammer.application.ExperimentStateMachine;
import com.dwurdy.heaphammer.domain.ExperimentPlan;

/**
 * Common abstraction for scenario executors across server ticks.
 */
public interface ScenarioExecutor {

    ExperimentStateMachine getStateMachine();

    ExperimentPlan getPlan();

    int getCurrentIteration();

    void tick();

    void stop(String reason);
}
