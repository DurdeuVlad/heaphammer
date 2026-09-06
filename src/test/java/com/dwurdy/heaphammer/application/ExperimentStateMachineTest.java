package com.dwurdy.heaphammer.application;

import com.dwurdy.heaphammer.domain.ExperimentState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExperimentStateMachineTest {

    @Test
    @DisplayName("StateMachine transitions through valid lifecycle progression")
    void testNormalProgression() {
        ExperimentStateMachine sm = new ExperimentStateMachine();
        assertEquals(ExperimentState.CREATED, sm.getState());

        assertTrue(sm.transitionTo(ExperimentState.RUNNING, "Starting"));
        assertEquals(ExperimentState.RUNNING, sm.getState());

        assertTrue(sm.transitionTo(ExperimentState.HOLDING, "Holding"));
        assertEquals(ExperimentState.HOLDING, sm.getState());

        assertTrue(sm.transitionTo(ExperimentState.CLEANING_UP, "Cleaning"));
        assertEquals(ExperimentState.CLEANING_UP, sm.getState());

        assertTrue(sm.transitionTo(ExperimentState.SETTLING, "Settling"));
        assertEquals(ExperimentState.SETTLING, sm.getState());

        assertTrue(sm.transitionTo(ExperimentState.MEASURING, "Measuring"));
        assertEquals(ExperimentState.MEASURING, sm.getState());

        assertTrue(sm.transitionTo(ExperimentState.COMPLETED, "Done"));
        assertEquals(ExperimentState.COMPLETED, sm.getState());
        assertTrue(sm.getState().isTerminal());

        // Cannot transition out of terminal state
        assertFalse(sm.transitionTo(ExperimentState.RUNNING, "Illegal restart"));
    }

    @Test
    @DisplayName("StateMachine permits emergency stop and abort from any active state")
    void testEmergencyAbort() {
        ExperimentStateMachine sm = new ExperimentStateMachine();
        sm.transitionTo(ExperimentState.RUNNING, "Running");
        assertTrue(sm.transitionTo(ExperimentState.STOPPING, "Stop requested"));
        assertTrue(sm.transitionTo(ExperimentState.ABORTED, "Aborted"));
        assertTrue(sm.getState().isTerminal());
    }
}
