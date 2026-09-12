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
        boolean cleanupValid,
        CheckpointDiagnostics diagnostics
) {
    public Checkpoint {
        Objects.requireNonNull(phase, "phase must not be null");
        Objects.requireNonNull(metrics, "metrics must not be null");
        diagnostics = diagnostics == null ? CheckpointDiagnostics.EMPTY : diagnostics;
    }

    public Checkpoint(
            CheckpointPhase phase,
            int iteration,
            long timestampEpochMs,
            MetricSample metrics,
            boolean cleanupValid
    ) {
        this(phase, iteration, timestampEpochMs, metrics, cleanupValid, CheckpointDiagnostics.EMPTY);
    }
}
