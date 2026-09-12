package com.dwurdy.heaphammer.platform.forge1710;

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
 * Platform adapter implementing Minecraft 1.7.10 Forge integration.
 * Quarantines 1.7.10 legacy API conventions (primitive coordinates, ChunkCoordIntPair,
 * pre-flattening registry blocks) behind pure hexagonal ports.
 */
public class ForgePlatformAdapter1710 implements PlatformAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger("heaphammer-forge1710");

    private final ForgeChunkTicketManager1710 ticketManager;
    private final List<Consumer<Long>> tickListeners = new CopyOnWriteArrayList<>();
    private final Map<String, Set<UUID>> testEntitiesByDimension = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> testBlockEntitiesByDimension = new ConcurrentHashMap<>();

    private volatile boolean serverReady = false;
    private long serverTickCounter = 0;

    public ForgePlatformAdapter1710(Object modInstance) {
        this.ticketManager = new ForgeChunkTicketManager1710(modInstance);
    }

    public ForgePlatformAdapter1710(ForgeChunkTicketManager1710 ticketManager) {
        this.ticketManager = Objects.requireNonNull(ticketManager, "ticketManager must not be null");
    }

    /**
     * Reads the build-time HeapHammer version filtered into
     * heaphammer-version.properties so it can never drift from mod_version.
     */
    private static String heapHammerVersion() {
        Properties props = new Properties();
        try (java.io.InputStream in = ForgePlatformAdapter1710.class
                .getResourceAsStream("/heaphammer-version.properties")) {
            if (in != null) {
                props.load(in);
                String v = props.getProperty("mod_version");
                if (v != null && !v.trim().isEmpty() && !v.contains("${")) {
                    return v.trim();
                }
            }
        } catch (java.io.IOException ignored) {
        }
        return "dev";
    }

    @Override
    public ChunkTicketManager getChunkTicketManager() {
        return ticketManager;
    }

    @Override
    public EnvironmentFingerprint captureFingerprint() {
        String heapHammerVersion = heapHammerVersion();
        String mcVersion = "1.7.10";
        String loaderVersion = "forge-10.13.4.1614";
        String javaVersion = System.getProperty("java.version", "8");
        long worldSeed = 0L;

        Map<String, String> mods = new TreeMap<>();
        mods.put("minecraft", "1.7.10");
        mods.put("forge", "10.13.4.1614");
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
     * Forge 1.7.10 tick dispatcher, hooked from FML TickEvent.ServerTickEvent.
     */
    public void onServerTick() {
        serverTickCounter++;
        for (Consumer<Long> listener : tickListeners) {
            try {
                listener.accept(serverTickCounter);
            } catch (Exception e) {
                LOGGER.warn("Error in 1.7.10 tick listener: {}", e.getMessage());
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
