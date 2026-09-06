package com.dwurdy.heaphammer.diagnostics;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Summary of differential growth between baseline and target histograms.
 */
public record HistogramDiff(
        List<HistogramDiffEntry> topGrowingClasses,
        long totalDeltaInstances,
        long totalDeltaBytes
) {
    public HistogramDiff {
        Objects.requireNonNull(topGrowingClasses, "topGrowingClasses must not be null");
        topGrowingClasses = Collections.unmodifiableList(topGrowingClasses);
    }

    public double totalDeltaBytesMb() {
        return totalDeltaBytes / (1024.0 * 1024.0);
    }
}
