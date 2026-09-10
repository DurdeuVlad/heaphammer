package com.dwurdy.heaphammer.domain;

import com.github.bsideup.jabel.Desugar;

import java.util.Objects;

/**
 * An immutable checkpoint containing metrics captured at a specific lifecycle phase.
 */
@Desugar
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
