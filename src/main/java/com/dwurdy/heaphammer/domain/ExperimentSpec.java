package com.dwurdy.heaphammer.domain;

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
        List<String> excludeMods
) {
    public static final String DEFAULT_DIMENSION = "minecraft:overworld";
    public static final String DEFAULT_STRATEGY = "SPIRAL";

    public ExperimentSpec {
        Objects.requireNonNull(scenarioId, "scenarioId must not be null");
        Objects.requireNonNull(dimension, "dimension must not be null");
        Objects.requireNonNull(strategy, "strategy must not be null");
        if (radius <= 0) throw new IllegalArgumentException("radius must be > 0");
        if (iterations <= 0) throw new IllegalArgumentException("iterations must be > 0");
        if (batchSize <= 0) throw new IllegalArgumentException("batchSize must be > 0");
        if (maxOperationsPerTick <= 0) throw new IllegalArgumentException("maxOperationsPerTick must be > 0");
        if (maxMillisPerTick <= 0) throw new IllegalArgumentException("maxMillisPerTick must be > 0");
        if (warmupIterations < 0) throw new IllegalArgumentException("warmupIterations must be >= 0");
        if (holdTicks < 0) throw new IllegalArgumentException("holdTicks must be >= 0");
        if (settleTicks < 0) throw new IllegalArgumentException("settleTicks must be >= 0");
        if (coverage <= 0.0 || coverage > 1.0) coverage = 1.0;
        includeMods = (includeMods == null) ? List.of() : Collections.unmodifiableList(List.copyOf(includeMods));
        excludeMods = (excludeMods == null) ? List.of() : Collections.unmodifiableList(List.copyOf(excludeMods));
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
                maxMillisPerTick, explicitGc, 1.0, List.of(), List.of());
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

        public ExperimentSpec build() {
            return new ExperimentSpec(
                    scenarioId, seed, dimension, centerX, centerZ, radius, iterations, batchSize,
                    strategy, warmupIterations, holdTicks, settleTicks, maxOperationsPerTick,
                    maxMillisPerTick, explicitGc, coverage, includeMods, excludeMods
            );
        }
    }
}
