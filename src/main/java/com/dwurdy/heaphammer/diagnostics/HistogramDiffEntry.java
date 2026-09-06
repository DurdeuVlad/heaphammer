package com.dwurdy.heaphammer.diagnostics;

import java.util.Objects;

/**
 * Difference between two histogram captures for a specific class.
 */
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
