package com.dwurdy.heaphammer.diagnostics;

import com.github.bsideup.jabel.Desugar;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/** Read-only entity-region metrics for one dimension. */
@Desugar
public record WorldStoreDimensionSnapshot(
        String dimension,
        long regionFileCount,
        long regionBytes,
        long entityCount,
        long persistentEntityCount,
        long itemEntityCount,
        long testEntityCount,
        long testItemEntityCount,
        Map<String, Long> itemAgeBuckets
) {
    public WorldStoreDimensionSnapshot {
        Objects.requireNonNull(dimension, "dimension must not be null");
        if (regionFileCount < 0L || regionBytes < 0L || entityCount < 0L || persistentEntityCount < 0L
                || itemEntityCount < 0L || testEntityCount < 0L || testItemEntityCount < 0L) {
            throw new IllegalArgumentException("world-store counts must not be negative");
        }
        itemAgeBuckets = itemAgeBuckets == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(itemAgeBuckets);
    }
}
