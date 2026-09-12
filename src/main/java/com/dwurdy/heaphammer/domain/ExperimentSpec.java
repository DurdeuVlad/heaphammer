package com.dwurdy.heaphammer.domain;

import com.dwurdy.heaphammer.infrastructure.config.ConfigManager;
import com.dwurdy.heaphammer.infrastructure.config.HeapHammerConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable specification defining an experiment run or plan.
 */
public record ExperimentSpec(
        ScenarioId scenarioId,
        long seed,
        String dimension,
        int centerX,
        int centerZ,
        int radius,
        int iterations,
        int batchSize,
        String strategy,
        int warmupIterations,
        int holdTicks,
        int settleTicks,
        int maxOperationsPerTick,
        long maxMillisPerTick,
        boolean explicitGc,
        double coverage,
        List<String> includeMods,
        List<String> excludeMods,
        EntityWorkloadProfile entityProfile,
        int loginsPerCycle,
        List<PlayerAction> playerActions,
        long durationSeconds,
        long intervalSeconds,
        List<DiagnosticCollector> diagnosticCollectors,
        List<String> trackedClasses
) {
    public static final String DEFAULT_DIMENSION = "minecraft:overworld";
    public static final String DEFAULT_STRATEGY = "SPIRAL";

    public ExperimentSpec {
        Objects.requireNonNull(scenarioId, "scenarioId must not be null");
        Objects.requireNonNull(dimension, "dimension must not be null");
        Objects.requireNonNull(strategy, "strategy must not be null");

        HeapHammerConfig config = ConfigManager.getActiveConfig();
        int maxRadius = config != null ? config.getMaxRadius() : 32;
        int effectiveMaxRadius = (scenarioId == ScenarioId.ENTITIES) ? maxRadius * 4 : maxRadius;
        if (radius <= 0 || radius > effectiveMaxRadius) {
            throw new IllegalArgumentException("Radius " + radius + " exceeds configured safety ceiling (" + effectiveMaxRadius + " chunks). " +
                    "To allow a larger radius, increase 'maxRadius' in config/heaphammer.json and run '/hh config reload'.");
        }

        int maxIterations = config != null ? config.getMaxIterations() : 50;
        if (iterations <= 0 || iterations > maxIterations) {
            throw new IllegalArgumentException("Iterations " + iterations + " exceeds configured safety ceiling (" + maxIterations + "). " +
                    "To allow more iterations, increase 'maxIterations' in config/heaphammer.json and run '/hh config reload'.");
        }

        int maxBatchSize = config != null ? config.getMaxBatchSize() : 128;
        if (batchSize <= 0 || batchSize > maxBatchSize) {
            throw new IllegalArgumentException("Batch size " + batchSize + " exceeds configured safety ceiling (" + maxBatchSize + "). " +
                    "To allow larger batch sizes, increase 'maxBatchSize' in config/heaphammer.json and run '/hh config reload'.");
        }

        int maxOps = config != null ? config.getMaxOperationsPerTick() : 50;
        if (maxOperationsPerTick <= 0 || maxOperationsPerTick > maxOps) {
            throw new IllegalArgumentException("maxOperationsPerTick " + maxOperationsPerTick + " exceeds configured ceiling (" + maxOps + "). " +
                    "To adjust, modify 'maxOperationsPerTick' in config/heaphammer.json and run '/hh config reload'.");
        }

        long maxMs = config != null ? config.getMaxMillisPerTick() : 35;
        if (maxMillisPerTick <= 0 || maxMillisPerTick > maxMs) {
            throw new IllegalArgumentException("maxMillisPerTick " + maxMillisPerTick + " exceeds configured ceiling (" + maxMs + " ms). " +
                    "To adjust, modify 'maxMillisPerTick' in config/heaphammer.json and run '/hh config reload'.");
        }

        int maxWarmup = config != null ? config.getMaxWarmupIterations() : 10;
        if (warmupIterations < 0 || warmupIterations > maxWarmup) {
            throw new IllegalArgumentException("Warmup iterations " + warmupIterations + " exceeds configured ceiling (" + maxWarmup + "). " +
                    "To adjust, modify 'maxWarmupIterations' in config/heaphammer.json and run '/hh config reload'.");
        }

        int maxHold = config != null ? config.getMaxHoldTicks() : 1200;
        if (holdTicks < 0 || holdTicks > maxHold) {
            throw new IllegalArgumentException("Hold ticks " + holdTicks + " exceeds configured ceiling (" + maxHold + " ticks). " +
                    "To adjust, modify 'maxHoldTicks' in config/heaphammer.json and run '/hh config reload'.");
        }

        int maxSettle = config != null ? config.getMaxSettleTicks() : 1200;
        if (settleTicks < 0 || settleTicks > maxSettle) {
            throw new IllegalArgumentException("Settle ticks " + settleTicks + " exceeds configured ceiling (" + maxSettle + " ticks). " +
                    "To adjust, modify 'maxSettleTicks' in config/heaphammer.json and run '/hh config reload'.");
        }
        if (coverage <= 0.0 || coverage > 1.0) coverage = 1.0;
        includeMods = (includeMods == null) ? List.of() : Collections.unmodifiableList(List.copyOf(includeMods));
        excludeMods = (excludeMods == null) ? List.of() : Collections.unmodifiableList(List.copyOf(excludeMods));
        playerActions = (playerActions == null) ? List.of(PlayerAction.JOIN, PlayerAction.QUIT) : Collections.unmodifiableList(List.copyOf(playerActions));
        diagnosticCollectors = (diagnosticCollectors == null) ? List.of() : Collections.unmodifiableList(List.copyOf(diagnosticCollectors));
        trackedClasses = (trackedClasses == null) ? List.of() : Collections.unmodifiableList(List.copyOf(trackedClasses));
    }

    public ExperimentSpec(
            ScenarioId scenarioId,
            long seed,
            String dimension,
            int centerX,
            int centerZ,
            int radius,
            int iterations,
            int batchSize,
            String strategy,
            int warmupIterations,
            int holdTicks,
            int settleTicks,
            int maxOperationsPerTick,
            long maxMillisPerTick,
            boolean explicitGc
    ) {
        this(scenarioId, seed, dimension, centerX, centerZ, radius, iterations, batchSize,
                strategy, warmupIterations, holdTicks, settleTicks, maxOperationsPerTick,
                maxMillisPerTick, explicitGc, 1.0, List.of(), List.of(),
                EntityWorkloadProfile.TRANSIENT, batchSize, List.of(PlayerAction.JOIN, PlayerAction.QUIT),
                0L, 0L, List.of(), List.of());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ScenarioId scenarioId = ScenarioId.CHUNKS;
        private long seed = 42L;
        private String dimension = DEFAULT_DIMENSION;
        private int centerX = 0;
        private int centerZ = 0;
        private int radius = 6;
        private int iterations = 5;
        private int batchSize = 9;
        private String strategy = DEFAULT_STRATEGY;
        private int warmupIterations = 1;
        private int holdTicks = 20;
        private int settleTicks = 40;
        private int maxOperationsPerTick = 10;
        private long maxMillisPerTick = 15;
        private boolean explicitGc = false;
        private double coverage = 1.0;
        private List<String> includeMods = new ArrayList<>();
        private List<String> excludeMods = new ArrayList<>();
        private EntityWorkloadProfile entityProfile = EntityWorkloadProfile.TRANSIENT;
        private int loginsPerCycle = 1;
        private List<PlayerAction> playerActions = new ArrayList<>(List.of(PlayerAction.JOIN, PlayerAction.QUIT));
        private long durationSeconds = 0L;
        private long intervalSeconds = 0L;
        private List<DiagnosticCollector> diagnosticCollectors = new ArrayList<>();
        private List<String> trackedClasses = new ArrayList<>();

        public Builder scenarioId(ScenarioId scenarioId) { this.scenarioId = scenarioId; return this; }
        public Builder seed(long seed) { this.seed = seed; return this; }
        public Builder dimension(String dimension) { this.dimension = dimension; return this; }
        public Builder center(int x, int z) { this.centerX = x; this.centerZ = z; return this; }
        public Builder centerX(int x) { this.centerX = x; return this; }
        public Builder centerZ(int z) { this.centerZ = z; return this; }
        public Builder radius(int radius) { this.radius = radius; return this; }
        public Builder iterations(int iterations) { this.iterations = iterations; return this; }
        public Builder batchSize(int batchSize) { this.batchSize = batchSize; return this; }
        public Builder strategy(String strategy) { this.strategy = strategy; return this; }
        public Builder warmupIterations(int warmup) { this.warmupIterations = warmup; return this; }
        public Builder holdTicks(int holdTicks) { this.holdTicks = holdTicks; return this; }
        public Builder settleTicks(int settleTicks) { this.settleTicks = settleTicks; return this; }
        public Builder maxOperationsPerTick(int maxOps) { this.maxOperationsPerTick = maxOps; return this; }
        public Builder maxMillisPerTick(long maxMs) { this.maxMillisPerTick = maxMs; return this; }
        public Builder explicitGc(boolean explicitGc) { this.explicitGc = explicitGc; return this; }
        public Builder coverage(double coverage) { this.coverage = coverage; return this; }
        public Builder includeMods(List<String> includeMods) { this.includeMods = (includeMods == null) ? new ArrayList<>() : new ArrayList<>(includeMods); return this; }
        public Builder excludeMods(List<String> excludeMods) { this.excludeMods = (excludeMods == null) ? new ArrayList<>() : new ArrayList<>(excludeMods); return this; }
        public Builder entityProfile(EntityWorkloadProfile profile) { this.entityProfile = profile; return this; }
        public Builder loginsPerCycle(int loginsPerCycle) { this.loginsPerCycle = loginsPerCycle; return this; }
        public Builder playerActions(List<PlayerAction> actions) { this.playerActions = (actions == null) ? new ArrayList<>() : new ArrayList<>(actions); return this; }
        public Builder durationSeconds(long durationSeconds) { this.durationSeconds = durationSeconds; return this; }
        public Builder intervalSeconds(long intervalSeconds) { this.intervalSeconds = intervalSeconds; return this; }
        public Builder diagnosticCollectors(List<DiagnosticCollector> collectors) { this.diagnosticCollectors = (collectors == null) ? new ArrayList<>() : new ArrayList<>(collectors); return this; }
        public Builder trackedClasses(List<String> classes) { this.trackedClasses = (classes == null) ? new ArrayList<>() : new ArrayList<>(classes); return this; }

        public ExperimentSpec build() {
            return new ExperimentSpec(
                    scenarioId, seed, dimension, centerX, centerZ, radius, iterations, batchSize,
                    strategy, warmupIterations, holdTicks, settleTicks, maxOperationsPerTick,
                    maxMillisPerTick, explicitGc, coverage, includeMods, excludeMods,
                    entityProfile, loginsPerCycle, playerActions, durationSeconds, intervalSeconds,
                    diagnosticCollectors, trackedClasses
            );
        }
    }

    public boolean isSoak() {
        return durationSeconds > 0L;
    }
}
