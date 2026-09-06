package com.dwurdy.heaphammer.domain;

/**
 * Lifecycle states of an experiment run (Section 15.3).
 */
public enum ExperimentState {
    CREATED,
    PLANNED,
    WARMING_UP,
    RUNNING,
    HOLDING,
    CLEANING_UP,
    SETTLING,
    MEASURING,
    COMPLETED,
    PAUSED,
    STOPPING,
    ABORTED,
    FAILED,
    CLEANUP_FAILED;

    public boolean isTerminal() {
        return this == COMPLETED || this == ABORTED || this == FAILED || this == CLEANUP_FAILED;
    }

    public boolean isActive() {
        return !isTerminal() && this != CREATED && this != PLANNED;
    }
}
