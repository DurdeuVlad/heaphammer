package com.dwurdy.heaphammer.diagnostics;

import java.util.Objects;

/**
 * A single class histogram entry.
 */
public record ClassHistogramEntry(
        int rank,
        String className,
        long instances,
        long bytes
) {
    public ClassHistogramEntry {
        Objects.requireNonNull(className, "className must not be null");
    }

    public double bytesMb() {
        return bytes / (1024.0 * 1024.0);
    }
}
