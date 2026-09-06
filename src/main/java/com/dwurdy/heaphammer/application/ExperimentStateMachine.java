package com.dwurdy.heaphammer.application;

import com.dwurdy.heaphammer.domain.ExperimentState;

import java.util.Objects;

/**
 * State machine enforcing valid experiment lifecycle transitions.
 */
public class ExperimentStateMachine {
    private volatile ExperimentState state = ExperimentState.CREATED;
    private volatile String statusMessage = "Created";

    public ExperimentState getState() {
        return state;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public synchronized boolean transitionTo(ExperimentState newState, String message) {
        Objects.requireNonNull(newState, "newState must not be null");

        if (this.state == newState) {
            this.statusMessage = message;
            return true;
        }

        if (this.state.isTerminal()) {
            return false;
        }

        boolean valid = isValidTransition(this.state, newState);
        if (valid) {
            this.state = newState;
            this.statusMessage = message != null ? message : newState.name();
            return true;
        }
        return false;
    }

    private boolean isValidTransition(ExperimentState from, ExperimentState to) {
        // Any non-terminal state can transition to STOPPING, ABORTED, or FAILED
        if (to == ExperimentState.STOPPING || to == ExperimentState.ABORTED || to == ExperimentState.FAILED || to == ExperimentState.CLEANUP_FAILED) {
            return true;
        }

        return switch (from) {
            case CREATED -> to == ExperimentState.PLANNED || to == ExperimentState.WARMING_UP || to == ExperimentState.RUNNING;
            case PLANNED -> to == ExperimentState.WARMING_UP || to == ExperimentState.RUNNING;
            case WARMING_UP -> to == ExperimentState.HOLDING || to == ExperimentState.RUNNING || to == ExperimentState.PAUSED;
            case RUNNING -> to == ExperimentState.HOLDING || to == ExperimentState.CLEANING_UP || to == ExperimentState.PAUSED;
            case HOLDING -> to == ExperimentState.CLEANING_UP || to == ExperimentState.PAUSED;
            case CLEANING_UP -> to == ExperimentState.SETTLING;
            case SETTLING -> to == ExperimentState.MEASURING;
            case MEASURING -> to == ExperimentState.WARMING_UP || to == ExperimentState.RUNNING || to == ExperimentState.COMPLETED;
            case PAUSED -> to == ExperimentState.RUNNING || to == ExperimentState.HOLDING || to == ExperimentState.CLEANING_UP;
            case STOPPING -> to == ExperimentState.CLEANING_UP || to == ExperimentState.ABORTED;
            default -> false;
        };
    }
}
