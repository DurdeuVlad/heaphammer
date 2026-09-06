package com.dwurdy.heaphammer.platform.forge;

import com.dwurdy.heaphammer.domain.EnvironmentFingerprint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class ForgePlatformAdapterTest {

    private ForgePlatformAdapter adapter;
    private MockForgeTicketBridge mockBridge;

    @BeforeEach
    void setUp() {
        mockBridge = new MockForgeTicketBridge();
        ForgeChunkTicketManager ticketManager = new ForgeChunkTicketManager(mockBridge);
        adapter = new ForgePlatformAdapter(ticketManager);
    }

    @Test
    @DisplayName("Fingerprint captures Forge 1.12.2 metadata correctly")
    void testCaptureFingerprint() {
        EnvironmentFingerprint fp = adapter.captureFingerprint();
        assertNotNull(fp);
        assertEquals("1.12.2", fp.minecraftVersion());
        assertTrue(fp.loaderVersion().contains("forge"));
        assertNotNull(fp.heapHammerVersion());
        assertTrue(fp.installedMods().containsKey("forge"));
        assertTrue(fp.installedMods().containsKey("minecraft"));
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

    private static class MockForgeTicketBridge implements ForgeTicketBridge {
        @Override
        public Object requestTicket(String dimension) {
            return new Object();
        }

        @Override
        public boolean forceChunk(Object ticket, String dimension, int chunkX, int chunkZ) {
            return true;
        }

        @Override
        public boolean unforceChunk(Object ticket, String dimension, int chunkX, int chunkZ) {
            return true;
        }

        @Override
        public void releaseTicket(Object ticket) {}

        @Override
        public boolean isDimensionLoaded(String dimension) {
            return true;
        }
    }
}
