package com.dwurdy.heaphammer.platform.forge;

import com.dwurdy.heaphammer.domain.EnvironmentFingerprint;
import com.google.common.collect.Iterables;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Production MinecraftForge 1.16.5 platform adapter backed by a live
 * {@link MinecraftServer}. Extends the headless {@link ForgePlatformAdapter} (kept
 * as the unit-testable base) and overrides every operation with real server
 * behavior while preserving the zero-leaked-reference invariant: only UUIDs,
 * {@link BlockPos} values and packed chunk keys are retained.
 */
public class DirectForgePlatformAdapter extends ForgePlatformAdapter {
    public static final String TEST_ENTITY_TAG = "heaphammer:test";

    private final Supplier<MinecraftServer> serverSupplier;
    private final Map<String, Set<UUID>> liveTestEntitiesByDimension = new ConcurrentHashMap<>();
    private final Map<String, Set<BlockPos>> liveTestBlockEntitiesByDimension = new ConcurrentHashMap<>();

    public DirectForgePlatformAdapter(ForgeChunkTicketManager ticketManager,
                                      Supplier<MinecraftServer> serverSupplier) {
        super(ticketManager);
        this.serverSupplier = Objects.requireNonNull(serverSupplier, "serverSupplier must not be null");
    }

    @Override
    public EnvironmentFingerprint captureFingerprint() {
        String heapHammerVersion = modVersion("heaphammer", "1.0.2");
        String mcVersion = modVersion("minecraft", "1.16.5");
        String loaderVersion = "forge-" + modVersion("forge", "36.2.42");
        String javaVersion = System.getProperty("java.version", "17");

        MinecraftServer server = serverSupplier.get();
        long worldSeed = 0L;
        if (server != null && server.getWorldData() != null && server.getWorldData().worldGenSettings() != null) {
            worldSeed = server.getWorldData().worldGenSettings().seed();
        }

        Map<String, String> mods = new TreeMap<>();
        ModList modList = ModList.get();
        if (modList != null) {
            for (IModInfo info : modList.getMods()) {
                mods.put(info.getModId(), info.getVersion().toString());
            }
        }

        String modpackHash = Integer.toHexString(mods.hashCode());

        return new EnvironmentFingerprint(
                heapHammerVersion,
                mcVersion,
                loaderVersion,
                javaVersion,
                worldSeed,
                modpackHash,
                mods
        );
    }

    private static String modVersion(String modId, String fallback) {
        ModList modList = ModList.get();
        if (modList == null) return fallback;
        return modList.getModContainerById(modId)
                .map(c -> c.getModInfo().getVersion().toString())
                .orElse(fallback);
    }

    @Override
    public int getLoadedChunkCount(String dimension) {
        ServerWorld level = getLevel(dimension);
        return level != null ? level.getChunkSource().getLoadedChunksCount() : 0;
    }

    @Override
    public int getTotalLoadedChunkCount() {
        MinecraftServer server = serverSupplier.get();
        if (server == null) return 0;
        int total = 0;
        for (ServerWorld level : server.getAllLevels()) {
            total += level.getChunkSource().getLoadedChunksCount();
        }
        return total;
    }

    @Override
    public int getActiveEntityCount(String dimension) {
        ServerWorld level = getLevel(dimension);
        if (level == null) return 0;
        return Iterables.size(level.getAllEntities());
    }

    @Override
    public boolean isServerReady() {
        MinecraftServer server = serverSupplier.get();
        return server != null && server.isRunning();
    }

    @Override
    public List<String> getAvailableEntityTypes() {
        List<String> types = new ArrayList<>();
        for (ResourceLocation key : Registry.ENTITY_TYPE.keySet()) {
            EntityType<?> type = Registry.ENTITY_TYPE.get(key);
            if (type != null && type.canSummon() && type != EntityType.PLAYER) {
                types.add(key.toString());
            }
        }
        Collections.sort(types);
        return types;
    }

    @Override
    public UUID spawnEntity(String dimension, String entityTypeId, double x, double y, double z) {
        ServerWorld level = getLevel(dimension);
        if (level == null) return null;

        ResourceLocation loc = ResourceLocation.tryParse(entityTypeId);
        if (loc == null) return null;

        EntityType<?> type = Registry.ENTITY_TYPE.get(loc);
        if (type == null || !type.canSummon() || type == EntityType.PLAYER) {
            return null;
        }

        Entity entity = type.create(level);
        if (entity == null) return null;

        entity.moveTo(x, y, z, 0.0f, 0.0f);
        if (entity instanceof MobEntity) {
            MobEntity mob = (MobEntity) entity;
            mob.setNoAi(true);
            mob.setPersistenceRequired();
        }
        entity.addTag(TEST_ENTITY_TAG);

        boolean added = level.addFreshEntity(entity);
        if (!added) return null;

        UUID uuid = entity.getUUID();
        liveTestEntitiesByDimension.computeIfAbsent(dimension, k -> ConcurrentHashMap.newKeySet()).add(uuid);
        return uuid;
    }

