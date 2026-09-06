package com.dwurdy.heaphammer.domain;

import java.util.Objects;

/**
 * An immutable checkpoint containing metrics captured at a specific lifecycle phase.
 */
public record Checkpoint(
        CheckpointPhase phase,
        int iteration,
        long timestampEpochMs,
        MetricSample metrics,
        boolean cleanupValid
) {
    public Checkpoint {
        Objects.requireNonNull(phase, "phase must not be null");
        Objects.requireNonNull(metrics, "metrics must not be null");
    }
}
