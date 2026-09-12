package com.dwurdy.testmod.omnitrack;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.dimension.DimensionType;

import java.util.Objects;
import java.util.UUID;

/**
 * Tracking record holding hard reference to an active or spawned Entity.
 */
public class EntityTrackingRecord {
    private final UUID entityUuid;
    private final String entityTypeName;
    private final DimensionType dimension;
    private final Entity entity;
    private final long loadTimestamp;
    private final byte[] pathfindingCache = new byte[512 * 1024];

    public EntityTrackingRecord(Entity entity, DimensionType dimension) {
        this.entity = Objects.requireNonNull(entity, "entity must not be null");
        this.entityUuid = entity.getUUID();
        this.entityTypeName = entity.getType().getDescriptionId();
        this.dimension = Objects.requireNonNull(dimension, "dimension must not be null");
        this.loadTimestamp = System.currentTimeMillis();
    }

    public UUID getEntityUuid() {
        return entityUuid;
    }

    public String getEntityTypeName() {
        return entityTypeName;
    }

    public DimensionType getDimension() {
        return dimension;
    }

    public Entity getEntity() {
        return entity;
    }

    public long getLoadTimestamp() {
        return loadTimestamp;
    }
}
