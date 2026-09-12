package com.dwurdy.heaphammer.platform.neoforge;

import com.dwurdy.heaphammer.domain.EnvironmentFingerprint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class NeoForgePlatformAdapterTest {

    private NeoForgePlatformAdapter adapter;
    private MockNeoForgeTicketBridge mockBridge;

    @BeforeEach
    void setUp() {
        mockBridge = new MockNeoForgeTicketBridge();
        NeoForgeChunkTicketManager ticketManager = new NeoForgeChunkTicketManager(mockBridge);
        adapter = new NeoForgePlatformAdapter(ticketManager);
    }

    @Test
    @DisplayName("Fingerprint captures NeoForge metadata correctly")
    void testCaptureFingerprint() {
        EnvironmentFingerprint fp = adapter.captureFingerprint();
        assertNotNull(fp);
        assertEquals("1.21.1", fp.minecraftVersion());
        assertTrue(fp.loaderVersion().contains("neoforge"));
        assertEquals("1.1.0", fp.heapHammerVersion());
        assertTrue(fp.installedMods().containsKey("neoforge"));
        assertTrue(fp.installedMods().containsKey("minecraft"));
        assertNotNull(fp.javaVersion());
    }

    @Test
    @DisplayName("Ticket lifecycle handles acquire, duplicate checks, release and releaseAll")
    void testTicketLifecycle() {
        NeoForgeChunkTicketManager tm = (NeoForgeChunkTicketManager) adapter.getChunkTicketManager();
        assertEquals(0, tm.getActiveTicketCount());

        assertTrue(tm.acquireTicket("minecraft:overworld", 10, 20));
        assertEquals(1, tm.getActiveTicketCount());

        // Duplicate acquire is idempotent
        assertTrue(tm.acquireTicket("minecraft:overworld", 10, 20));
        assertEquals(1, tm.getActiveTicketCount());

        assertTrue(tm.acquireTicket("minecraft:overworld", 11, 20));
        assertEquals(2, tm.getActiveTicketCount());

        Set<Long> keys = tm.getActiveTicketChunkKeys("minecraft:overworld");
        assertEquals(2, keys.size());

        assertTrue(tm.releaseTicket("minecraft:overworld", 10, 20));
        assertEquals(1, tm.getActiveTicketCount());

        tm.releaseAllTickets();
        assertEquals(0, tm.getActiveTicketCount());
        assertTrue(tm.getActiveTicketChunkKeys("minecraft:overworld").isEmpty());
    }

    @Test
    @DisplayName("Entity lifecycle tracks, spawns, and cleans up entities safely")
    void testEntityLifecycle() {
        List<String> types = adapter.getAvailableEntityTypes();
        assertFalse(types.isEmpty());
        assertTrue(types.contains("minecraft:zombie"));

        UUID uuid1 = adapter.spawnEntity("minecraft:overworld", "minecraft:zombie", 0, 64, 0);
        assertNotNull(uuid1);
        assertEquals(1, adapter.getActiveEntityCount("minecraft:overworld"));

        UUID uuid2 = adapter.spawnEntity("minecraft:overworld", "minecraft:skeleton", 1, 64, 1);
        assertNotNull(uuid2);
        assertEquals(2, adapter.getActiveEntityCount("minecraft:overworld"));

        assertTrue(adapter.removeEntity("minecraft:overworld", uuid1, "DISCARD"));
        assertEquals(1, adapter.getActiveEntityCount("minecraft:overworld"));

        int removed = adapter.removeAllTestEntities("minecraft:overworld");
        assertEquals(1, removed);
        assertEquals(0, adapter.getActiveEntityCount("minecraft:overworld"));
    }

    @Test
    @DisplayName("Block entity lifecycle places, tracks, and cleans up blocks")
    void testBlockEntityLifecycle() {
        List<String> types = adapter.getAvailableBlockEntityTypes();
        assertFalse(types.isEmpty());
        assertTrue(types.contains("minecraft:chest"));

        assertTrue(adapter.placeBlockEntity("minecraft:overworld", "minecraft:chest", 10, 64, 10));
        assertTrue(adapter.removeBlockEntity("minecraft:overworld", 10, 64, 10));

        adapter.placeBlockEntity("minecraft:overworld", "minecraft:chest", 10, 64, 10);
        adapter.placeBlockEntity("minecraft:overworld", "minecraft:furnace", 11, 64, 10);
        int removed = adapter.removeAllTestBlockEntities("minecraft:overworld");
        assertEquals(2, removed);
    }

    @Test
    @DisplayName("Server tick hook receives tick notifications")
    void testServerTickHook() {
        AtomicLong lastTick = new AtomicLong(0);
        adapter.registerServerTickHook(lastTick::set);

        adapter.onServerTick();
        assertEquals(1L, lastTick.get());

        adapter.onServerTick();
        assertEquals(2L, lastTick.get());
    }

    @Test
    @DisplayName("Cleanup orphaned state releases all resources")
    void testCleanupOrphanedState() {
        adapter.getChunkTicketManager().acquireTicket("minecraft:overworld", 5, 5);
        adapter.spawnEntity("minecraft:overworld", "minecraft:zombie", 0, 64, 0);
        adapter.placeBlockEntity("minecraft:overworld", "minecraft:chest", 10, 64, 10);

        assertEquals(1, adapter.getChunkTicketManager().getActiveTicketCount());
        assertEquals(1, adapter.getActiveEntityCount("minecraft:overworld"));

        int cleaned = adapter.cleanupOrphanedState();
        assertEquals(2, cleaned); // 1 entity + 1 block entity
        assertEquals(0, adapter.getChunkTicketManager().getActiveTicketCount());
        assertEquals(0, adapter.getActiveEntityCount("minecraft:overworld"));
    }

    private static class MockNeoForgeTicketBridge implements NeoForgeTicketBridge {
        @Override
        public boolean addRegionTicket(String dimension, int chunkX, int chunkZ) {
            return true;
        }

        @Override
        public boolean removeRegionTicket(String dimension, int chunkX, int chunkZ) {
            return true;
        }

        @Override
        public boolean isDimensionLoaded(String dimension) {
            return true;
        }
    }
}
