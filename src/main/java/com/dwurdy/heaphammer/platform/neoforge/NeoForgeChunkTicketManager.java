package com.dwurdy.heaphammer.platform.neoforge;

import com.dwurdy.heaphammer.platform.ChunkTicketManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NeoForge 1.21.x implementation of ChunkTicketManager using NeoForgeTicketBridge.
 * Operates strictly with primitive coordinate keys, never holding live LevelChunk references.
 */
public class NeoForgeChunkTicketManager implements ChunkTicketManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("heaphammer-neoforge-tickets");

    private final NeoForgeTicketBridge bridge;
    private final Map<String, Set<Long>> activeTicketsByDimension = new ConcurrentHashMap<>();

    public NeoForgeChunkTicketManager(NeoForgeTicketBridge bridge) {
        this.bridge = Objects.requireNonNull(bridge, "bridge must not be null");
    }

    public static long packChunkPos(int x, int z) {
        return ((long) x & 0xFFFFFFFFL) | (((long) z & 0xFFFFFFFFL) << 32);
    }

    public static int unpackChunkX(long packed) {
        return (int) (packed & 0xFFFFFFFFL);
    }

    public static int unpackChunkZ(long packed) {
        return (int) ((packed >>> 32) & 0xFFFFFFFFL);
    }

    @Override
    public boolean acquireTicket(String dimension, int chunkX, int chunkZ) {
        if (!bridge.isDimensionLoaded(dimension)) {
            LOGGER.warn("Cannot acquire NeoForge ticket: dimension '{}' is not loaded", dimension);
            return false;
        }

        long key = packChunkPos(chunkX, chunkZ);
        Set<Long> dimTickets = activeTicketsByDimension.computeIfAbsent(dimension, k -> ConcurrentHashMap.newKeySet());
        if (dimTickets.contains(key)) {
            return true; // Already acquired
        }

        boolean added = bridge.addRegionTicket(dimension, chunkX, chunkZ);
        if (!added) {
            LOGGER.warn("NeoForge ticket registration failed in dimension '{}' for ({}, {})", dimension, chunkX, chunkZ);
            return false;
        }

        dimTickets.add(key);
        return true;
    }

    @Override
    public boolean releaseTicket(String dimension, int chunkX, int chunkZ) {
        Set<Long> dimTickets = activeTicketsByDimension.get(dimension);
        if (dimTickets == null) return false;

        long key = packChunkPos(chunkX, chunkZ);
        if (!dimTickets.remove(key)) {
            return false;
        }

        return bridge.removeRegionTicket(dimension, chunkX, chunkZ);
    }

    @Override
    public int getActiveTicketCount() {
        return activeTicketsByDimension.values().stream().mapToInt(Set::size).sum();
    }

    @Override
    public Set<Long> getActiveTicketChunkKeys(String dimension) {
        Set<Long> dimTickets = activeTicketsByDimension.get(dimension);
        return dimTickets == null ? Collections.emptySet() : Collections.unmodifiableSet(new HashSet<>(dimTickets));
    }

    @Override
    public void releaseAllTickets() {
        for (Map.Entry<String, Set<Long>> entry : activeTicketsByDimension.entrySet()) {
            String dimension = entry.getKey();
            Set<Long> keys = entry.getValue();
            for (Long key : keys) {
                bridge.removeRegionTicket(dimension, unpackChunkX(key), unpackChunkZ(key));
            }
            keys.clear();
        }
        activeTicketsByDimension.clear();
        LOGGER.info("All NeoForge chunk tickets released successfully.");
    }

    public NeoForgeTicketBridge getBridge() {
        return bridge;
    }
}
