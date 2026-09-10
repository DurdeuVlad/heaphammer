package com.dwurdy.heaphammer.diagnostics;

import com.github.bsideup.jabel.Desugar;

import java.util.Objects;

/**
 * Difference between two histogram captures for a specific class.
 */
@Desugar
public record HistogramDiffEntry(
        String className,
        long deltaInstances,
        long deltaBytes
) {
    public HistogramDiffEntry {
        Objects.requireNonNull(className, "className must not be null");
    }

    public double deltaBytesMb() {
        return deltaBytes / (1024.0 * 1024.0);
    }
}
