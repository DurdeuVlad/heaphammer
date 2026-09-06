package com.dwurdy.heaphammer.domain;

import com.dwurdy.heaphammer.scenario.blockentities.ResolvedBlockEntityOperation;
import com.dwurdy.heaphammer.scenario.entities.ResolvedEntityOperation;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Persisted execution plan containing deterministic resolved operations.
 */
public record ExperimentPlan(
        ExperimentId id,
        long createdAtEpochMs,
        ExperimentSpec spec,
        List<ResolvedChunkOperation> operations,
        List<ResolvedEntityOperation> entityOperations,
        List<ResolvedBlockEntityOperation> blockEntityOperations,
        int estimatedDurationTicks,
        int uniqueChunksCount
) {
    public ExperimentPlan {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(spec, "spec must not be null");
        operations = (operations == null) ? List.of() : Collections.unmodifiableList(List.copyOf(operations));
        entityOperations = (entityOperations == null) ? List.of() : Collections.unmodifiableList(List.copyOf(entityOperations));
        blockEntityOperations = (blockEntityOperations == null) ? List.of() : Collections.unmodifiableList(List.copyOf(blockEntityOperations));
    }

    public ExperimentPlan(
            ExperimentId id,
            long createdAtEpochMs,
            ExperimentSpec spec,
            List<ResolvedChunkOperation> operations,
            int estimatedDurationTicks,
            int uniqueChunksCount
    ) {
        this(id, createdAtEpochMs, spec, operations, List.of(), List.of(), estimatedDurationTicks, uniqueChunksCount);
    }

    public static ExperimentPlan forEntities(
            ExperimentId id,
            long createdAtEpochMs,
            ExperimentSpec spec,
            List<ResolvedEntityOperation> entityOperations,
            int estimatedDurationTicks
    ) {
        return new ExperimentPlan(id, createdAtEpochMs, spec, List.of(), entityOperations, List.of(), estimatedDurationTicks, 0);
    }

    public static ExperimentPlan forBlockEntities(
            ExperimentId id,
            long createdAtEpochMs,
            ExperimentSpec spec,
            List<ResolvedBlockEntityOperation> blockEntityOperations,
            int estimatedDurationTicks
    ) {
        return new ExperimentPlan(id, createdAtEpochMs, spec, List.of(), List.of(), blockEntityOperations, estimatedDurationTicks, 0);
    }

    public int totalOperations() {
        return operations.size() + entityOperations.size() + blockEntityOperations.size();
    }
}
