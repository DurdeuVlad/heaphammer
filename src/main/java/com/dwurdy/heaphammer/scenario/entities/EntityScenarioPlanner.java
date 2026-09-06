package com.dwurdy.heaphammer.scenario.entities;

import com.dwurdy.heaphammer.domain.ExperimentId;
import com.dwurdy.heaphammer.domain.ExperimentPlan;
import com.dwurdy.heaphammer.domain.ExperimentSpec;

import java.util.*;

/**
 * Deterministic scenario planner for entity churn experiments (Section 11.2, Issue #16).
 */
public class EntityScenarioPlanner {

    private static final List<String> DEFAULT_FALLBACK_TYPES = List.of(
            "minecraft:pig",
            "minecraft:cow",
            "minecraft:sheep",
            "minecraft:chicken",
            "minecraft:zombie",
            "minecraft:skeleton"
    );

    public ExperimentPlan plan(ExperimentSpec spec, List<String> availableEntityTypes) {
        return plan(ExperimentId.generate(), spec, availableEntityTypes);
    }

    public ExperimentPlan plan(ExperimentId id, ExperimentSpec spec, List<String> availableEntityTypes) {
        Objects.requireNonNull(id, "ExperimentId must not be null");
        Objects.requireNonNull(spec, "ExperimentSpec must not be null");

        List<String> pool = (availableEntityTypes == null || availableEntityTypes.isEmpty())
                ? new ArrayList<>(DEFAULT_FALLBACK_TYPES)
                : new ArrayList<>(availableEntityTypes);

        // Sort pool for cross-platform determinism regardless of input order
        Collections.sort(pool);

        Random rng = new Random(spec.seed());
        List<ResolvedEntityOperation> operations = new ArrayList<>();
        int stepIndex = 0;

        String removeMode = (spec.strategy() != null && spec.strategy().equalsIgnoreCase("KILL"))
                ? ResolvedEntityOperation.MODE_KILL
                : ResolvedEntityOperation.MODE_DISCARD;

        for (int iter = 0; iter < spec.iterations(); iter++) {
            // Phase 1: Spawn batch
            for (int b = 0; b < spec.batchSize(); b++) {
                String typeId = pool.get(rng.nextInt(pool.size()));
                double angle = rng.nextDouble() * 2.0 * Math.PI;
                double dist = rng.nextDouble() * spec.radius();
                double x = spec.centerX() + (dist * Math.cos(angle));
                double z = spec.centerZ() + (dist * Math.sin(angle));
                double y = 64.0;

                operations.add(new ResolvedEntityOperation(
                        iter,
                        stepIndex++,
                        spec.dimension(),
                        x,
                        y,
                        z,
                        typeId,
                        ResolvedEntityOperation.ACTION_SPAWN,
                        spec.holdTicks(),
                        removeMode
                ));
            }

            // Phase 2: Remove batch
            for (int b = 0; b < spec.batchSize(); b++) {
                operations.add(new ResolvedEntityOperation(
                        iter,
                        stepIndex++,
                        spec.dimension(),
                        spec.centerX(),
                        64.0,
                        spec.centerZ(),
                        "ANY",
                        ResolvedEntityOperation.ACTION_REMOVE,
                        0,
                        removeMode
                ));
            }
        }

        int estimatedDurationTicks = spec.iterations() * (spec.holdTicks() + spec.settleTicks() + Math.max(1, spec.batchSize() / spec.maxOperationsPerTick()));

        return ExperimentPlan.forEntities(
                id,
                System.currentTimeMillis(),
                spec,
                operations,
                estimatedDurationTicks
        );
    }
}
