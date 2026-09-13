package com.dwurdy.heaphammer.platform;

import com.dwurdy.heaphammer.domain.EntityWorkloadProfile;

import java.util.UUID;

/** Optional extension for persistent and loaded-but-unticked entity workloads. */
public interface EntityLifecyclePort {
    UUID spawn(String dimension, String entityTypeId, double x, double y, double z, EntityWorkloadProfile profile);

    boolean cycleChunk(String dimension, int chunkX, int chunkZ, EntityWorkloadProfile profile);

    long countMaterializedTestEntities(String dimension);

    int cleanupTestEntities();
}
