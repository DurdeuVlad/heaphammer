package com.dwurdy.heaphammer.infrastructure.config;

/**
 * Configurable safety limits, circuit breaker settings, and crash recovery options.
 * Stored at config/heaphammer.json.
 */
public class HeapHammerConfig {
    public static final String DEFAULT_CONFIG_PATH = "config/heaphammer.json";

    // Safety limits
    private int maxRadius = 32;
    private int maxIterations = 50;
    private int maxBatchSize = 128;
    private int maxWarmupIterations = 10;
    private int maxHoldTicks = 1200; // 60 seconds
    private int maxSettleTicks = 1200; // 60 seconds
    private int maxOperationsPerTick = 50;
    private long maxMillisPerTick = 35;

    // Safety Circuit Breaker
    private boolean circuitBreakerEnabled = true;
    private long minFreeMemoryMb = 64;

    // Crash Recovery
    private boolean autoCleanupOnStartup = true;

    public HeapHammerConfig() {}

    public int getMaxRadius() {
        return maxRadius;
    }

    public void setMaxRadius(int maxRadius) {
        this.maxRadius = Math.max(1, maxRadius);
    }

    public int getMaxIterations() {
        return maxIterations;
    }

    public void setMaxIterations(int maxIterations) {
        this.maxIterations = Math.max(1, maxIterations);
    }

    public int getMaxBatchSize() {
        return maxBatchSize;
    }

    public void setMaxBatchSize(int maxBatchSize) {
        this.maxBatchSize = Math.max(1, maxBatchSize);
    }

    public int getMaxWarmupIterations() {
        return maxWarmupIterations;
    }

    public void setMaxWarmupIterations(int maxWarmupIterations) {
        this.maxWarmupIterations = Math.max(0, maxWarmupIterations);
    }

    public int getMaxHoldTicks() {
        return maxHoldTicks;
    }

    public void setMaxHoldTicks(int maxHoldTicks) {
        this.maxHoldTicks = Math.max(0, maxHoldTicks);
    }

    public int getMaxSettleTicks() {
        return maxSettleTicks;
    }

    public void setMaxSettleTicks(int maxSettleTicks) {
        this.maxSettleTicks = Math.max(0, maxSettleTicks);
    }

    public int getMaxOperationsPerTick() {
        return maxOperationsPerTick;
    }

    public void setMaxOperationsPerTick(int maxOperationsPerTick) {
        this.maxOperationsPerTick = Math.max(1, maxOperationsPerTick);
    }

    public long getMaxMillisPerTick() {
        return maxMillisPerTick;
    }

    public void setMaxMillisPerTick(long maxMillisPerTick) {
        this.maxMillisPerTick = Math.max(1, maxMillisPerTick);
    }

    public boolean isCircuitBreakerEnabled() {
        return circuitBreakerEnabled;
    }

    public void setCircuitBreakerEnabled(boolean circuitBreakerEnabled) {
        this.circuitBreakerEnabled = circuitBreakerEnabled;
    }

    public long getMinFreeMemoryMb() {
        return minFreeMemoryMb;
    }

    public void setMinFreeMemoryMb(long minFreeMemoryMb) {
        this.minFreeMemoryMb = Math.max(16, minFreeMemoryMb);
    }

    public boolean isAutoCleanupOnStartup() {
        return autoCleanupOnStartup;
    }

    public void setAutoCleanupOnStartup(boolean autoCleanupOnStartup) {
        this.autoCleanupOnStartup = autoCleanupOnStartup;
    }
}
