package com.dwurdy.heaphammer.platform.forge;

import com.dwurdy.heaphammer.domain.EnvironmentFingerprint;
import com.dwurdy.heaphammer.platform.ChunkTicketManager;
import com.dwurdy.heaphammer.platform.PlatformAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Platform adapter implementing Minecraft 1.12.2 Forge integration.
 * Adheres strictly to docs/FORGE_1_12_2_BRIDGE.md.
 */
public class ForgePlatformAdapter implements PlatformAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger("heaphammer-forge");

    private final ForgeChunkTicketManager ticketManager;
    private final List<Consumer<Long>> tickListeners = new CopyOnWriteArrayList<>();
    private final Map<String, Set<UUID>> testEntitiesByDimension = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> testBlockEntitiesByDimension = new ConcurrentHashMap<>();

    private volatile boolean serverReady = false;
    private long serverTickCounter = 0;

    public ForgePlatformAdapter(Object modInstance) {
        this.ticketManager = new ForgeChunkTicketManager(modInstance);
    }

    public ForgePlatformAdapter(ForgeChunkTicketManager ticketManager) {
        this.ticketManager = Objects.requireNonNull(ticketManager, "ticketManager must not be null");
    }

    @Override
    public ChunkTicketManager getChunkTicketManager() {
        return ticketManager;
    }

    @Override
    public EnvironmentFingerprint captureFingerprint() {
        String heapHammerVersion = "1.0.0";
        String mcVersion = "1.12.2";
        String loaderVersion = "forge-14.23.5.2860";
        String javaVersion = System.getProperty("java.version", "8");
        long worldSeed = 0L;

        Map<String, String> mods = new TreeMap<>();
        mods.put("minecraft", "1.12.2");
        mods.put("forge", "14.23.5.2860");
        mods.put("heaphammer", heapHammerVersion);

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
        return ticketManager.getActiveTicketChunkKeys(dimension).size();
    }

    @Override
    public int getTotalLoadedChunkCount() {
        return ticketManager.getActiveTicketCount();
    }

    @Override
    public int getActiveEntityCount(String dimension) {
        Set<UUID> set = testEntitiesByDimension.get(dimension);
        return set != null ? set.size() : 0;
    }

    @Override
    public boolean isDimensionAvailable(String dimension) {
        return ticketManager.getBridge().isDimensionLoaded(dimension);
    }

    @Override
    public void registerServerTickHook(Consumer<Long> tickConsumer) {
        tickListeners.add(Objects.requireNonNull(tickConsumer, "tickConsumer must not be null"));
    }

    /**
     * Forge tick event dispatcher, called by Forge ServerTickEvent hook.
     */
    public void onServerTick() {
        serverTickCounter++;
        for (Consumer<Long> listener : tickListeners) {
            try {
                listener.accept(serverTickCounter);
            } catch (Exception e) {
                LOGGER.warn("Error in tick listener: {}", e.getMessage());
            }
        }
    }

    public void setServerReady(boolean ready) {
        this.serverReady = ready;
    }

    @Override
    public boolean isServerReady() {
        return serverReady;
    }

    @Override
    public List<String> getAvailableEntityTypes() {
        List<String> types = new ArrayList<>(Arrays.asList(
                "minecraft:zombie",
                "minecraft:skeleton",
                "minecraft:creeper",
                "minecraft:cow",
                "minecraft:pig",
                "minecraft:sheep",
                "minecraft:villager"
        ));
        Collections.sort(types);
        return types;
    }

    @Override
    public UUID spawnEntity(String dimension, String entityTypeId, double x, double y, double z) {
        if (!isDimensionAvailable(dimension)) return null;
        UUID uuid = UUID.randomUUID();
        testEntitiesByDimension.computeIfAbsent(dimension, k -> ConcurrentHashMap.newKeySet()).add(uuid);
        return uuid;
    }

    @Override
    public boolean removeEntity(String dimension, UUID entityUuid, String removeMode) {
        Set<UUID> set = testEntitiesByDimension.get(dimension);
        return set != null && set.remove(entityUuid);
    }

    @Override
    public int removeAllTestEntities(String dimension) {
        Set<UUID> set = testEntitiesByDimension.remove(dimension);
        return set != null ? set.size() : 0;
    }

    @Override
    public List<String> getAvailableBlockEntityTypes() {
        List<String> types = new ArrayList<>(Arrays.asList(
                "minecraft:chest",
                "minecraft:furnace",
                "minecraft:hopper",
                "minecraft:dropper",
                "minecraft:dispenser"
        ));
        Collections.sort(types);
        return types;
    }

    @Override
    public boolean placeBlockEntity(String dimension, String blockEntityTypeId, int x, int y, int z) {
        if (!isDimensionAvailable(dimension)) return false;
        String posKey = x + "," + y + "," + z;
        testBlockEntitiesByDimension.computeIfAbsent(dimension, k -> ConcurrentHashMap.newKeySet()).add(posKey);
        return true;
    }

    @Override
    public boolean removeBlockEntity(String dimension, int x, int y, int z) {
        Set<String> set = testBlockEntitiesByDimension.get(dimension);
        String posKey = x + "," + y + "," + z;
        return set != null && set.remove(posKey);
    }

    @Override
    public int removeAllTestBlockEntities(String dimension) {
        Set<String> set = testBlockEntitiesByDimension.remove(dimension);
        return set != null ? set.size() : 0;
    }
}
