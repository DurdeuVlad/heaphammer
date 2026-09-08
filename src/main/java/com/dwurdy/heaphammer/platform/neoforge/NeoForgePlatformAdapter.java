package com.dwurdy.heaphammer.platform.neoforge;

import com.dwurdy.heaphammer.domain.EnvironmentFingerprint;
import com.dwurdy.heaphammer.platform.ChunkTicketManager;
import com.dwurdy.heaphammer.platform.PlatformAdapter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * NeoForge 1.21.x platform adapter implementation.
 * Encapsulates NeoForge lifecycle, tick event subscriptions, and entity tracking
 * behind pure Java interfaces with zero leaked live object references.
 */
public class NeoForgePlatformAdapter implements PlatformAdapter {

    private final NeoForgeChunkTicketManager ticketManager;
    private final List<Consumer<Long>> tickListeners = new CopyOnWriteArrayList<>();
    private final AtomicLong tickCounter = new AtomicLong(0);

    // Track test entities and block entities created by HeapHammer workloads for zero-leak cleanup
    private final Map<String, Set<UUID>> testEntitiesByDimension = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> testBlockEntitiesByDimension = new ConcurrentHashMap<>();

    private final Map<String, String> installedMods = new ConcurrentHashMap<>();
    private volatile boolean serverReady = true;

    public NeoForgePlatformAdapter(NeoForgeChunkTicketManager ticketManager) {
        this.ticketManager = Objects.requireNonNull(ticketManager, "ticketManager must not be null");
        installedMods.put("minecraft", "1.21.1");
        installedMods.put("neoforge", "21.1.70");
        installedMods.put("heaphammer", "1.0.0");
    }

    @Override
    public ChunkTicketManager getChunkTicketManager() {
        return ticketManager;
    }

    @Override
    public EnvironmentFingerprint captureFingerprint() {
        return new EnvironmentFingerprint(
                "1.0.0",
                "1.21.1",
                "neoforge-21.1.70",
                System.getProperty("java.version", "21"),
                0L,
                "neoforge-default-hash",
                Collections.unmodifiableMap(new HashMap<>(installedMods))
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
        return set == null ? 0 : set.size();
    }

    @Override
    public boolean isDimensionAvailable(String dimension) {
        return ticketManager.getBridge().isDimensionLoaded(dimension);
    }

    @Override
    public void registerServerTickHook(Consumer<Long> tickConsumer) {
        if (tickConsumer != null) {
            tickListeners.add(tickConsumer);
        }
    }

    /**
     * Dispatch tick notification from NeoForge ServerTickEvent.Post to registered workload listeners.
     */
    public void onServerTick() {
        long tick = tickCounter.incrementAndGet();
        for (Consumer<Long> listener : tickListeners) {
            try {
                listener.accept(tick);
            } catch (Exception e) {
                // Prevent listener errors from crashing server tick thread
            }
        }
    }

    @Override
    public boolean isServerReady() {
        return serverReady;
    }

    public void setServerReady(boolean ready) {
        this.serverReady = ready;
    }

    @Override
    public List<String> getAvailableEntityTypes() {
        return Arrays.asList("minecraft:zombie", "minecraft:skeleton", "minecraft:cow", "minecraft:pig");
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
        if (set == null) return false;
        return set.remove(entityUuid);
    }

    @Override
    public int removeAllTestEntities(String dimension) {
        Set<UUID> set = testEntitiesByDimension.remove(dimension);
        if (set == null) return 0;
        int count = set.size();
        set.clear();
        return count;
    }

    @Override
    public List<String> getAvailableBlockEntityTypes() {
        return Arrays.asList("minecraft:chest", "minecraft:furnace", "minecraft:hopper", "minecraft:barrel");
    }

    @Override
    public boolean placeBlockEntity(String dimension, String blockEntityTypeId, int x, int y, int z) {
        if (!isDimensionAvailable(dimension)) return false;
        String coordKey = x + "," + y + "," + z;
        testBlockEntitiesByDimension.computeIfAbsent(dimension, k -> ConcurrentHashMap.newKeySet()).add(coordKey);
        return true;
    }

    @Override
    public boolean removeBlockEntity(String dimension, int x, int y, int z) {
        Set<String> set = testBlockEntitiesByDimension.get(dimension);
        if (set == null) return false;
        String coordKey = x + "," + y + "," + z;
        return set.remove(coordKey);
    }

    @Override
    public int removeAllTestBlockEntities(String dimension) {
        Set<String> set = testBlockEntitiesByDimension.remove(dimension);
        if (set == null) return 0;
        int count = set.size();
        set.clear();
        return count;
    }

    @Override
    public int cleanupOrphanedState() {
        int count = 0;
        ticketManager.releaseAllTickets();
        for (Set<UUID> entities : testEntitiesByDimension.values()) {
            count += entities.size();
            entities.clear();
        }
        testEntitiesByDimension.clear();

        for (Set<String> blocks : testBlockEntitiesByDimension.values()) {
            count += blocks.size();
            blocks.clear();
        }
        testBlockEntitiesByDimension.clear();
        return count;
    }
}
