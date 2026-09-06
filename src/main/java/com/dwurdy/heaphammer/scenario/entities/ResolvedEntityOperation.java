package com.dwurdy.heaphammer.scenario.entities;

import java.util.Objects;

/**
 * Resolved deterministic operation on an entity instance (Section 11.2, Issue #16).
 */
public record ResolvedEntityOperation(
        int iteration,
        int stepIndex,
        String dimension,
        double x,
        double y,
        double z,
        String entityTypeId,
        String action,
        int lifetimeTicks,
        String removeMode
) {
    public static final String ACTION_SPAWN = "SPAWN";
    public static final String ACTION_REMOVE = "REMOVE";
    public static final String MODE_DISCARD = "DISCARD";
    public static final String MODE_KILL = "KILL";

    public ResolvedEntityOperation {
        Objects.requireNonNull(dimension, "dimension must not be null");
        Objects.requireNonNull(entityTypeId, "entityTypeId must not be null");
        Objects.requireNonNull(action, "action must not be null");
        if (removeMode == null) {
            removeMode = MODE_DISCARD;
        }
    }
}
