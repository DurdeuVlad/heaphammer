package com.dwurdy.testmod.omnitrack;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.UUID;

/**
 * Tracking record holding hard reference to an active or spawned Entity.
 */
public class EntityTrackingRecord {
    private final UUID entityUuid;
    private final String entityTypeName;
    private final ResourceKey<Level> dimension;
    private final Entity entity;
    private final long loadTimestamp;

    public EntityTrackingRecord(Entity entity, ResourceKey<Level> dimension) {
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

    public ResourceKey<Level> getDimension() {
        return dimension;
    }

    public Entity getEntity() {
        return entity;
    }

    public long getLoadTimestamp() {
        return loadTimestamp;
    }
}
