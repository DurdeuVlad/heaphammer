package com.dwurdy.heaphammer.domain;

import java.util.ArrayList;


import com.github.bsideup.jabel.Desugar;

import com.dwurdy.heaphammer.scenario.blockentities.ResolvedBlockEntityOperation;
import com.dwurdy.heaphammer.scenario.entities.ResolvedEntityOperation;
import com.dwurdy.heaphammer.scenario.targeting.TargetPartition;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Persisted execution plan containing deterministic resolved operations.
 */
@Desugar
public record ExperimentPlan(
        ExperimentId id,
        long createdAtEpochMs,
        ExperimentSpec spec,
        List<ResolvedChunkOperation> operations,
        List<ResolvedEntityOperation> entityOperations,
        List<ResolvedBlockEntityOperation> blockEntityOperations,
        TargetPartition targetPartition,
        int estimatedDurationTicks,
        int uniqueChunksCount
) {
    public ExperimentPlan {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(spec, "spec must not be null");
        operations = (operations == null) ? Collections.emptyList() : Collections.unmodifiableList(Collections.unmodifiableList(new ArrayList<>(operations)));
        entityOperations = (entityOperations == null) ? Collections.emptyList() : Collections.unmodifiableList(Collections.unmodifiableList(new ArrayList<>(entityOperations)));
        blockEntityOperations = (blockEntityOperations == null) ? Collections.emptyList() : Collections.unmodifiableList(Collections.unmodifiableList(new ArrayList<>(blockEntityOperations)));
    }

    public ExperimentPlan(
            ExperimentId id,
            long createdAtEpochMs,
            ExperimentSpec spec,
            List<ResolvedChunkOperation> operations,
            List<ResolvedEntityOperation> entityOperations,
            List<ResolvedBlockEntityOperation> blockEntityOperations,
            int estimatedDurationTicks,
            int uniqueChunksCount
    ) {
        this(id, createdAtEpochMs, spec, operations, entityOperations, blockEntityOperations, null, estimatedDurationTicks, uniqueChunksCount);
    }

    public ExperimentPlan(
            ExperimentId id,
            long createdAtEpochMs,
            ExperimentSpec spec,
            List<ResolvedChunkOperation> operations,
            int estimatedDurationTicks,
            int uniqueChunksCount
    ) {
        this(id, createdAtEpochMs, spec, operations, Collections.emptyList(), Collections.emptyList(), null, estimatedDurationTicks, uniqueChunksCount);
    }

    public static ExperimentPlan forEntities(
            ExperimentId id,
            long createdAtEpochMs,
            ExperimentSpec spec,
            List<ResolvedEntityOperation> entityOperations,
            TargetPartition targetPartition,
            int estimatedDurationTicks
    ) {
        return new ExperimentPlan(id, createdAtEpochMs, spec, Collections.emptyList(), entityOperations, Collections.emptyList(), targetPartition, estimatedDurationTicks, 0);
    }

    public static ExperimentPlan forEntities(
            ExperimentId id,
            long createdAtEpochMs,
            ExperimentSpec spec,
            List<ResolvedEntityOperation> entityOperations,
            int estimatedDurationTicks
    ) {
        return forEntities(id, createdAtEpochMs, spec, entityOperations, null, estimatedDurationTicks);
    }

    public static ExperimentPlan forBlockEntities(
            ExperimentId id,
            long createdAtEpochMs,
            ExperimentSpec spec,
            List<ResolvedBlockEntityOperation> blockEntityOperations,
            TargetPartition targetPartition,
            int estimatedDurationTicks
    ) {
        return new ExperimentPlan(id, createdAtEpochMs, spec, Collections.emptyList(), Collections.emptyList(), blockEntityOperations, targetPartition, estimatedDurationTicks, 0);
    }

    public static ExperimentPlan forBlockEntities(
            ExperimentId id,
            long createdAtEpochMs,
            ExperimentSpec spec,
            List<ResolvedBlockEntityOperation> blockEntityOperations,
            int estimatedDurationTicks
    ) {
        return forBlockEntities(id, createdAtEpochMs, spec, blockEntityOperations, null, estimatedDurationTicks);
    }

    public Optional<TargetPartition> optionalTargetPartition() {
        return Optional.ofNullable(targetPartition);
    }

    public int totalOperations() {
        return operations.size() + entityOperations.size() + blockEntityOperations.size();
    }
}
