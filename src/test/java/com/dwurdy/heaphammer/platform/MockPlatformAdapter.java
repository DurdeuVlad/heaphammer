package com.dwurdy.heaphammer.platform;

import com.dwurdy.heaphammer.domain.EnvironmentFingerprint;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * In-memory MockPlatformAdapter for headless unit and scenario testing.
 */
public class MockPlatformAdapter implements PlatformAdapter {
    private final MockChunkTicketManager ticketManager = new MockChunkTicketManager();
    private final List<Consumer<Long>> tickListeners = new ArrayList<>();
    private long currentTick = 0;
    private int loadedChunks = 100;
    private int entities = 20;

    @Override
    public ChunkTicketManager getChunkTicketManager() {
        return ticketManager;
    }

    @Override
    public EnvironmentFingerprint captureFingerprint() {
        return new EnvironmentFingerprint(
                "1.0.0-test", "1.21.1", "0.19.5", "21.0.8", 12345L, "test-hash",
                Map.of("heaphammer", "1.0.0-test")
        );
    }

    @Override
    public int getLoadedChunkCount(String dimension) {
        return loadedChunks + ticketManager.getActiveTicketCount();
    }

    @Override
    public int getTotalLoadedChunkCount() {
        return loadedChunks + ticketManager.getActiveTicketCount();
    }

    @Override
    public int getActiveEntityCount(String dimension) {
        return entities;
    }

    @Override
    public boolean isDimensionAvailable(String dimension) {
        return true;
    }

    @Override
    public void registerServerTickHook(Consumer<Long> tickConsumer) {
        tickListeners.add(tickConsumer);
    }

    @Override
    public boolean isServerReady() {
        return true;
    }

    public void advanceTick() {
        currentTick++;
        for (Consumer<Long> listener : tickListeners) {
            listener.accept(currentTick);
        }
    }

    public static class MockChunkTicketManager implements ChunkTicketManager {
        private final Map<String, Set<Long>> tickets = new ConcurrentHashMap<>();

        @Override
        public boolean acquireTicket(String dimension, int chunkX, int chunkZ) {
            long key = (((long) chunkX) & 0xFFFFFFFFL) | ((((long) chunkZ) & 0xFFFFFFFFL) << 32);
            return tickets.computeIfAbsent(dimension, k -> new HashSet<>()).add(key);
        }

        @Override
        public boolean releaseTicket(String dimension, int chunkX, int chunkZ) {
            long key = (((long) chunkX) & 0xFFFFFFFFL) | ((((long) chunkZ) & 0xFFFFFFFFL) << 32);
            Set<Long> set = tickets.get(dimension);
            return set != null && set.remove(key);
        }

        @Override
        public int getActiveTicketCount() {
            return tickets.values().stream().mapToInt(Set::size).sum();
        }

        @Override
        public Set<Long> getActiveTicketChunkKeys(String dimension) {
            Set<Long> set = tickets.get(dimension);
            return set == null ? Collections.emptySet() : Collections.unmodifiableSet(new HashSet<>(set));
        }

        @Override
        public void releaseAllTickets() {
            tickets.clear();
        }
    }
}
