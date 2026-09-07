package com.dwurdy.heaphammer.platform.fabric;

import com.dwurdy.heaphammer.domain.EnvironmentFingerprint;
import com.dwurdy.heaphammer.platform.ChunkTicketManager;
import com.dwurdy.heaphammer.platform.PlatformAdapter;
import com.google.common.collect.Iterables;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;


/**
 * Platform adapter implementing Minecraft 1.21.1 and Fabric Loader integration.
 */
public class FabricPlatformAdapter implements PlatformAdapter {
    private final Supplier<MinecraftServer> serverSupplier;
    private final FabricChunkTicketManager ticketManager;
    private final List<Consumer<Long>> tickListeners = new CopyOnWriteArrayList<>();
    private long serverTickCounter = 0;

    public FabricPlatformAdapter(Supplier<MinecraftServer> serverSupplier) {
        this.serverSupplier = Objects.requireNonNull(serverSupplier, "serverSupplier must not be null");
        this.ticketManager = new FabricChunkTicketManager(serverSupplier);

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server == serverSupplier.get()) {
                serverTickCounter++;
                for (Consumer<Long> listener : tickListeners) {
                    try {
                        listener.accept(serverTickCounter);
                    } catch (Exception e) {
                        // Suppress listener failure from crashing the server tick
                    }
                }
            }
        });
    }

    @Override
    public ChunkTicketManager getChunkTicketManager() {
        return ticketManager;
    }

    @Override
    public EnvironmentFingerprint captureFingerprint() {
        String heapHammerVersion = FabricLoader.getInstance()
                .getModContainer("heaphammer")
                .map(m -> m.getMetadata().getVersion().getFriendlyString())
                .orElse("1.0.0");

        String mcVersion = FabricLoader.getInstance()
                .getModContainer("minecraft")
                .map(m -> m.getMetadata().getVersion().getFriendlyString())
                .orElse("1.21.1");

        String loaderVersion = FabricLoader.getInstance()
                .getModContainer("fabricloader")
                .map(m -> m.getMetadata().getVersion().getFriendlyString())
                .orElse("0.19.5");

        String javaVersion = System.getProperty("java.version", "21");

        MinecraftServer server = serverSupplier.get();
        long worldSeed = 0L;
        if (server != null && server.getWorldData() != null && server.getWorldData().worldGenOptions() != null) {
            worldSeed = server.getWorldData().worldGenOptions().seed();
        }

        Map<String, String> mods = new TreeMap<>();
        for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
            mods.put(container.getMetadata().getId(), container.getMetadata().getVersion().getFriendlyString());
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

    @Override
    public int getLoadedChunkCount(String dimension) {
        ServerLevel level = getLevel(dimension);
        return level != null ? level.getChunkSource().getLoadedChunksCount() : 0;
    }

    @Override
    public int getTotalLoadedChunkCount() {
        MinecraftServer server = serverSupplier.get();
        if (server == null) return 0;
        int total = 0;
        for (ServerLevel level : server.getAllLevels()) {
            total += level.getChunkSource().getLoadedChunksCount();
        }
        return total;
    }

    @Override
    public int getActiveEntityCount(String dimension) {
        ServerLevel level = getLevel(dimension);
        if (level == null) return 0;
        return Iterables.size(level.getAllEntities());
    }

    @Override
    public boolean isDimensionAvailable(String dimension) {
        return getLevel(dimension) != null;
    }

    @Override
    public void registerServerTickHook(Consumer<Long> tickConsumer) {
        tickListeners.add(Objects.requireNonNull(tickConsumer, "tickConsumer must not be null"));
    }

    @Override
    public boolean isServerReady() {
        MinecraftServer server = serverSupplier.get();
        return server != null && server.isRunning();
    }

    private final Map<String, Set<UUID>> testEntitiesByDimension = new ConcurrentHashMap<>();
    private final Map<String, Set<BlockPos>> testBlockEntitiesByDimension = new ConcurrentHashMap<>();

    @Override
    public List<String> getAvailableEntityTypes() {
        List<String> types = new ArrayList<>();
        for (ResourceLocation key : BuiltInRegistries.ENTITY_TYPE.keySet()) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(key);
            if (type != null && type.canSummon() && type != EntityType.PLAYER) {
                types.add(key.toString());
            }
        }
        Collections.sort(types);
        return types;
    }

    @Override
    public UUID spawnEntity(String dimension, String entityTypeId, double x, double y, double z) {
        ServerLevel level = getLevel(dimension);
        if (level == null) return null;

        ResourceLocation loc = ResourceLocation.tryParse(entityTypeId);
        if (loc == null) return null;

        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(loc);
        if (type == null || !type.canSummon() || type == EntityType.PLAYER) {
            return null;
        }

        Entity entity = type.create(level);
        if (entity == null) return null;

        entity.moveTo(x, y, z, 0.0f, 0.0f);
        if (entity instanceof Mob mob) {
            mob.setNoAi(true);
            mob.setPersistenceRequired();
        }

        boolean added = level.addFreshEntity(entity);
        if (!added) return null;

        UUID uuid = entity.getUUID();
        testEntitiesByDimension.computeIfAbsent(dimension, k -> ConcurrentHashMap.newKeySet()).add(uuid);
        return uuid;
    }

    @Override
    public boolean removeEntity(String dimension, UUID entityUuid, String removeMode) {
        ServerLevel level = getLevel(dimension);
        if (level == null || entityUuid == null) return false;

        Set<UUID> set = testEntitiesByDimension.get(dimension);
        if (set != null) {
            set.remove(entityUuid);
        }

        Entity entity = level.getEntity(entityUuid);
        if (entity == null) return false;

        if ("KILL".equalsIgnoreCase(removeMode)) {
            entity.kill();
        } else {
            entity.discard();
        }
        return true;
    }

    @Override
    public int removeAllTestEntities(String dimension) {
        ServerLevel level = getLevel(dimension);
        if (level == null) return 0;

        Set<UUID> set = testEntitiesByDimension.remove(dimension);
        if (set == null || set.isEmpty()) return 0;

        int count = 0;
        for (UUID uuid : set) {
            Entity entity = level.getEntity(uuid);
            if (entity != null) {
                entity.discard();
                count++;
            }
        }
        return count;
    }

    @Override
    public List<String> getAvailableBlockEntityTypes() {
        List<String> types = new ArrayList<>();
        for (ResourceLocation key : BuiltInRegistries.BLOCK_ENTITY_TYPE.keySet()) {
            types.add(key.toString());
        }
        Collections.sort(types);
        return types;
    }

    @Override
    public boolean placeBlockEntity(String dimension, String blockEntityTypeId, int x, int y, int z) {
        ServerLevel level = getLevel(dimension);
        if (level == null) return false;

        ResourceLocation loc = ResourceLocation.tryParse(blockEntityTypeId);
        if (loc == null) return false;

        BlockEntityType<?> type = BuiltInRegistries.BLOCK_ENTITY_TYPE.get(loc);
        if (type == null) return false;

        // Find a valid block state for this block entity type
        BlockState validState = null;
        for (net.minecraft.world.level.block.Block block : BuiltInRegistries.BLOCK) {
            BlockState defaultState = block.defaultBlockState();
            if (type.isValid(defaultState)) {
                validState = defaultState;
                break;
            }
        }

        if (validState == null) {
            return false;
        }

        BlockPos pos = new BlockPos(x, y, z);
        boolean placed = level.setBlockAndUpdate(pos, validState);
        if (placed) {
            testBlockEntitiesByDimension.computeIfAbsent(dimension, k -> ConcurrentHashMap.newKeySet()).add(pos);
        }
        return placed;
    }

    @Override
    public boolean removeBlockEntity(String dimension, int x, int y, int z) {
        ServerLevel level = getLevel(dimension);
        if (level == null) return false;

        BlockPos pos = new BlockPos(x, y, z);
        Set<BlockPos> set = testBlockEntitiesByDimension.get(dimension);
        if (set != null) {
            set.remove(pos);
        }

        return level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    }

    @Override
    public int removeAllTestBlockEntities(String dimension) {
        ServerLevel level = getLevel(dimension);
        if (level == null) return 0;

        Set<BlockPos> set = testBlockEntitiesByDimension.remove(dimension);
        if (set == null || set.isEmpty()) return 0;

        int count = 0;
        for (BlockPos pos : set) {
            if (level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState())) {
                count++;
            }
        }
        return count;
    }

    private ServerLevel getLevel(String dimension) {
        MinecraftServer server = serverSupplier.get();
        if (server == null) return null;

        ResourceLocation loc = ResourceLocation.tryParse(dimension);
        if (loc == null) return null;

        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, loc);
        return server.getLevel(key);
    }
}
