package com.dwurdy.heaphammer.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionBudgetTest {

    @Test
    @DisplayName("ExecutionBudget triggers exceeded when operations reach maximum limit")
    void testOperationLimit() {
        ExecutionBudget budget = new ExecutionBudget(3, 100);
        budget.startTick();
        assertFalse(budget.isExceeded());

        budget.recordOperation();
        assertFalse(budget.isExceeded());

        budget.recordOperation();
        assertFalse(budget.isExceeded());

        budget.recordOperation();
        assertTrue(budget.isExceeded());
        assertEquals(3, budget.getOperationsExecutedInCurrentTick());

        // Reset on new tick
        budget.startTick();
        assertFalse(budget.isExceeded());
        assertEquals(0, budget.getOperationsExecutedInCurrentTick());
    }
}
