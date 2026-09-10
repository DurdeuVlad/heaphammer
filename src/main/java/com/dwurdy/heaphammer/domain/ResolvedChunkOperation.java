package com.dwurdy.heaphammer.domain;

import com.github.bsideup.jabel.Desugar;

import java.util.Objects;

/**
 * An individual resolved chunk operation in an experiment plan.
 */
@Desugar
public record ResolvedChunkOperation(
        int iteration,
        int stepIndex,
        String dimension,
        int chunkX,
        int chunkZ,
        String action
) {
    public static final String ACTION_ACQUIRE = "ACQUIRE";
    public static final String ACTION_RELEASE = "RELEASE";

    public ResolvedChunkOperation {
        Objects.requireNonNull(dimension, "dimension must not be null");
        Objects.requireNonNull(action, "action must not be null");
    }

    public long chunkPosKey() {
        return (((long) chunkX) & 0xFFFFFFFFL) | ((((long) chunkZ) & 0xFFFFFFFFL) << 32);
    }
}
