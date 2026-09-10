package com.dwurdy.heaphammer.scenario.chunks;

import com.dwurdy.heaphammer.domain.ExperimentId;
import com.dwurdy.heaphammer.domain.ExperimentPlan;
import com.dwurdy.heaphammer.domain.ExperimentSpec;
import com.dwurdy.heaphammer.domain.ResolvedChunkOperation;

import java.util.*;

/**
 * Deterministic chunk workload planner (BR-001, BR-002).
 */
public class ChunkWorkloadPlanner {

    public ExperimentPlan plan(ExperimentSpec spec) {
        return plan(ExperimentId.generate(), spec);
    }

    public ExperimentPlan plan(ExperimentId id, ExperimentSpec spec) {
        Objects.requireNonNull(id, "ExperimentId must not be null");
        Objects.requireNonNull(spec, "ExperimentSpec must not be null");

        ChunkSelectionStrategy strategy = resolveStrategy(spec.strategy());
        List<ChunkSelectionStrategy.ChunkCoord> relativeCoords = strategy.generateCoords(spec);

        if (relativeCoords.isEmpty()) {
            relativeCoords = Collections.unmodifiableList(Arrays.asList(new ChunkSelectionStrategy.ChunkCoord(0, 0)));
        }

        List<ResolvedChunkOperation> operations = new ArrayList<>();
        Set<Long> uniqueChunks = new HashSet<>();
        int stepIndex = 0;
        int coordIndex = 0;

        for (int iter = 0; iter < spec.iterations(); iter++) {
            List<ChunkSelectionStrategy.ChunkCoord> batch = new ArrayList<>(spec.batchSize());
            for (int b = 0; b < spec.batchSize(); b++) {
                ChunkSelectionStrategy.ChunkCoord coord = relativeCoords.get(coordIndex % relativeCoords.size());
                batch.add(coord);
                coordIndex++;
            }

            // Phase 1: Acquire tickets for this batch
            for (ChunkSelectionStrategy.ChunkCoord coord : batch) {
                int absoluteChunkX = spec.centerX() + coord.x();
                int absoluteChunkZ = spec.centerZ() + coord.z();
                ResolvedChunkOperation op = new ResolvedChunkOperation(
                        iter,
                        stepIndex++,
                        spec.dimension(),
                        absoluteChunkX,
                        absoluteChunkZ,
                        ResolvedChunkOperation.ACTION_ACQUIRE
                );
                operations.add(op);
                uniqueChunks.add(op.chunkPosKey());
            }

            // Phase 2: Release tickets after hold period
            for (ChunkSelectionStrategy.ChunkCoord coord : batch) {
                int absoluteChunkX = spec.centerX() + coord.x();
                int absoluteChunkZ = spec.centerZ() + coord.z();
                ResolvedChunkOperation op = new ResolvedChunkOperation(
                        iter,
                        stepIndex++,
                        spec.dimension(),
                        absoluteChunkX,
                        absoluteChunkZ,
                        ResolvedChunkOperation.ACTION_RELEASE
                );
                operations.add(op);
            }
        }

        int estimatedDurationTicks = spec.iterations() * (spec.holdTicks() + spec.settleTicks() + Math.max(1, spec.batchSize() / spec.maxOperationsPerTick()));

        return new ExperimentPlan(
                id,
                System.currentTimeMillis(),
                spec,
                operations,
                estimatedDurationTicks,
                uniqueChunks.size()
        );
    }

    public static ChunkSelectionStrategy resolveStrategy(String strategyName) {
        if (strategyName == null) return new SpiralStrategy();
        String normalized = strategyName.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "RING" -> new RingStrategy();
            case "RANDOM_WALK", "RANDOM" -> new RandomWalkStrategy();
            case "HOTSPOT_CHURN", "HOTSPOT" -> new HotspotChurnStrategy();
            case "GRID_SWEEP", "GRID" -> new GridSweepStrategy();
            case "SPIRAL" -> new SpiralStrategy();
            default -> new SpiralStrategy();
        };
    }
}