    @Override
    public boolean removeEntity(String dimension, UUID entityUuid, String removeMode) {
        ServerWorld level = getLevel(dimension);
        if (level == null || entityUuid == null) return false;

        Set<UUID> set = liveTestEntitiesByDimension.get(dimension);
        if (set != null) {
            set.remove(entityUuid);
        }

        Entity entity = level.getEntity(entityUuid);
        if (entity == null) return false;

        if ("KILL".equalsIgnoreCase(removeMode)) {
            entity.kill();
        } else {
            entity.remove();
        }
        return true;
    }

    @Override
    public int removeAllTestEntities(String dimension) {
        ServerWorld level = getLevel(dimension);
        if (level == null) return 0;

        Set<UUID> set = liveTestEntitiesByDimension.remove(dimension);
        if (set == null || set.isEmpty()) return 0;

        int count = 0;
        for (UUID uuid : set) {
            Entity entity = level.getEntity(uuid);
            if (entity != null) {
                entity.remove();
                count++;
            }
        }
        return count;
    }

    @Override
    public List<String> getAvailableBlockEntityTypes() {
        List<String> types = new ArrayList<>();
        for (ResourceLocation key : Registry.BLOCK_ENTITY_TYPE.keySet()) {
            types.add(key.toString());
        }
        Collections.sort(types);
        return types;
    }

    @Override
    public boolean placeBlockEntity(String dimension, String blockEntityTypeId, int x, int y, int z) {
        ServerWorld level = getLevel(dimension);
        if (level == null) return false;

        ResourceLocation loc = ResourceLocation.tryParse(blockEntityTypeId);
        if (loc == null) return false;

        TileEntityType<?> type = Registry.BLOCK_ENTITY_TYPE.get(loc);
        if (type == null) return false;

        // Find a valid block state for this block entity type
        BlockState validState = null;
        for (Block block : Registry.BLOCK) {
            if (type.isValid(block)) {
                validState = block.defaultBlockState();
                break;
            }
        }

        if (validState == null) {
            return false;
        }

        BlockPos pos = new BlockPos(x, y, z);
        boolean placed = level.setBlockAndUpdate(pos, validState);
        if (placed) {
            liveTestBlockEntitiesByDimension.computeIfAbsent(dimension, k -> ConcurrentHashMap.newKeySet()).add(pos);
        }
        return placed;
    }

    @Override
    public boolean removeBlockEntity(String dimension, int x, int y, int z) {
        ServerWorld level = getLevel(dimension);
        if (level == null) return false;

        BlockPos pos = new BlockPos(x, y, z);
        Set<BlockPos> set = liveTestBlockEntitiesByDimension.get(dimension);
        if (set != null) {
            set.remove(pos);
        }

        return level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    }

    @Override
    public int removeAllTestBlockEntities(String dimension) {
        ServerWorld level = getLevel(dimension);
        if (level == null) return 0;

        Set<BlockPos> set = liveTestBlockEntitiesByDimension.remove(dimension);
        if (set == null || set.isEmpty()) return 0;

        int count = 0;
        for (BlockPos pos : set) {
            if (level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState())) {
                count++;
            }
        }
        return count;
    }

    public int cleanupOrphanedState() {
        int cleaned = 0;

        // 1. Release all chunk tickets
        getChunkTicketManager().releaseAllTickets();

        // 2. Revert any tracked block entities
        for (String dim : new ArrayList<>(liveTestBlockEntitiesByDimension.keySet())) {
            cleaned += removeAllTestBlockEntities(dim);
        }

        // 3. Discard any tracked test entities
        for (String dim : new ArrayList<>(liveTestEntitiesByDimension.keySet())) {
            cleaned += removeAllTestEntities(dim);
        }

        // 4. Sweep all server levels for any orphaned entity bearing TEST_ENTITY_TAG
        MinecraftServer server = serverSupplier.get();
        if (server != null) {
            for (ServerWorld level : server.getAllLevels()) {
                for (Entity entity : level.getAllEntities()) {
                    if (entity.getTags().contains(TEST_ENTITY_TAG)) {
                        entity.remove();
                        cleaned++;
                    }
                }
            }
        }

        return cleaned;
    }

    private ServerWorld getLevel(String dimension) {
        MinecraftServer server = serverSupplier.get();
        if (server == null) return null;

        ResourceLocation loc = ResourceLocation.tryParse(dimension);
        if (loc == null) return null;

        RegistryKey<World> key = RegistryKey.create(Registry.DIMENSION_REGISTRY, loc);
        return server.getLevel(key);
    }
}
