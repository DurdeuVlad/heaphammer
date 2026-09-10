package com.dwurdy.heaphammer.report;

import java.util.ArrayList;


import com.github.bsideup.jabel.Desugar;

import com.dwurdy.heaphammer.domain.DetectionClassification;
import com.dwurdy.heaphammer.domain.ExperimentId;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Comparative differential analysis between two experiment reports (Section 27).
 */
@Desugar
public record ReportDiff(
        ExperimentId runA,
        ExperimentId runB,
        boolean environmentMatched,
        String environmentComparison,
        long initialHeapA,
        long initialHeapB,
        long finalHeapA,
        long finalHeapB,
        long netDeltaA,
        long netDeltaB,
        long netDeltaDifferenceBytes,
        double slopeA,
        double slopeB,
        double slopeDifferenceBytes,
        DetectionClassification classificationA,
        DetectionClassification classificationB,
        boolean classificationChanged,
        String summary,
        List<String> warnings
) {
    public ReportDiff {
        Objects.requireNonNull(runA, "runA must not be null");
        Objects.requireNonNull(runB, "runB must not be null");
        Objects.requireNonNull(classificationA, "classificationA must not be null");
        Objects.requireNonNull(classificationB, "classificationB must not be null");
        Objects.requireNonNull(summary, "summary must not be null");
        warnings = (warnings == null) ? Collections.emptyList() : Collections.unmodifiableList(Collections.unmodifiableList(new ArrayList<>(warnings)));
    }

    public double netDeltaDiffMb() {
        return netDeltaDifferenceBytes / (1024.0 * 1024.0);
    }

    public double slopeDiffMb() {
        return slopeDifferenceBytes / (1024.0 * 1024.0);
    }
}
