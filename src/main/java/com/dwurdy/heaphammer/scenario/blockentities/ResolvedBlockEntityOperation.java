package com.dwurdy.heaphammer.scenario.blockentities;

import java.util.Objects;

/**
 * Resolved deterministic operation on a block entity lifecycle (Section 11.3, Issue #17).
 */
public record ResolvedBlockEntityOperation(
        int iteration,
        int stepIndex,
        String dimension,
        int x,
        int y,
        int z,
        String blockEntityTypeId,
        String action
) {
    public static final String ACTION_PLACE = "PLACE";
    public static final String ACTION_REMOVE = "REMOVE";

    public ResolvedBlockEntityOperation {
        Objects.requireNonNull(dimension, "dimension must not be null");
        Objects.requireNonNull(blockEntityTypeId, "blockEntityTypeId must not be null");
        Objects.requireNonNull(action, "action must not be null");
    }
}
