package com.dwurdy.heaphammer.scenario.blockentities;

import com.dwurdy.heaphammer.domain.ExperimentId;
import com.dwurdy.heaphammer.domain.ExperimentPlan;
import com.dwurdy.heaphammer.domain.ExperimentSpec;

import java.util.*;

/**
 * Deterministic scenario planner for block entity lifecycle stress experiments (Section 11.3, Issue #17).
 */
public class BlockEntityScenarioPlanner {

    private static final List<String> DEFAULT_FALLBACK_TYPES = List.of(
            "minecraft:chest",
            "minecraft:furnace",
            "minecraft:hopper",
            "minecraft:barrel",
            "minecraft:dispenser",
            "minecraft:dropper",
            "minecraft:brewing_stand"
    );

    public ExperimentPlan plan(ExperimentSpec spec, List<String> availableBlockEntityTypes) {
        return plan(ExperimentId.generate(), spec, availableBlockEntityTypes);
    }

    public ExperimentPlan plan(ExperimentId id, ExperimentSpec spec, List<String> availableBlockEntityTypes) {
        Objects.requireNonNull(id, "ExperimentId must not be null");
        Objects.requireNonNull(spec, "ExperimentSpec must not be null");

        List<String> pool = (availableBlockEntityTypes == null || availableBlockEntityTypes.isEmpty())
                ? new ArrayList<>(DEFAULT_FALLBACK_TYPES)
                : new ArrayList<>(availableBlockEntityTypes);

        // Sort pool for cross-platform determinism
        Collections.sort(pool);

        Random rng = new Random(spec.seed());
        List<ResolvedBlockEntityOperation> operations = new ArrayList<>();
        int stepIndex = 0;

        for (int iter = 0; iter < spec.iterations(); iter++) {
            List<ResolvedBlockEntityOperation> placedForIter = new ArrayList<>();

            // Phase 1: Place batch
            for (int b = 0; b < spec.batchSize(); b++) {
                String typeId = pool.get(rng.nextInt(pool.size()));
                int offsetX = (b % 8) * 2;
                int offsetZ = (b / 8) * 2;
                int x = spec.centerX() + offsetX;
                int z = spec.centerZ() + offsetZ;
                int y = 64;

                ResolvedBlockEntityOperation op = new ResolvedBlockEntityOperation(
                        iter,
                        stepIndex++,
                        spec.dimension(),
                        x,
                        y,
                        z,
                        typeId,
                        ResolvedBlockEntityOperation.ACTION_PLACE
                );
                operations.add(op);
                placedForIter.add(op);
            }

            // Phase 2: Remove batch
            for (ResolvedBlockEntityOperation placed : placedForIter) {
                operations.add(new ResolvedBlockEntityOperation(
                        iter,
                        stepIndex++,
                        spec.dimension(),
                        placed.x(),
                        placed.y(),
                        placed.z(),
                        placed.blockEntityTypeId(),
                        ResolvedBlockEntityOperation.ACTION_REMOVE
                ));
            }
        }

        int estimatedDurationTicks = spec.iterations() * (spec.holdTicks() + spec.settleTicks() + Math.max(1, spec.batchSize() / spec.maxOperationsPerTick()));

        return ExperimentPlan.forBlockEntities(
                id,
                System.currentTimeMillis(),
                spec,
                operations,
                estimatedDurationTicks
        );
    }
}
