package com.dwurdy.heaphammer.domain;

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
        int estimatedDurationTicks,
        int uniqueChunksCount
) {
    public ExperimentPlan {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(spec, "spec must not be null");
        Objects.requireNonNull(operations, "operations must not be null");
        operations = Collections.unmodifiableList(List.copyOf(operations));
    }

    public int totalOperations() {
        return operations.size();
    }
}
