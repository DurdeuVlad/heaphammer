package com.dwurdy.heaphammer.domain;

/**
 * Metric readings sampled at a specific instant in time.
 */
public record MetricSample(
        long timestampEpochMs,
        long tick,
        long heapUsedBytes,
        long heapCommittedBytes,
        long heapMaxBytes,
        long nonHeapUsedBytes,
        int loadedChunks,
        int activeEntities,
        long gcCount,
        long gcTimeMs
) {
    public double heapUsedMb() {
        return heapUsedBytes / (1024.0 * 1024.0);
    }
}
