package com.dwurdy.heaphammer.diagnostics;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Difference between two persisted entity-store snapshots. */
public record WorldStoreDelta(long timestamp, Map<String, WorldStoreDimensionDelta> dimensions) {
    public WorldStoreDelta {
        Objects.requireNonNull(dimensions, "dimensions must not be null");
        dimensions = Collections.unmodifiableMap(new LinkedHashMap<>(dimensions));
    }
}
