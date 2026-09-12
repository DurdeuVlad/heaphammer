package com.dwurdy.heaphammer.platform.neoforge;

import com.dwurdy.heaphammer.diagnostics.RetentionTracker;
import com.dwurdy.heaphammer.domain.EntityWorkloadProfile;
import com.dwurdy.heaphammer.platform.EntityLifecyclePort;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Profile-aware live entity operations for NeoForge 1.21.1. */
public final class NeoForgeEntityLifecyclePort implements EntityLifecyclePort {
    private final Supplier<MinecraftServer> serverSupplier;
    private final ConcurrentHashMap<String, java.util.Set<UUID>> testEntities = new ConcurrentHashMap<>();
    private volatile RetentionTracker retentionTracker;

    public NeoForgeEntityLifecyclePort(Supplier<MinecraftServer> serverSupplier) {
        this.serverSupplier = serverSupplier;
    }

    public void attach(RetentionTracker tracker) {
        retentionTracker = tracker;
    }

    @Override
    public UUID spawn(String dimension, String entityTypeId, double x, double y, double z, EntityWorkloadProfile profile) {
        MinecraftServer server = serverSupplier.get();
        ServerLevel level = server == null ? null : getLevel(server, dimension);
        if (level == null) return null;
        ResourceLocation location = ResourceLocation.tryParse(entityTypeId);
        EntityType<?> type = location == null ? null : BuiltInRegistries.ENTITY_TYPE.get(location);
        if (type == null || type == EntityType.PLAYER || !type.canSummon()) return null;
        Entity entity = type.create(level);
        if (entity == null) return null;
        entity.moveTo(x, y, z, 0.0F, 0.0F);
        if (entity instanceof Mob mob) {
            mob.setNoAi(true);
            if (profile == EntityWorkloadProfile.PERSISTENT) mob.setPersistenceRequired();
        }
        entity.addTag(DirectNeoForgePlatformAdapter.TEST_ENTITY_TAG);
        if (!level.addFreshEntity(entity)) return null;
        testEntities.computeIfAbsent(dimension, ignored -> ConcurrentHashMap.newKeySet()).add(entity.getUUID());
        RetentionTracker tracker = retentionTracker;
        if (tracker != null) tracker.observe(entity.getClass().getName(), entity);
        return entity.getUUID();
    }

    @Override
    public boolean cycleChunk(String dimension, int chunkX, int chunkZ, EntityWorkloadProfile profile) {
        return false;
    }

    @Override
    public long countMaterializedTestEntities(String dimension) {
        MinecraftServer server = serverSupplier.get();
        ServerLevel level = server == null ? null : getLevel(server, dimension);
        if (level == null) return 0L;
        long count = 0L;
        for (Entity entity : level.getAllEntities()) if (entity.getTags().contains(DirectNeoForgePlatformAdapter.TEST_ENTITY_TAG)) count++;
        return count;
    }

    @Override
    public int cleanupTestEntities() {
        MinecraftServer server = serverSupplier.get();
        if (server == null) return 0;
        int count = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity.getTags().contains(DirectNeoForgePlatformAdapter.TEST_ENTITY_TAG)) {
                    entity.discard();
                    count++;
                }
            }
        }
        testEntities.clear();
        return count;
    }

    private static ServerLevel getLevel(MinecraftServer server, String dimension) {
        ResourceLocation location = ResourceLocation.tryParse(dimension);
        return location == null ? null : server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, location));
    }
}
