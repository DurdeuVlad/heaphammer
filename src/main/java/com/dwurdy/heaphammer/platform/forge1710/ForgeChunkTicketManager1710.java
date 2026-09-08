package com.dwurdy.heaphammer.platform.forge1710;

import com.dwurdy.heaphammer.platform.ChunkTicketManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1.7.10 Forge implementation of ChunkTicketManager using ForgeChunkManager via ForgeTicketBridge1710.
 * Enforces strict tick budgeting and leak-free chunk retention across legacy 1.7.10 servers.
 */
public class ForgeChunkTicketManager1710 implements ChunkTicketManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("heaphammer-forge1710-tickets");

    private final ForgeTicketBridge1710 bridge;
    private final Map<String, Map<Long, Object>> activeTicketsByDimension = new ConcurrentHashMap<>();

    public ForgeChunkTicketManager1710(Object modInstance) {
        this(new ReflectiveForgeTicketBridge1710(modInstance));
    }

    public ForgeChunkTicketManager1710(ForgeTicketBridge1710 bridge) {
        this.bridge = Objects.requireNonNull(bridge, "bridge must not be null");
    }

    /**
     * Standard Minecraft ChunkPos / ChunkCoordIntPair coordinate packing.
     */
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
            LOGGER.warn("Cannot acquire Forge 1.7.10 ticket: dimension '{}' is not loaded", dimension);
            return false;
        }

        long key = packChunkPos(chunkX, chunkZ);
        Map<Long, Object> dimTickets = activeTicketsByDimension.computeIfAbsent(dimension, k -> new ConcurrentHashMap<>());
        if (dimTickets.containsKey(key)) {
            return true; // Already held
        }

        Object ticket = bridge.requestTicket(dimension);
        if (ticket == null) {
            LOGGER.warn("Forge 1.7.10 ticket request failed in dimension '{}' for ({}, {})", dimension, chunkX, chunkZ);
            return false;
        }

        boolean forced = bridge.forceChunk(ticket, dimension, chunkX, chunkZ);
        if (!forced) {
            bridge.releaseTicket(ticket);
            return false;
        }

        dimTickets.put(key, ticket);
        return true;
    }

    @Override
    public boolean releaseTicket(String dimension, int chunkX, int chunkZ) {
        Map<Long, Object> dimTickets = activeTicketsByDimension.get(dimension);
        if (dimTickets == null) return false;

        long key = packChunkPos(chunkX, chunkZ);
        Object ticket = dimTickets.remove(key);
        if (ticket == null) return false;

        bridge.unforceChunk(ticket, dimension, chunkX, chunkZ);
        bridge.releaseTicket(ticket);
        return true;
    }

    @Override
    public int getActiveTicketCount() {
        return activeTicketsByDimension.values().stream().mapToInt(Map::size).sum();
    }

    @Override
    public Set<Long> getActiveTicketChunkKeys(String dimension) {
        Map<Long, Object> dimTickets = activeTicketsByDimension.get(dimension);
        return dimTickets == null ? Collections.emptySet() : Collections.unmodifiableSet(new HashSet<>(dimTickets.keySet()));
    }

    @Override
    public void releaseAllTickets() {
        for (Map.Entry<String, Map<Long, Object>> dimEntry : activeTicketsByDimension.entrySet()) {
            String dimension = dimEntry.getKey();
            Map<Long, Object> dimTickets = dimEntry.getValue();
            for (Map.Entry<Long, Object> entry : dimTickets.entrySet()) {
                long key = entry.getKey();
                Object ticket = entry.getValue();
                bridge.unforceChunk(ticket, dimension, unpackChunkX(key), unpackChunkZ(key));
                bridge.releaseTicket(ticket);
            }
            dimTickets.clear();
        }
        activeTicketsByDimension.clear();
        LOGGER.info("All Forge 1.7.10 chunk tickets released successfully.");
    }

    public ForgeTicketBridge1710 getBridge() {
        return bridge;
    }
}
