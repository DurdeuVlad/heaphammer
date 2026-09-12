package com.dwurdy.heaphammer.diagnostics;

import com.github.bsideup.jabel.Desugar;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Read-only snapshot of persisted entity stores across dimensions. */
@Desugar
public record WorldStoreSnapshot(long timestamp, Map<String, WorldStoreDimensionSnapshot> dimensions) {
    public WorldStoreSnapshot {
        Objects.requireNonNull(dimensions, "dimensions must not be null");
        dimensions = Collections.unmodifiableMap(new LinkedHashMap<>(dimensions));
    }

    public static WorldStoreSnapshot empty() {
        return new WorldStoreSnapshot(System.currentTimeMillis(), Collections.emptyMap());
    }

    public long totalRegionBytes() {
        return dimensions.values().stream().mapToLong(WorldStoreDimensionSnapshot::regionBytes).sum();
    }

    public long totalEntityCount() {
        return dimensions.values().stream().mapToLong(WorldStoreDimensionSnapshot::entityCount).sum();
    }

    public WorldStoreDelta diff(WorldStoreSnapshot baseline) {
        Map<String, WorldStoreDimensionDelta> deltas = new LinkedHashMap<>();
        Map<String, WorldStoreDimensionSnapshot> base = baseline == null ? Collections.emptyMap() : baseline.dimensions();
        for (Map.Entry<String, WorldStoreDimensionSnapshot> current : dimensions.entrySet()) {
            WorldStoreDimensionSnapshot previous = base.get(current.getKey());
            deltas.put(current.getKey(), WorldStoreDimensionDelta.from(current.getValue(), previous));
        }
        return new WorldStoreDelta(timestamp, deltas);
    }
}
