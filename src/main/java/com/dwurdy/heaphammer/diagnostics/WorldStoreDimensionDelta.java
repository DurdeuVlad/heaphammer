package com.dwurdy.heaphammer.diagnostics;

import java.util.Objects;

/** Difference between two persisted entity-store snapshots for one dimension. */
public record WorldStoreDimensionDelta(
        String dimension,
        long deltaRegionFiles,
        long deltaRegionBytes,
        long deltaEntities,
        long deltaPersistentEntities,
        long deltaItems,
        long deltaTestEntities,
        long deltaTestItems
) {
    public WorldStoreDimensionDelta {
        Objects.requireNonNull(dimension, "dimension must not be null");
    }

    public static WorldStoreDimensionDelta from(WorldStoreDimensionSnapshot current, WorldStoreDimensionSnapshot previous) {
        long previousRegions = previous == null ? 0L : previous.regionFileCount();
        long previousBytes = previous == null ? 0L : previous.regionBytes();
        long previousEntities = previous == null ? 0L : previous.entityCount();
        long previousPersistent = previous == null ? 0L : previous.persistentEntityCount();
        long previousItems = previous == null ? 0L : previous.itemEntityCount();
        long previousTestEntities = previous == null ? 0L : previous.testEntityCount();
        long previousTestItems = previous == null ? 0L : previous.testItemEntityCount();
        return new WorldStoreDimensionDelta(
                current.dimension(),
                current.regionFileCount() - previousRegions,
                current.regionBytes() - previousBytes,
                current.entityCount() - previousEntities,
                current.persistentEntityCount() - previousPersistent,
                current.itemEntityCount() - previousItems,
                current.testEntityCount() - previousTestEntities,
                current.testItemEntityCount() - previousTestItems
        );
    }
}
