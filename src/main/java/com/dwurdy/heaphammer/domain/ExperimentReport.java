package com.dwurdy.heaphammer.domain;

import java.util.ArrayList;


import com.github.bsideup.jabel.Desugar;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable report generated for an experiment run (BR-011, Section 14).
 */
@Desugar
public record ExperimentReport(
        ExperimentId runId,
        long startTimeEpochMs,
        long endTimeEpochMs,
        String status,
        ExperimentSpec spec,
        EnvironmentFingerprint environment,
        List<Checkpoint> checkpoints,
        DetectionResult detection,
        DiagnosticRefs diagnostics,
        String canonicalCommand,
        List<String> warnings
) {
    public ExperimentReport {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(spec, "spec must not be null");
        Objects.requireNonNull(environment, "environment must not be null");
        Objects.requireNonNull(detection, "detection must not be null");
        diagnostics = (diagnostics == null) ? DiagnosticRefs.EMPTY : diagnostics;
        checkpoints = (checkpoints == null) ? Collections.emptyList() : Collections.unmodifiableList(Collections.unmodifiableList(new ArrayList<>(checkpoints)));
        warnings = (warnings == null) ? Collections.emptyList() : Collections.unmodifiableList(Collections.unmodifiableList(new ArrayList<>(warnings)));
    }

    public ExperimentReport(
            ExperimentId runId,
            long startTimeEpochMs,
            long endTimeEpochMs,
            String status,
            ExperimentSpec spec,
            EnvironmentFingerprint environment,
            List<Checkpoint> checkpoints,
            DetectionResult detection,
            String canonicalCommand,
            List<String> warnings
    ) {
        this(runId, startTimeEpochMs, endTimeEpochMs, status, spec, environment, checkpoints, detection, DiagnosticRefs.EMPTY, canonicalCommand, warnings);
    }

    public long durationMs() {
        return Math.max(0, endTimeEpochMs - startTimeEpochMs);
    }
}
