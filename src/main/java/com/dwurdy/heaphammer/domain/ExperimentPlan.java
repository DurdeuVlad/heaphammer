package com.dwurdy.heaphammer.domain;

import com.dwurdy.heaphammer.scenario.blockentities.ResolvedBlockEntityOperation;
import com.dwurdy.heaphammer.scenario.entities.ResolvedEntityOperation;
import com.dwurdy.heaphammer.scenario.players.ResolvedPlayerOperation;
import com.dwurdy.heaphammer.scenario.targeting.TargetPartition;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
        List<ResolvedPlayerOperation> playerOperations,
        TargetPartition targetPartition,
        int estimatedDurationTicks,
        int uniqueChunksCount
) {
    public ExperimentPlan {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(spec, "spec must not be null");
        operations = (operations == null) ? List.of() : Collections.unmodifiableList(List.copyOf(operations));
        entityOperations = (entityOperations == null) ? List.of() : Collections.unmodifiableList(List.copyOf(entityOperations));
        blockEntityOperations = (blockEntityOperations == null) ? List.of() : Collections.unmodifiableList(List.copyOf(blockEntityOperations));
        playerOperations = (playerOperations == null) ? List.of() : Collections.unmodifiableList(List.copyOf(playerOperations));
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
        this(id, createdAtEpochMs, spec, operations, entityOperations, blockEntityOperations, List.of(), null, estimatedDurationTicks, uniqueChunksCount);
    }

    public ExperimentPlan(
            ExperimentId id,
            long createdAtEpochMs,
            ExperimentSpec spec,
            List<ResolvedChunkOperation> operations,
            List<ResolvedEntityOperation> entityOperations,
            List<ResolvedBlockEntityOperation> blockEntityOperations,
            List<ResolvedPlayerOperation> playerOperations,
            int estimatedDurationTicks,
            int uniqueChunksCount
    ) {
        this(id, createdAtEpochMs, spec, operations, entityOperations, blockEntityOperations,
                playerOperations, null, estimatedDurationTicks, uniqueChunksCount);
    }

    public ExperimentPlan(
            ExperimentId id,
            long createdAtEpochMs,
            ExperimentSpec spec,
            List<ResolvedChunkOperation> operations,
            int estimatedDurationTicks,
            int uniqueChunksCount
    ) {
        this(id, createdAtEpochMs, spec, operations, List.of(), List.of(), List.of(), null, estimatedDurationTicks, uniqueChunksCount);
    }

    public static ExperimentPlan forEntities(
            ExperimentId id,
            long createdAtEpochMs,
            ExperimentSpec spec,
            List<ResolvedEntityOperation> entityOperations,
            TargetPartition targetPartition,
            int estimatedDurationTicks
    ) {
        return new ExperimentPlan(id, createdAtEpochMs, spec, List.of(), entityOperations, List.of(), List.of(), targetPartition, estimatedDurationTicks, 0);
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
        return new ExperimentPlan(id, createdAtEpochMs, spec, List.of(), List.of(), blockEntityOperations, List.of(), targetPartition, estimatedDurationTicks, 0);
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
        return operations.size() + entityOperations.size() + blockEntityOperations.size() + playerOperations.size();
    }

    public static ExperimentPlan forPlayers(
            ExperimentId id,
            long createdAtEpochMs,
            ExperimentSpec spec,
            List<ResolvedPlayerOperation> playerOperations,
            int estimatedDurationTicks
    ) {
        return new ExperimentPlan(id, createdAtEpochMs, spec, List.of(), List.of(), List.of(),
                playerOperations, null, estimatedDurationTicks, 0);
    }
}
