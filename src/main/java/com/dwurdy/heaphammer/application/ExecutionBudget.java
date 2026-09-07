package com.dwurdy.heaphammer.application;

/**
 * Enforces per-tick execution safety limits (operations and wall-clock duration) adhering to BR-010.
 */
public class ExecutionBudget {
    private final int maxOperationsPerTick;
    private final long maxNanosPerTick;
    private int operationsExecutedInCurrentTick;
    private long tickStartNanoTime;

    public ExecutionBudget(int maxOperationsPerTick, long maxMillisPerTick) {
        if (maxOperationsPerTick <= 0) throw new IllegalArgumentException("maxOperationsPerTick must be > 0");
        if (maxMillisPerTick <= 0) throw new IllegalArgumentException("maxMillisPerTick must be > 0");

        com.dwurdy.heaphammer.infrastructure.config.HeapHammerConfig config =
                com.dwurdy.heaphammer.infrastructure.config.ConfigManager.getActiveConfig();
        int maxOpsCeiling = config != null ? config.getMaxOperationsPerTick() : 50;
        long maxMsCeiling = config != null ? config.getMaxMillisPerTick() : 35;

        this.maxOperationsPerTick = Math.min(maxOpsCeiling, maxOperationsPerTick);
        this.maxNanosPerTick = Math.min(maxMsCeiling, maxMillisPerTick) * 1_000_000L;
    }

    public void startTick() {
        this.operationsExecutedInCurrentTick = 0;
        this.tickStartNanoTime = System.nanoTime();
    }

    public void recordOperation() {
        this.operationsExecutedInCurrentTick++;
    }

    public boolean isExceeded() {
        if (operationsExecutedInCurrentTick >= maxOperationsPerTick) {
            return true;
        }
        return (System.nanoTime() - tickStartNanoTime) >= maxNanosPerTick;
    }

    public int getOperationsExecutedInCurrentTick() {
        return operationsExecutedInCurrentTick;
    }

    public int getMaxOperationsPerTick() {
        return maxOperationsPerTick;
    }
}
